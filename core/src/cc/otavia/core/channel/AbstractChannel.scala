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
import cc.otavia.core.channel.inflight.{FutureQueue, QueueMap, StackQueue}
import cc.otavia.core.channel.message.{AdaptiveBufferChangeNotice, DatagramAdaptiveRangePacket, ReadPlan, ReadPlanFactory}
import cc.otavia.core.message.ReactorEvent
import cc.otavia.core.slf4a.Logger
import cc.otavia.core.stack.*
import cc.otavia.core.system.{ActorSystem, ActorThread}

import java.net.SocketAddress
import java.nio.file.attribute.FileAttribute
import java.nio.file.{OpenOption, Path}
import scala.language.unsafeNulls

/** Abstract class of file channel and network channel. */
abstract class AbstractChannel(val system: ActorSystem) extends Channel, ChannelState {

    protected val logger: Logger        = Logger.getLogger(getClass, system)
    private var actor: ChannelsActor[?] = _

    private var channelId: Int                = -1
    private var pipe: ChannelPipelineImpl     = _
    private var unsafe: AbstractUnsafeChannel = _

    private var futureBarrier: AnyRef => Boolean = _ => false
    private var stackBarrier: AnyRef => Boolean  = _ => true
    private var maxStackInflight: Int            = 1
    private var maxFutureInflight: Int           = 1

    // outbound futures which is write to channel and wait channel reply
    private val inflightFutures: QueueMap[ChannelReplyPromise] = new QueueMap[ChannelReplyPromise]()
    // outbound futures which is waiting channel to send
    private val pendingFutures: QueueMap[ChannelReplyPromise] = new QueueMap[ChannelReplyPromise]()
    // inbound stack which is running by actor
    private val inflightStacks: QueueMap[ChannelStack[?]] = new QueueMap[ChannelStack[?]]()
    // inbound stack to wait actor running
    private val pendingStacks: QueueMap[ChannelStack[?]] = new QueueMap[ChannelStack[?]]()

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

    final override def futureMessageBarrier: AnyRef => Boolean                   = futureBarrier
    final override def setFutureMessageBarrier(barrier: AnyRef => Boolean): Unit = futureBarrier = barrier
    final override def stackMessageBarrier: AnyRef => Boolean                    = stackBarrier
    final override def setStackMessageBarrier(barrier: AnyRef => Boolean): Unit  = stackBarrier = barrier
    override def setMaxFutureInflight(max: Int): Unit                            = maxFutureInflight = max
    override def setMaxStackInflight(max: Int): Unit                             = maxStackInflight = max
    final override def inflightFutureSize: Int                                   = inflightFutures.size
    final override def pendingFutureSize: Int                                    = pendingFutures.size
    final override def inflightStackSize: Int                                    = inflightStacks.size
    final override def pendingStackSize: Int                                     = pendingStacks.size
    override def setStackHeadOfLine(hol: Boolean): Unit                          = stackHeadOfLine = hol

    override def generateMessageId: Long = { channelMsgId += 1; channelMsgId }

    override private[core] def onInboundMessage(msg: AnyRef): Unit = {
        if (maxFutureInflight == 1 && inflightFutures.size == 1) {
            val promise = inflightFutures.pop()
            if (!msg.isInstanceOf[Throwable]) promise.setSuccess(msg)
            else promise.setFailure(msg.asInstanceOf[Throwable])
        } else {
            val stack = ChannelStack(this, msg, msgId = generateMessageId)
            pendingStacks.append(stack)
            processPendingStacks()
        }
        if (pendingFutures.nonEmpty) processPendingFutures()
    }

    override private[core] def onInboundMessage(msg: AnyRef, id: Long): Unit = {
        if (inflightFutures.contains(id)) {
            val promise = inflightFutures.remove(id)
            if (!msg.isInstanceOf[Throwable]) promise.setSuccess(msg)
            else promise.setFailure(msg.asInstanceOf[Throwable])
        } else {
            val stack = ChannelStack(this, msg, id)
            pendingStacks.append(stack)
            processPendingStacks()
        }
        if (pendingFutures.nonEmpty) processPendingFutures()
    }

    final private[core] def processCompletedChannelStacks(): Unit =
        while (inflightStacks.nonEmpty && inflightStacks.first.isDone) {
            val stack = inflightStacks.pop()
            if (stack.hasResult) this.writeAndFlush(stack.result, stack.messageId)
            stack.recycle()
        }

    final private[core] def processPendingStacks(): Unit = if (pendingStacks.headIsBarrier) {
        if (inflightStacks.isEmpty) processPendingStack()
    } else if (!inflightStacks.headIsBarrier) {
        while (
          pendingStacks.nonEmpty &&
          inflightStacks.size < maxStackInflight &&
          !pendingStacks.headIsBarrier
        ) processPendingStack()
        processCompletedChannelStacks()
    }

    private def processPendingStack(): Unit = {
        val stack = pendingStacks.pop()
        inflightStacks.append(stack)
        executor.receiveChannelMessage(stack)
        if (stack.isDone && !stackHeadOfLine) {
            inflightStacks.remove(stack.entityId)
            if (stack.hasResult) this.writeAndFlush(stack.result, stack.messageId)
            stack.recycle()
        }
    }

    final private[core] def processPendingFutures(): Unit = if (pendingFutures.headIsBarrier) {
        if (inflightFutures.isEmpty) {
            val promise = pendingFutures.pop()
            inflightFutures.append(promise)
            // write message to pipeline, and send data by socket
            this.writeAndFlush(promise.getAsk(), promise.messageId)
        }
    } else if (!inflightFutures.headIsBarrier) {
        while (
          pendingFutures.nonEmpty &&
          inflightFutures.size < maxFutureInflight &&
          !pendingFutures.headIsBarrier
        ) {
            val promise = pendingFutures.pop()
            this.write(promise.getAsk(), promise.messageId)
            inflightFutures.append(promise)
        }
        this.flush()
    }

    final protected def failedFutures(cause: Throwable): Unit = {
        while (inflightFutures.nonEmpty) {
            val promise = inflightFutures.pop()
            promise.setFailure(cause)
        }
        while (pendingFutures.nonEmpty) {
            val promise = pendingFutures.pop()
            promise.setFailure(cause)
        }
    }

    // end impl ChannelInflight

    // impl ChannelAddress
    override def ask(value: AnyRef, future: ChannelReplyFuture): ChannelReplyFuture = {
        val promise = future.promise
        promise.setMessageId(generateMessageId)
        promise.setBarrier(stackMessageBarrier(value))
        promise.setAsk(value)
        executor.attachStack(executor.generateSendMessageId(), future)
        pendingFutures.append(promise)
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

    override def executor: ChannelsActor[?] =
        if (actor != null) actor
        else
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

    protected def closeAdaptiveBuffers(): Unit = {
        closeInboundAdaptiveBuffers()
        closeOutboundAdaptiveBuffers()
    }

    protected def closeInboundAdaptiveBuffers(): Unit = {
        shutdownedInbound = true
        pipeline.closeInboundAdaptiveBuffers()
    }

    protected def closeOutboundAdaptiveBuffers(): Unit = {
        shutdownedOutbound = true
        pipeline.closeOutboundAdaptiveBuffers()
    }

    final protected def newPromise(): ChannelPromise = ChannelPromise()

}
