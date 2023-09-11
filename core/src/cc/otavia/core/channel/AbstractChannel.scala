/*
 * Copyright 2022 Yan Kun <yan_kun_1992@foxmail.com>
 *
 * This file fork from netty.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cc.otavia.core.channel

import cc.otavia.buffer.AbstractBuffer
import cc.otavia.buffer.pool.{AbstractPooledPageAllocator, AdaptiveBuffer}
import cc.otavia.core.actor.ChannelsActor
import cc.otavia.core.channel.inflight.{FutureQueue, StackQueue}
import cc.otavia.core.channel.message.{AdaptiveBufferChangeNotice, DatagramAdaptiveRangePacket, ReadPlan, ReadPlanFactory}
import cc.otavia.core.message.ReactorEvent
import cc.otavia.core.slf4a.Logger
import cc.otavia.core.stack.{AbstractPromise, ChannelPromise, ChannelReplyFuture, ChannelStack}
import cc.otavia.core.system.{ActorSystem, ActorThread}

import java.net.SocketAddress
import java.nio.file.attribute.FileAttribute
import java.nio.file.{OpenOption, Path}
import scala.language.unsafeNulls

/** Abstract class of file channel and network channel. */
abstract class AbstractChannel(val system: ActorSystem) extends Channel, ChannelState {

    protected val logger: Logger = Logger.getLogger(getClass, system)

    private var channelId: Int = -1

    private var actor: ChannelsActor[?] | Null = _

    private var pipe: ChannelPipelineImpl = _

    private var unsafe: AbstractUnsafeChannel = _

    private var inboundMsgBarrier: AnyRef => Boolean  = _ => false
    private var outboundMsgBarrier: AnyRef => Boolean = _ => true

    private var maxOutInflight: Int = 1

    // outbound futures which is write to channel and wait channel reply
    private val outboundInflightFutures: FutureQueue = new FutureQueue()

    // outbound futures which is waiting channel to send
    private val outboundPendingFutures: FutureQueue = new FutureQueue()

    // inbound stack which is running by actor
    private val inboundInflightStacks: StackQueue = new StackQueue()

    // inbound stack to wait actor running
    private val inboundPendingStacks: StackQueue = new StackQueue()

    private var channelMsgId: Long = ChannelInflight.INVALID_CHANNEL_MESSAGE_ID

    protected var ongoingChannelPromise: ChannelPromise = _

    private var direct: AbstractPooledPageAllocator = _
    private var heap: AbstractPooledPageAllocator   = _

    // initial channel state on constructing
    created = true
    registering = false
    registered = false

    connecting = false
    connected = false

    /** true if the channel has never been registered, false otherwise */
    neverRegistered = true

    neverActive = true

    inputClosedSeenErrorOnRead = false

    autoRead = true
    autoClose = true
    writable = true
    allowHalfClosure = false
    inWriteFlushed = false

    closeInitiated = false

    // impl ChannelInflight

    final override def inboundMessageBarrier: AnyRef => Boolean = inboundMsgBarrier

    final override def setInboundMessageBarrier(barrier: AnyRef => Boolean): Unit = inboundMsgBarrier = barrier

    final override def outboundMessageBarrier: AnyRef => Boolean = outboundMsgBarrier

    final override def setOutboundMessageBarrier(barrier: AnyRef => Boolean): Unit = outboundMsgBarrier = barrier

    override def setMaxOutboundInflight(max: Int): Unit = maxOutInflight = max

    override def maxOutboundInflight: Int = maxOutInflight

    private def attachStack(promise: AbstractPromise[?]): Unit = {
        assert(promise.notInChain, "The Promise has been used, can't be use again!")
        promise.setStack(this.executor.currentStack)
        this.executor.currentStack.addUncompletedPromise(promise)
    }

    final override def outboundInflightSize: Int = outboundInflightFutures.size

    final override def outboundPendingSize: Int = outboundPendingFutures.size

    final override def inboundInflightSize: Int = inboundInflightStacks.size

    final override def inboundPendingSize: Int = inboundPendingStacks.size

