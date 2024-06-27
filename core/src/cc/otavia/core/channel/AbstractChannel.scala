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

import cc.otavia.buffer.pool.{AbstractPooledPageAllocator, AdaptiveBuffer}
import cc.otavia.core.actor.ChannelsActor
import cc.otavia.core.channel.ChannelOption.*
import cc.otavia.core.channel.inflight.QueueMap
import cc.otavia.core.channel.internal.AdaptiveBufferOffset
import cc.otavia.core.channel.message.{AdaptiveBufferChangeNotice, DatagramAdaptiveRangePacket, ReadPlan, ReadPlanFactory}
import cc.otavia.core.message.ReactorEvent
import cc.otavia.core.slf4a.Logger
import cc.otavia.core.stack.*
import cc.otavia.core.stack.helper.ChannelFutureState
import cc.otavia.core.system.{ActorSystem, ActorThread}

import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.file.attribute.FileAttribute
import java.nio.file.{OpenOption, Path}
import scala.collection.mutable
import scala.language.unsafeNulls

/** Abstract class of file channel and network channel. */
abstract class AbstractChannel(val system: ActorSystem) extends Channel, ChannelState {

    import AbstractChannel.*

    protected val logger: Logger        = Logger.getLogger(getClass, system)
    private var actor: ChannelsActor[?] = _

    private var channelId: Int                = -1
    private var pipe: ChannelPipelineImpl     = _
    private var unsafe: AbstractUnsafeChannel = _

    private var futureBarrier: AnyRef => Boolean = FALSE_FUNC
    private var stackBarrier: AnyRef => Boolean  = TURE_FUNC
    private var maxStackInflight: Int            = 1
    private var maxFutureInflight: Int           = 1

    // outbound futures which is write to channel and wait channel reply
    private[core] val inflightFutures: QueueMap[ChannelPromise] = new QueueMap[ChannelPromise]()
    // outbound futures which is waiting channel to send
    private val pendingFutures: QueueMap[ChannelPromise] = new QueueMap[ChannelPromise]()
    // inbound stack which is running by actor
    private[core] val inflightStacks: QueueMap[ChannelStack[?]] = new QueueMap[ChannelStack[?]]()
    // inbound stack to wait actor running
    private val pendingStacks: QueueMap[ChannelStack[?]] = new QueueMap[ChannelStack[?]]()

    private var channelMsgId: Long = ChannelInflight.INVALID_CHANNEL_MESSAGE_ID

    protected var ongoingChannelPromise: ChannelPromise = _

    protected var outboundQueue: mutable.ArrayDeque[AdaptiveBufferOffset | FileRegion] = mutable.ArrayDeque.empty

    private var direct: AbstractPooledPageAllocator = _
    private var heap: AbstractPooledPageAllocator   = _
    private var threadId: Int                       = -1

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

    closed = false
    closing = false

    override final def isMounted: Boolean = mounted

    // impl ChannelInflight

    /** Set inbound message barrier function
     *
     *  @param barrier
     *    a function to check a [[AnyRef]] object is barrier.
     */
    final private def setFutureMessageBarrier(barrier: AnyRef => Boolean): Unit = futureBarrier = barrier

    /** Set inbound message barrier
     *
     *  @param barrier
     *    a function to check a [[AnyRef]] object is barrier.
     */
    private final def setStackMessageBarrier(barrier: AnyRef => Boolean): Unit = stackBarrier = barrier
    private final def setMaxFutureInflight(max: Int): Unit                     = maxFutureInflight = max
    private final def setMaxStackInflight(max: Int): Unit                      = maxStackInflight = max
    final override def inflightFutureSize: Int                                 = inflightFutures.size
    final override def pendingFutureSize: Int                                  = pendingFutures.size
    final override def inflightStackSize: Int                                  = inflightStacks.size
    final override def pendingStackSize: Int                                   = pendingStacks.size
    private final def setStackHeadOfLine(hol: Boolean): Unit                   = stackHeadOfLine = hol

    override def generateMessageId: Long = { channelMsgId += 1; channelMsgId }

    override private[core] def onInboundMessage(msg: AnyRef, exception: Boolean): Unit = {
        if (maxFutureInflight == 1 && inflightFutures.size == 1) {
            val promise = inflightFutures.pop()
            if (!exception) promise.setSuccess(msg) else promise.setFailure(msg.asInstanceOf[Throwable])
        } else {
            val stack = ChannelStack(this, msg, msgId = generateMessageId)
            pendingStacks.append(stack)
            processPendingStacks()
        }
        if (pendingFutures.nonEmpty) processPendingFutures()
    }

    override private[core] def onInboundMessage(msg: AnyRef, exception: Boolean, id: Long): Unit = {
        if (inflightFutures.contains(id)) {
            val promise = inflightFutures.remove(id)
            if (!exception) promise.setSuccess(msg) else promise.setFailure(msg.asInstanceOf[Throwable])
        } else {
            val stack = ChannelStack(this, msg, id)
            pendingStacks.append(stack)
            processPendingStacks()
        }
        if (pendingFutures.nonEmpty) processPendingFutures()
    }

    final private[core] def processCompletedChannelStacks(): Unit = {
        while (inflightStacks.nonEmpty && inflightStacks.first.isDone) {
            val stack = inflightStacks.first
            if (stack.hasResult) {
                this.write(stack.result, stack.messageId)
                if (channelOutboundAdaptiveBuffer.readableBytes > ActorSystem.PAGE_SIZE * 4) this.flush()
            }
            actor.recycleStack(inflightStacks.pop())
        }
        this.flush()

        if (pendingStacks.nonEmpty) processPendingStacks()
    }

    final private[core] def processPendingStacks(): Unit =
        if (pendingStacks.nonEmpty && stackBarrier(pendingStacks.first.message)) {
            if (inflightStacks.isEmpty) processPendingStack()
        } else if (pendingStacks.nonEmpty && !stackBarrier(pendingStacks.first.message)) {
            while (
              pendingStacks.nonEmpty &&
              inflightStacks.size < maxStackInflight &&
              !stackBarrier(pendingStacks.first.message)
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
            actor.recycleStack(stack)
        }
    }