    override def generateMessageId: Long = {
        channelMsgId += 1
        channelMsgId
    }

    override private[core] def onInboundMessage(msg: AnyRef): Unit = {
        if (outboundInflightFutures.isEmpty) {
            val stack = ChannelStack(this, msg, msgId = generateMessageId) // TODO: support channel notice message
            inboundInflightStacks
            executor.receiveChannelMessage(stack)
        } else {
            if (maxOutInflight == 1 && outboundInflightFutures.size == 1) {}
        }
    }

    override private[core] def onInboundMessage(msg: AnyRef, id: Long): Unit = {
        if (outboundInflightFutures.hasMessage(id)) {
            val promise = outboundInflightFutures.pop()
            if (!msg.isInstanceOf[Throwable]) promise.setSuccess(msg)
            else promise.setFailure(msg.asInstanceOf[Throwable])
        } else {
            val stack = ChannelStack(this, msg, id)
            //        this.executor.continueChannelStack()

            this.executor.receiveChannelMessage(stack)
        }
        if (outboundPendingFutures.nonEmpty) processPendingFutures()
    }

    private def processPendingFutures(): Unit = {
        if (outboundPendingFutures.headIsBarrier) {
            if (outboundInflightFutures.isEmpty) {
                val promise = outboundPendingFutures.pop()
                // write message to pipeline, and send data by socket
                this.writeAndFlush(promise.getAsk(), promise.messageId)
                outboundInflightFutures.append(promise)
            } // else do nothing and wait outboundInflightFutures become empty.
        } else {
            while (
              outboundPendingFutures.nonEmpty && outboundInflightFutures.size < maxOutboundInflight &&
              !outboundInflightFutures.headIsBarrier
            ) {
                val promise = outboundPendingFutures.pop()
                this.write(promise.getAsk(), promise.messageId)
                outboundInflightFutures.append(promise)
            }
            this.flush()
        }
    }

    // end impl ChannelInflight

    // impl ChannelAddress
    override def ask(value: AnyRef, future: ChannelReplyFuture): ChannelReplyFuture = {
        val promise = future.promise
        promise.setMessageId(generateMessageId)
        promise.setBarrier(outboundMessageBarrier(value))
        promise.setAsk(value)
        executor.attachStack(executor.idAllocator.generate, future)
        outboundPendingFutures.append(promise)
        processPendingFutures()
        future
    }

    override def notice(value: AnyRef): Unit = {
        this.write(value) // write message to pipeline
        this.flush()      // flush data to io transport
    }

    override def batchNotice(notices: Seq[AnyRef]): Unit = {
        for (notice <- notices) this.write(notice) // write message to pipeline
        this.flush() // flush data to io transport
    }

    // end impl ChannelAddress

    // impl Channel

    override def id: Int = channelId

    override def executor: ChannelsActor[?] = actor match
        case a: ChannelsActor[?] => a
        case null =>
            throw new IllegalStateException(s"The channel $this is not mounted, use mount to mount channel.")

    final private[core] def mount(channelsActor: ChannelsActor[?]): Unit = {
        assert(!mounted, s"The channel $this has been mounted already, you can't mount it twice!")
        actor = channelsActor
        direct = ActorThread.currentThread().directAllocator
        heap = ActorThread.currentThread().heapAllocator
        channelId = executor.generateChannelId()
        pipe = newChannelPipeline()
        mounted = true
    }

    override def directAllocator: AbstractPooledPageAllocator = direct

    override def heapAllocator: AbstractPooledPageAllocator = heap

    def unsafeChannel: AbstractUnsafeChannel = unsafe

    private[core] def setUnsafeChannel(uch: AbstractUnsafeChannel): Unit = unsafe = uch

    override def pipeline: ChannelPipeline = pipe

    /** Returns a new [[ChannelPipeline]] instance. */
    private def newChannelPipeline(): ChannelPipelineImpl = new ChannelPipelineImpl(this)

    protected def currentThread: ActorThread = Thread.currentThread().asInstanceOf[ActorThread]

    private def laterTasks = currentThread.laterTasks

    // This method is used by outbound operation implementations to trigger an inbound event later.
    // They do not trigger an inbound event immediately because an outbound operation might have been
    // triggered by another inbound event handler method.  If fired immediately, the call stack
    // will look like this for example:
    //
    //   handlerA.inboundBufferUpdated() - (1) an inbound handler method closes a connection.
    //   -> handlerA.ctx.close()
    //      -> channel.closeTransport()
    //         -> handlerA.channelInactive() - (2) another inbound handler method called while in (1) yet
    //
    // which means the execution of two inbound handler methods of the same handler overlap undesirably.
    protected def invokeLater(task: Runnable): Unit = laterTasks.append(task)

    override def getOption[T](option: ChannelOption[T]): T = ???

    override def setOption[T](option: ChannelOption[T], value: T): Channel = ???

    override def isOptionSupported(option: ChannelOption[?]): Boolean = ???

    override def isOpen: Boolean = ???

    override def isActive: Boolean = ???

    override def isRegistered: Boolean = ???

    override def isShutdown(direction: ChannelShutdownDirection): Boolean = ???

    override def localAddress: Option[SocketAddress] = ???

    override def remoteAddress: Option[SocketAddress] = ???

    override def writableBytes: Long = ???

    override private[core] def closeAfterCreate(): Unit = ???

    // end impl Channel

    // impl EventHandle

    override private[core] def handleChannelCloseEvent(event: ReactorEvent.ChannelClose): Unit = ???

    override private[core] def handleChannelDeregisterReplyEvent(event: ReactorEvent.DeregisterReply): Unit = ???

    override private[core] def handleChannelReadinessEvent(event: ReactorEvent.ChannelReadiness): Unit = ???

    override private[core] def handleChannelTimeoutEvent(eventRegisterId: Long): Unit = ???

    override private[core] def handleChannelAcceptedEvent(event: ReactorEvent.AcceptedEvent): Unit =
        event.channel.pipeline.fireChannelRead(event.accepted)

    override private[core] def handleChannelReadCompletedEvent(event: ReactorEvent.ReadCompletedEvent): Unit =
        pipe.fireChannelReadComplete()

    override private[core] def handleChannelBindReplyEvent(event: ReactorEvent.BindReply): Unit = ???

    override private[core] def handleChannelConnectReplyEvent(event: ReactorEvent.ConnectReply): Unit = ???

    override private[core] def handleChannelDisconnectReplyEvent(event: ReactorEvent.DisconnectReply): Unit = {}

    override private[core] def handleChannelOpenReplyEvent(event: ReactorEvent.OpenReply): Unit = {}

    override private[core] def handleChannelReadBufferEvent(event: ReactorEvent.ReadBuffer): Unit = {
        event.sender match
            case Some(address) => // fire UDP message
                val start = channelInboundAdaptiveBuffer.writerOffset
                channelInboundAdaptiveBuffer.extend(event.buffer)
                val length = channelInboundAdaptiveBuffer.writerOffset - start
                pipeline.fireChannelRead(DatagramAdaptiveRangePacket(start, length, event.recipient, event.sender))
            case None => // fire other message
                if (channelInboundAdaptiveBuffer.readableBytes == 0) {
                    channelInboundAdaptiveBuffer.extend(event.buffer)
                } else if (channelInboundAdaptiveBuffer.allocatedWritableBytes >= event.buffer.readableBytes) {
                    channelInboundAdaptiveBuffer.writeBytes(event.buffer)
                } else {
                    val last = channelInboundAdaptiveBuffer.splitLast()
                    if (last.capacity - last.readableBytes >= event.buffer.readableBytes) {
                        last.compact()
                        last.writeBytes(event.buffer)
                        event.buffer.close()
                        channelInboundAdaptiveBuffer.extend(last)
                    } else {
                        channelInboundAdaptiveBuffer.extend(last)
                        channelInboundAdaptiveBuffer.extend(event.buffer)
                    }
                }
                pipeline.fireChannelRead(channelInboundAdaptiveBuffer)
    }

    // end impl EventHandle

    final protected def newPromise(): ChannelPromise = ChannelPromise()

}