    final private[core] def processPendingFutures(): Unit =
        if (pendingFutures.nonEmpty && futureBarrier(pendingFutures.first.getAsk())) {
            if (inflightFutures.isEmpty) {
                val promise = pendingFutures.pop()
                inflightFutures.append(promise)
                // write message to pipeline, and send data by socket
                this.writeAndFlush(promise.getAsk(), promise.messageId)
            }
        } else {
            while (
              pendingFutures.nonEmpty &&
              inflightFutures.size < maxFutureInflight &&
              !futureBarrier(pendingFutures.first.getAsk()) &&
              !inflightFutures.isBarrierMode
            ) {
                val promise = pendingFutures.pop()
                inflightFutures.append(promise)
                this.write(promise.getAsk(), promise.messageId)
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

    final protected def failedStacks(cause: Throwable): Unit = {
        while (inflightStacks.nonEmpty) {
            val stack = inflightStacks.pop()
            actor.recycleStack(stack)
        }
        while (pendingStacks.nonEmpty) {
            val stack = pendingStacks.pop()
            actor.recycleStack(stack)
        }
    }

    // end impl ChannelInflight

    // impl ChannelAddress
    override def ask(value: AnyRef, future: ChannelFuture): ChannelFuture = {
        val promise = future.promise
        promise.setMessageId(generateMessageId)
        promise.setAsk(value)
        promise.setChannel(this)
        actor.attachStack(actor.generateSendMessageId(), future)
        pendingFutures.append(promise)
        processPendingFutures()
        future
    }

    override def ask(value: AnyRef): ChannelFutureState = {
        val channelState = ChannelFutureState()
        val promise      = channelState.future.promise
        promise.setMessageId(generateMessageId)
        promise.setAsk(value)
        promise.setChannel(this)
        actor.attachStack(actor.generateSendMessageId(), channelState.future)
        pendingFutures.append(promise)
        processPendingFutures()
        channelState
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

    override final def executor: ChannelsActor[?] = actor

    final private[core] def mount(channelsActor: ChannelsActor[?]): Unit = {
        assert(!mounted, s"The channel $this has been mounted already, you can't mount it twice!")
        actor = channelsActor
        val thread = ActorThread.currentThread()
        threadId = thread.index
        direct = thread.directAllocator
        heap = thread.heapAllocator
        channelId = executor.generateChannelId()
        pipe = newChannelPipeline()
        mounted = true
    }

    final override def mountThreadId: Int = threadId

    override def directAllocator: AbstractPooledPageAllocator = direct

    override def heapAllocator: AbstractPooledPageAllocator = heap

    def unsafeChannel: AbstractUnsafeChannel = unsafe

    private[core] def setUnsafeChannel(uch: AbstractUnsafeChannel): Unit = unsafe = uch

    override def pipeline: ChannelPipeline = pipe

    /** Returns a new [[ChannelPipeline]] instance. */
    private def newChannelPipeline(): ChannelPipelineImpl = new ChannelPipelineImpl(this)

    private def laterTasks = ActorThread.currentThread().laterTasks

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

    override final def getOption[T](option: ChannelOption[T]): T = option match
        case CHANNEL_FUTURE_BARRIER      => futureBarrier
        case CHANNEL_STACK_BARRIER       => stackBarrier
        case CHANNEL_MAX_FUTURE_INFLIGHT => maxFutureInflight
        case CHANNEL_MAX_STACK_INFLIGHT  => maxStackInflight
        case CHANNEL_STACK_HEAD_OF_LINE  => stackHeadOfLine
        case _                           => getExtendedOption(option)

    /** Override to add support for more [[ChannelOption]]s. You need to also call super after handling the extra
     *  options.
     *
     *  @param option
     *    the [[ChannelOption]].
     *  @tparam T
     *    the value type.
     *  @return
     *    the value for the option.
     *  @throws UnsupportedOperationException
     *    if the [[ChannelOption]] is not supported.
     */
    protected def getExtendedOption[T](option: ChannelOption[T]): T =
        throw new UnsupportedOperationException(s"ChannelOption not supported: $option")

    override final def setOption[T](option: ChannelOption[T], value: T): Channel = {
        option match
            case CHANNEL_FUTURE_BARRIER      => setFutureMessageBarrier(value)
            case CHANNEL_STACK_BARRIER       => setStackMessageBarrier(value)
            case CHANNEL_MAX_FUTURE_INFLIGHT => setMaxFutureInflight(value)
            case CHANNEL_MAX_STACK_INFLIGHT  => setMaxStackInflight(value)
            case CHANNEL_STACK_HEAD_OF_LINE  => setStackHeadOfLine(value)
            case _                           => setExtendedOption(option, value)
        this
    }

    /** Override to add support for more [[ChannelOption]]s. You need to also call super after handling the extra
     *  options.
     *
     *  @param option
     *    the [[ChannelOption]].
     *  @tparam T
     *    the value type.
     *  @throws UnsupportedOperationException
     *    if the [[ChannelOption]] is not supported.
     */
    protected def setExtendedOption[T](option: ChannelOption[T], value: T): Unit =
        throw new UnsupportedOperationException(s"ChannelOption not supported: $option")

    override final def isOptionSupported(option: ChannelOption[?]): Boolean =
        SUPPORTED_CHANNEL_OPTIONS.contains(option) || isExtendedOptionSupported(option)

    /** Override to add support for more [[ChannelOption]]s. You need to also call super after handling the extra
     *  options.
     *
     *  @param option
     *    the [[ChannelOption]].
     *  @return
     *    true if supported, false otherwise.
     */
    protected def isExtendedOptionSupported(option: ChannelOption[?]) = false

    override final def isOpen: Boolean = unsafe.isOpen

    override final def isActive: Boolean = unsafe.isActive

    override final def isRegistered: Boolean = registered

    override def isShutdown(direction: ChannelShutdownDirection): Boolean = unsafe.isShutdown(direction)

    override def writableBytes: Long = ???

    override private[core] def closeAfterCreate(): Unit = closeTransport(newPromise())

    // end impl Channel

    // impl EventHandle

    final private[core] def handleChannelAcceptedEvent(event: ReactorEvent.AcceptedEvent): Unit =
        event.channel.pipeline.fireChannelRead(event.accepted)

    final private[core] def handleChannelReadCompletedEvent(event: ReactorEvent.ReadCompletedEvent): Unit =
        pipe.fireChannelReadComplete()

    final private[core] def handleChannelReadBufferEvent(event: ReactorEvent.ReadBuffer): Unit = {
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

    // unsafe transport

    final private[core] def writeTransport(msg: AnyRef): Unit = {
        msg match
            case adaptiveBuffer: AdaptiveBuffer if adaptiveBuffer == channelOutboundAdaptiveBuffer =>
                updateLastOutboundQueue()
            case bytes: Array[Byte] =>
                channelOutboundAdaptiveBuffer.writeBytes(bytes)
                updateLastOutboundQueue()
            case buffer: ByteBuffer =>
                channelOutboundAdaptiveBuffer.writeBytes(buffer)
                updateLastOutboundQueue()
            case fileRegion: FileRegion => outboundQueue.addOne(fileRegion)
            case _                      => throw new UnsupportedOperationException()
    }

    private def updateLastOutboundQueue(): Unit =
        if (outboundQueue.nonEmpty && outboundQueue.last.isInstanceOf[AdaptiveBufferOffset]) {
            val last = outboundQueue.last.asInstanceOf[AdaptiveBufferOffset]
            last.endIndex = channelOutboundAdaptiveBuffer.writerOffset
        } else {
            val offset = new AdaptiveBufferOffset(channelOutboundAdaptiveBuffer.writerOffset)
            outboundQueue.addOne(offset)
        }

    final private[core] def flushTransport(): Unit = {
        val resize = if (outboundQueue.size > 64) true else false
        while (outboundQueue.nonEmpty) {
            val payload = outboundQueue.removeHead()
            payload match
                case fileRegion: FileRegion => reactor.flush(this, fileRegion)
                case adaptiveBufferOffset: AdaptiveBufferOffset =>
                    val chain = channelOutboundAdaptiveBuffer.splitBefore(adaptiveBufferOffset.endIndex)
                    reactor.flush(this, chain)
        }
        if (resize) outboundQueue.clearAndShrink()
    }

    private[core] def bindTransport(local: SocketAddress, channelPromise: ChannelPromise): Unit

    // format: off
    private[core] def connectTransport(remote: SocketAddress, local: Option[SocketAddress], promise: ChannelPromise): Unit
    // format: on

    // format: off
    private[core] def openTransport(path: Path, options: Seq[OpenOption], attrs: Seq[FileAttribute[?]], promise: ChannelPromise): Unit
    // format: on

    private[core] def disconnectTransport(promise: ChannelPromise): Unit

    private[core] def closeTransport(promise: ChannelPromise): Unit

    private[core] def shutdownTransport(direction: ChannelShutdownDirection, promise: ChannelPromise): Unit

    /** Call by head handler on pipeline register outbound event.
     *
     *  send channel register to reactor, and handle reactor reply at [[handleChannelRegisterReplyEvent]]
     */
    private[core] def registerTransport(promise: ChannelPromise): Unit

    private[core] def deregisterTransport(promise: ChannelPromise): Unit

    private[core] def readTransport(readPlan: ReadPlan): Unit = reactor.read(this, readPlan)

    //// Event Handler: A handle that will process [[Event]] from [[Reactor]] and [[Timer]].

    // Event from Reactor

    /** Handle channel close event */
    private[core] def handleChannelCloseEvent(event: ReactorEvent.ChannelClose): Unit = {}

    /** Handle channel register result event */
    private[core] def handleChannelRegisterReplyEvent(event: ReactorEvent.RegisterReply): Unit = {}

    /** Handle channel deregister result event */
    private[core] def handleChannelDeregisterReplyEvent(event: ReactorEvent.DeregisterReply): Unit = {}

    /** Handle channel readiness event */
    private[core] def handleChannelReadinessEvent(event: ReactorEvent.ChannelReadiness): Unit = {}

    private[core] def handleChannelBindReplyEvent(event: ReactorEvent.BindReply): Unit = {}

    private[core] def handleChannelConnectReplyEvent(event: ReactorEvent.ConnectReply): Unit = {}

    private[core] def handleChannelDisconnectReplyEvent(event: ReactorEvent.DisconnectReply): Unit = {}

    private[core] def handleChannelOpenReplyEvent(event: ReactorEvent.OpenReply): Unit = {}

    // Event from Timer

    /** Handle channel timeout event */
    private[core] def handleChannelTimeoutEvent(eventRegisterId: Long): Unit = {}

}

object AbstractChannel {

    private val TURE_FUNC: AnyRef => Boolean  = _ => true
    private val FALSE_FUNC: AnyRef => Boolean = _ => false

    private val SUPPORTED_CHANNEL_OPTIONS: Set[ChannelOption[?]] = Set(
      CHANNEL_FUTURE_BARRIER,
      CHANNEL_STACK_BARRIER,
      CHANNEL_MAX_FUTURE_INFLIGHT,
      CHANNEL_MAX_STACK_INFLIGHT,
      CHANNEL_STACK_HEAD_OF_LINE
    )

}
