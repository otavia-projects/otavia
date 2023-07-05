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

package io.otavia.core.channel

import io.otavia.buffer.{Buffer, BufferAllocator}
import io.otavia.core.actor.ChannelsActor
import io.otavia.core.buffer.AdaptiveBuffer
import io.otavia.core.channel.AbstractNetChannel.*
import io.otavia.core.channel.ChannelOption.*
import io.otavia.core.channel.estimator.*
import io.otavia.core.channel.inflight.ChannelInflightImpl
import io.otavia.core.channel.internal.{ChannelOutboundBuffer, ReadSink, WriteBufferWaterMark, WriteSink}
import io.otavia.core.channel.message.{ReadPlan, ReadPlanFactory}
import io.otavia.core.message.{ReactorEvent, TimeoutEvent}
import io.otavia.core.slf4a.Logger
import io.otavia.core.stack.*
import io.otavia.core.system.ActorThread
import io.otavia.core.timer.{TimeoutTrigger, Timer}
import io.otavia.core.util.{Platform, ThrowableUtil}

import java.net.*
import java.nio.channels.{AlreadyConnectedException, ClosedChannelException, ConnectionPendingException, NotYetConnectedException}
import java.nio.file.attribute.FileAttribute
import java.nio.file.{OpenOption, Path}
import scala.collection.mutable
import scala.concurrent.Future.{find, never}
import scala.language.unsafeNulls
import scala.util.{Failure, Success, Try}

/** A skeletal Channel implementation.
 *
 *  @param supportingDisconnect
 *    true if and only if the channel has the [[disconnect]] operation that allows a user to disconnect and then call
 *    [[Channel.connect]] again, such as UDP/IP.
 *  @param defaultWriteHandleFactory
 *    the [[WriteHandleFactory]] that is used by default.
 *  @tparam L
 *    type of local address
 *  @tparam R
 *    type of remote address
 */
abstract class AbstractNetChannel[L <: SocketAddress, R <: SocketAddress] protected (
    val supportingDisconnect: Boolean,
    val defaultWriteHandleFactory: WriteHandleFactory
) extends AbstractChannel,
      WriteSink,
      ReadSink,
      ChannelInflightImpl,
      ChannelInternal[L, R],
      ChannelInboundBuffer {

    /** Creates a new instance.
     *
     *  @param supportingDisconnect
     *    true if and only if the channel has the [[disconnect]] operation that allows a user to disconnect and then
     *    call [[Channel.connect]] again, such as UDP/IP.
     */
    protected def this(supportingDisconnect: Boolean) = this(
      supportingDisconnect,
      new MaxMessagesWriteHandleFactory(Int.MaxValue)
    )

    private var outboundBuffer: ChannelOutboundBuffer | Null = new ChannelOutboundBuffer()

    private var writeHandleFactory: WriteHandleFactory = defaultWriteHandleFactory

    private var msgSizeEstimator: MessageSizeEstimator = DEFAULT_MSG_SIZE_ESTIMATOR
    private var connectTimeoutMillis: Int              = DEFAULT_CONNECT_TIMEOUT
    private var connectTimeoutRegisterId: Long         = Timer.INVALID_TIMEOUT_REGISTER_ID

    // writeBufferWaterMark
    private var waterMarkLow: Int  = WriteBufferWaterMark.DEFAULT_LOW_WATER_MARK
    private var waterMarkHigh: Int = WriteBufferWaterMark.DEFAULT_HIGH_WATER_MARK

    private var readBeforeActive: ReadPlan = _

    private lazy val estimatorHandle: MessageSizeEstimator.Handle = msgSizeEstimator.newHandle

    private var registerPromise: ChannelPromise   = _
    private var connectPromise: ChannelPromise    = _
    private var requestedRemoteAddress: R | Null  = _
    private var deregisterPromise: ChannelPromise = _
    private var closePromise: ChannelPromise      = _

    private def readSink: ReadSink   = this
    private def writeSink: WriteSink = this

    private def closeIfClosed(): Unit = if (!isOpen) closeTransport(newPromise())

    override def localAddress: Option[SocketAddress] = localAddress0

    override def remoteAddress: Option[SocketAddress] = remoteAddress0

    override def isRegistered: Boolean = registered

    private def totalPending: Long = outboundBuffer match
        case null                       => -1
        case out: ChannelOutboundBuffer => out.totalPendingWriteBytes + pipeline.pendingOutboundBytes

    override final def writableBytes: Long = {
        val totalPending = this.totalPending
        if (totalPending == -1) 0
        else {
            val bytes = ???
        }
        ???
    }

    override def hashCode(): Int = id

    override def toString: String = {
        if (remoteAddress.nonEmpty) {
            val buf = new mutable.StringBuilder(128)
                .append("[id: ")
                .append(id)
                .append(", L:")
                .append(if localAddress.nonEmpty then localAddress.get else "None")
                .append(if isActive then " - " else " ! ")
                .append("R:")
                .append(remoteAddress.get)
                .append(']')
            buf.toString()
        } else if (localAddress.nonEmpty) {
            val buf = new mutable.StringBuilder(128)
                .append("[id: ")
                .append(id)
                .append(", L:")
                .append(localAddress.get)
                .append(']')
            buf.toString()
        } else {
            val buf = new mutable.StringBuilder(64)
                .append("[id: ")
                .append(id)
                .append(']')
            buf.toString()
        }
    }

    protected final def readIfIsAutoRead(): Unit = if (isAutoRead) read()

    /** Calls [[ChannelPipeline.fireChannelActive]] if it was not done yet.
     *
     *  @return
     *    true if [[ChannelPipeline.fireChannelActive]] was called, false otherwise.
     */
    private def fireChannelActiveIfNotActiveBefore(): Boolean = if (neverActive) {
        neverActive = false
        pipeline.fireChannelActive()
        true
    } else false

    /** use in register */
    private def closeNowAndFail(promise: Promise[?], cause: Throwable): Unit = {
        closing = true
        try {
            doClose()
        } catch {
            case e: Exception => logger.warn("Failed to close a channel.", e)
        }
        closed = true
        closing = false
        promise.setFailure(cause)
    }

    override private[core] def closeAfterCreate(): Unit = try {
        closing = true
        doClose()
        closed = true
        closing = false
    } catch {
        case cause: Throwable =>
            logger.error(s"close channel $this occur error with ${ThrowableUtil.stackTraceToString(cause)}")
    }

    /** Call by head handler on pipeline register outbound event.
     *
     *  send channel register to reactor, and handle reactor reply at [[handleChannelRegisterReplyEvent]]
     */
    override private[core] def registerTransport(promise: ChannelPromise): Unit = {
        if (registering) promise.setFailure(new IllegalStateException(s"The channel $this is registering to reactor!"))
        else if (isRegistered) promise.setFailure(new IllegalStateException("registered to reactor already"))
        else {
            registering = true
            registerPromise = promise
            reactor.register(this)
        }
    }

    override private[core] def handleChannelRegisterReplyEvent(event: ReactorEvent.RegisterReply): Unit =
        event.cause match
            case None =>
                val firstRegistration = neverRegistered
                neverRegistered = false
                registering = false
                registered = true
                pipeline.fireChannelRegistered()
                val promise = registerPromise
                registerPromise = null
                promise.setSuccess(event)
                // Only fire a channelActive if the channel has never been registered. This prevents firing
                // multiple channel actives if the channel is deregistered and re-registered.
                if (isActive) {
                    if (firstRegistration) fireChannelActiveIfNotActiveBefore()
                    readIfIsAutoRead()
                }
            case Some(cause) =>
                val promise = registerPromise
                registerPromise = null
                closeNowAndFail(promise, cause)

    override private[core] def bindTransport(local: SocketAddress, channelPromise: ChannelPromise): Unit = {
        if (!mounted) channelPromise.setFailure(new IllegalStateException(s"channel $this is not mounted to actor!"))
        else if (!registered) { // if not register
            if (registering) channelPromise.setFailure(new IllegalStateException("A registering operation is running"))
            else {
                pipeline.register(newPromise())
                registerPromise.onCompleted {
                    case self if self.isSuccess =>
                        this.registerPromise = null
                        bindTransport0(local, channelPromise)
                    case self if self.isFailed =>
                        this.registerPromise = null
                        channelPromise.setFailure(self.causeUnsafe)
                }
            }
        } else bindTransport0(local, channelPromise)
    }

    private def bindTransport0(local: SocketAddress, promise: ChannelPromise): Unit = {
        binding = true
        local match {
            case address: InetSocketAddress
                if isOptionSupported(ChannelOption.SO_BROADCAST) && getOption[Boolean](ChannelOption.SO_BROADCAST) &&
                    !address.getAddress.isAnyLocalAddress && !Platform.isWindows && !Platform.maybeSuperUser =>
                // Warn a user about the fact that a non-root user can't receive a
                // broadcast packet on *nix if the socket is bound on non-wildcard address.
                logger.warn(
                  "A non-root user can't receive a broadcast packet if the socket " +
                      "is not bound to a wildcard address; binding to a non-wildcard " +
                      s"address ($address) anyway as requested."
                )
            case _ =>
        }

        val wasActive = isActive

        var success: Boolean = false
        try {
            setUnresolvedLocalAddress(local)
            doBind()
            success = true
        } catch {
            case cause: Throwable =>
                promise.setFailure(cause)
                closeIfClosed()
        }
        if (success) {
            bound = true
            binding = false
            if (!wasActive && isActive) // first active
                if (fireChannelActiveIfNotActiveBefore()) readIfIsAutoRead()

            promise.setSuccess(ReactorEvent.EMPTY_EVENT)
        }

    }

    override private[core] def disconnectTransport(promise: ChannelPromise): Unit = {
        val wasActive = isActive
        try {
            doDisconnect()
            // Reset remoteAddress and localAddress
            clearRemoteAddress()
            clearLocalAddress()
            neverActive = true
        } catch {
            case t: Throwable => closeIfClosed(); return
        }

        if (wasActive && !isActive) invokeLater(() => pipeline.fireChannelInactive())

        closeIfClosed() // TCP channel disconnect is close, UDP is cancel local SocketAddress binding.
    }

    override private[core] def closeTransport(promise: ChannelPromise): Unit = {}

    private def close(promise: ChannelPromise, cause: Throwable, closeCause: ClosedChannelException): Unit = {
        if (closed) promise.setSuccess(ReactorEvent.EMPTY_EVENT)
        else if (closing && !closed) promise.setFailure(new IllegalStateException("A close operation is running"))
        else {
            closing = true
            val wasActive = isActive
            closeAdaptiveBuffers()
            val exception = new ClosedChannelException()
            failInflights(exception)
            cancelConnect(exception)
            if (unregistered) {
                closeAfterDeregister(wasActive)
            } else {
                if (!unregistering && !unregistered) deregisterTransport(newPromise())
                deregisterPromise.onCompleted {
                    case self if self.isSuccess =>
                        this.deregisterPromise = null
                        closeAfterDeregister(wasActive)
                    case self if self.isFailed =>
                        this.deregisterPromise = null
                        promise.setFailure(self.causeUnsafe)
                }
            }
        }
    }

    private def closeAfterDeregister(wasActive: Boolean): Unit = {
        if (isOpen && getOption(ChannelOption.SO_LINGER) > 0) {
            val future = system.aio.submitClose(this) // TODO: replace by aio
            future.onCompleted { self => fireChannelInactive(wasActive, self.cause) }
            val blockFuture = system.executeBlocking(() => doClose(), this)
            blockFuture.promise.onCompleted { self =>
                fireChannelInactive(wasActive, self.cause)
            }
        } else {
            var cause: Option[Throwable] = None
            try { doClose() }
            catch {
                case t: Throwable => cause = Some(t)
            }
            fireChannelInactive(wasActive, cause)
        }
    }

    private def fireChannelInactive(wasActive: Boolean, cause: Option[Throwable]): Unit = {
        if (wasActive && !isActive) pipeline.fireChannelInactive()
        val promise = closePromise
        closePromise = null
        cause match
            case Some(value) => promise.setFailure(value)
            case None        => promise.setSuccess(ReactorEvent.EMPTY_EVENT)
        closing = false
        closed = true
    }

    private def closeAdaptiveBuffers(): Unit = {
        closeInboundAdaptiveBuffers()
        closeOutboundAdaptiveBuffers()
    }

    private def closeInboundAdaptiveBuffers(): Unit = {
        shutdownedInbound = true
        pipeline.closeInboundAdaptiveBuffers()
    }

    private def closeOutboundAdaptiveBuffers(): Unit = {
        shutdownedOutbound = true
        pipeline.closeOutboundAdaptiveBuffers()
    }

    private def failInflights(cause: Throwable): Unit = {
        // TODO: impl
    }

    private def cancelConnect(cause: Throwable): Unit = if (connectPromise != null) {
        val promise = connectPromise
        connectPromise = null
        if (promise.canTimeout) timer.cancelTimerTask(promise.timeoutId)
        promise.setFailure(cause)
    }

    override private[core] def shutdownTransport(
        direction: ChannelShutdownDirection,
        promise: ChannelPromise
    ): Unit = {
        if (!isActive) {
            if (isOpen) promise.setFailure(new NotYetConnectedException())
            else promise.setFailure(new ClosedChannelException())
        } else if (isShutdown(direction)) promise.setSuccess(ReactorEvent.EMPTY_EVENT)
        else {
            val fireEvent = direction match
                case ChannelShutdownDirection.Outbound => shutdownOutput(promise, None)
                case ChannelShutdownDirection.Inbound  => shutdownInput(promise)
            if (fireEvent) pipeline.fireChannelShutdown(direction)
        }
    }

    /** Shutdown the output portion of the corresponding Channel. For example this will close the
     *  [[channelOutboundAdaptiveBuffer]] and not allow any more writes.
     *
     *  @param promise
     *    [[ChannelPromise]]
     *  @param cause
     *    The cause which may provide rational for the shutdown.
     *  @return
     */
    private def shutdownOutput(promise: ChannelPromise, cause: Option[Throwable]): Boolean = {
        if (closed) {
            promise.setFailure(new ClosedChannelException())
            false
        } else {
            val shutdownCause = cause match
                case Some(t) => new ChannelOutputShutdownException("Channel output shutdown", t)
                case None    => new ChannelOutputShutdownException("Channel output shutdown")
            try {
                doShutdown(ChannelShutdownDirection.Outbound)
                closeOutboundAdaptiveBuffers()
                promise.setSuccess(ReactorEvent.EMPTY_EVENT)
            } catch {
                case t: Throwable => promise.setFailure(t)
            } finally {
                failInflights(shutdownCause)
            }
            true
        }
    }

    /** Shutdown the input portion of the corresponding Channel. For example this will close the
     *  [[channelInboundAdaptiveBuffer]] and not allow any more writes.
     *
     *  @param promise
     *    [[ChannelPromise]]
     *  @return
     */
    private def shutdownInput(promise: ChannelPromise): Boolean = {
        try {
            doShutdown(ChannelShutdownDirection.Inbound)
            closeInboundAdaptiveBuffers()
            promise.setSuccess(ReactorEvent.EMPTY_EVENT)
            true
        } catch {
            case cause: Throwable =>
                promise.setFailure(cause)
                false
        }
    }

    override private[core] def deregisterTransport(promise: ChannelPromise): Unit = {
        if (!registered) promise.setSuccess(ReactorEvent.EMPTY_EVENT)
        else if (!unregistering && !unregistered) {
            deregisterPromise = promise
            deregisterPromise.onSuccess(self => deregisterDone(self))
            unregistering = true
            invokeLater(() => reactor.deregister(this))
        } else if (unregistering && !unregistered) {
            deregisterPromise.addListener(promise)
        } else {
            promise.setSuccess(ReactorEvent.EMPTY_EVENT)
        }
    }

    private def deregisterDone(promise: ChannelPromise): Unit = {
        // Ensure we also clear all scheduled reads so its possible to schedule again if the Channel is re-registered.
        clearScheduledRead()

        if (registered) {
            registered = false
            unregistering = false
            unregistered = true

            if (!isOpen) {
                // Remove all handlers from the ChannelPipeline. This is needed to ensure
                // handlerRemoved(...) is called and so resources are released.
                while (!pipeline.isEmpty) {
                    try { pipeline.removeLast() }
                    catch {
                        case _: Throwable => // do nothing
                    }
                }
            }
        }
    }

    override private[core] def handleChannelDeregisterReplyEvent(event: ReactorEvent.DeregisterReply): Unit = {
        event.cause match
            case Some(cause) =>
                logger.warn("Unexpected exception occurred while deregistering a channel.", cause)
            case None =>

        // done deregister
        val promise = deregisterPromise
        deregisterPromise = null
        promise.setSuccess(ReactorEvent.EMPTY_EVENT)
    }

    override private[core] def readTransport(readPlan: ReadPlan): Unit = if (!isActive) {
        throw new IllegalStateException(s"channel $this is not active!")
    } else if (isShutdown(ChannelShutdownDirection.Inbound)) {
        // Input was shutdown so not try to read.
    } else {
        readSink.setReadPlan(readPlan)
        try { doRead() }
        catch {
            case e: Exception =>
                invokeLater(() => pipeline.fireChannelExceptionCaught(e))
//                closeTransport()
        }
    }

    /** Reading from the underlying transport now until there is nothing more to read or the [[ReadPlan]] is telling us
     *  to stop.
     */
    protected final def readNow(): Unit = {
        if (isShutdown(ChannelShutdownDirection.Inbound) && (inputClosedSeenErrorOnRead || !isAllowHalfClosure)) {
            // There is nothing to read anymore.
            clearScheduledRead() // TODO: clearScheduledRead()
        } else readSink.readLoop()
    }

    /** Shutdown the read side of this channel. Depending on if half-closure is supported or not this will either just
     *  shutdown the [[ChannelShutdownDirection.Inbound]] or close the channel completely.
     */
    protected final def shutdownReadSide(): Unit = if (!isShutdown(ChannelShutdownDirection.Inbound)) {
        if (isAllowHalfClosure) shutdownTransport(ChannelShutdownDirection.Inbound, newPromise())
        else closeTransport(newPromise())
    } else inputClosedSeenErrorOnRead = true

    private def clearScheduledRead(): Unit = doClearScheduledRead()

    override private[core] def writeTransport(msg: AnyRef): Unit = {
        var size: Int = 0
        try {
            val message = filterOutboundMessage(msg)
//      size =
        } catch {
            case t: Throwable =>
        }

    }

    override private[core] def flushTransport(): Unit = {
        outboundBuffer match
            case null =>
            case out: ChannelOutboundBuffer =>
                out.addFlush()
                writeFlushed()
    }

    /** Writing previous flushed messages if [[isWriteFlushedScheduled]] returns false, otherwise do nothing. */
    protected final def writeFlushed(): Unit = if (!isWriteFlushedScheduled) writeFlushedNow()

    /** Writing previous flushed messages now. */
    protected final def writeFlushedNow(): Unit = {
        outboundBuffer match
            case null =>
            case out: ChannelOutboundBuffer =>
                inWriteFlushed = true
                try {
                    if (!isActive) {
                        if (!out.isEmpty) {
                            ???
                        }
                    } else {
                        writeSink.writeLoop(out)
                    }
                } finally {
                    inWriteFlushed = false
                }
    }

    protected def writeLoopComplete(allWriten: Boolean): Unit = if (!allWriten) invokeLater(() => writeFlushed())

    override private[core] def connectTransport(
        remote: SocketAddress,
        local: Option[SocketAddress],
        promise: ChannelPromise
    ): Unit = {
        if (!mounted) promise.setFailure(new IllegalStateException(s"channel $this is not mounted to actor!"))
        else if (connected) promise.setFailure(new AlreadyConnectedException())
        else if (connecting) promise.setFailure(new ConnectionPendingException())
        else if (closed || closing) promise.setFailure(new ClosedChannelException())
        else if (!registered) { // if not register
            if (!registering) pipeline.register(newPromise())
            registerPromise.addListener(promise)
            promise.onSuccess { self => connectTransport0(remote, local, self) }
            promise.onFailure { self => cancelConnect(self.causeUnsafe) }
        } else {
            connectTransport0(remote, local, promise)
        }
    }

    private def connectTransport0(
        remote: SocketAddress,
        local: Option[SocketAddress],
        promise: ChannelPromise
    ): Unit = {
        if (connected) promise.setFailure(new AlreadyConnectedException())
        else if (connecting) promise.setFailure(new ConnectionPendingException())
        else {
            connecting = true
            this.connectPromise = promise
            val wasActive  = isActive
            val fastOption = ChannelOption.TCP_FASTOPEN_CONNECT
            val fastOpen   = if (isOptionSupported(fastOption) && getOption(fastOption)) true else false
            if (doConnect(remote, local, fastOpen)) { // Connect finished
                fulfillConnect(wasActive)
            } else {
                requestedRemoteAddress = remote.asInstanceOf[R]
                // Connect not finish, the connectPromise will handle by handleChannelReadinessEvent or handleConnectTimeout
                // The reactor will send a [ReactorEvent.ChannelReadiness] event to ChannelsActor, then ChannelsActor will
                // call the channel's handleChannelReadinessEvent method which call finishConnect.

                // register connect timeout trigger. When timeout, the timer will send a timeout event, the
                // handleConnectTimeout method will handle this timeout event.
                if (connectTimeoutMillis > 0) {
                    val tid = timer.registerChannelTimeout(TimeoutTrigger.DelayTime(connectTimeoutMillis), this)
                    connectTimeoutRegisterId = tid
                    promise.setTimeoutId(tid)
                }
            }
        }
    }

    private def handleConnectTimeout(): Unit = {
        val promise = connectPromise
        connectPromise = null
        closeTransport(newPromise()) // close the channel and ignore close result.

        promise.setFailure(new ConnectTimeoutException(s"connection timed out: $requestedRemoteAddress"))
    }

    override private[core] def handleChannelTimeoutEvent(eventRegisterId: Long): Unit = {
        if (eventRegisterId == connectTimeoutRegisterId) {
            // handle connect timeout event.
            if (connecting) handleConnectTimeout() // else ignore the event
        } else {
            // fire other timeout event to pipeline.
            pipeline.fireChannelTimeoutEvent(eventRegisterId)
        }
    }

    /** Should be called once the connect request is ready to be completed and [[isConnectPending]] is `true`. Calling
     *  this method if no [[isConnectPending]] connect is pending will result in an [[AlreadyConnectedException]].
     *
     *  @return
     *    `true` if the connect operation completed, `false` otherwise.
     */
    protected def finishConnect(): Unit = {
        if (connectPromise != null) {
            var connectStillInProgress = false
            try {
                val wasActive = isActive
                if (!doFinishConnect(requestedRemoteAddress.nn)) {
                    connectStillInProgress = true
                } else { // connect success
                    requestedRemoteAddress = null
                    fulfillConnect(wasActive)
                }
            } catch {
                case t: Throwable => fulfillConnect(annotateConnectException(t, requestedRemoteAddress))
            }
        }
    }

    private def fulfillConnect(wasActive: Boolean): Unit = {
        if (connectPromise == null) {
            // closed and the promise has been notified already.
        } else {
            val active  = isActive
            val promise = connectPromise
            connectPromise = null
            connected = true
            connecting = false
            if (promise.canTimeout)
                timer.cancelTimerTask(promise.timeoutId)
            promise.setSuccess(ReactorEvent.EMPTY_EVENT)
            if (!wasActive && active) if (fireChannelActiveIfNotActiveBefore()) readIfIsAutoRead()
        }
    }

    private def fulfillConnect(cause: Throwable): Unit = {
        if (connectPromise != null) {
            val promise = connectPromise
            connectPromise = null
            connecting = false
            if (promise.canTimeout) timer.cancelTimerTask(promise.timeoutId)
            promise.setFailure(cause)
            closeIfClosed()
        }
    }

    private[core] def updateWritabilityIfNeeded(notify: Boolean, notifyLater: Boolean): Unit = {
        val totalPending = this.totalPending
        if (totalPending > waterMarkHigh) {
            writable = false
            fireChannelWritabilityChangedIfNeeded(notify, notifyLater)
        } else if (totalPending < waterMarkLow) {
            writable = true
            fireChannelWritabilityChangedIfNeeded(notify, notifyLater)
        }
    }

    private def fireChannelWritabilityChangedIfNeeded(notify: Boolean, later: Boolean): Unit = if (notify) {
        if (later) invokeLater(() => pipeline.fireChannelWritabilityChanged())
        else
            pipeline.fireChannelWritabilityChanged()
    }

    override private[core] def openTransport(
        path: Path,
        options: Seq[OpenOption],
        attrs: Seq[FileAttribute[?]],
        promise: ChannelPromise
    ): Unit = {
        promise.setFailure(new UnsupportedOperationException())
    }

    /** Invoked when a new message is added to to the outbound queue of this [[AbstractNetChannel]], so that the
     *  [[Channel]] implementation converts the message to another. (e.g. heap buffer -> direct buffer)
     *
     *  @param msg
     *    the message to filter / convert.
     *  @throws Exception
     *    thrown on error.
     *  @return
     */
    protected def filterOutboundMessage(msg: AnyRef): AnyRef = msg

    /** Returns true if the implementation supports disconnecting and re-connecting, false otherwise.
     *  @return
     *    true if supported.
     */
    protected def isSupportingDisconnect: Boolean = supportingDisconnect

    override final def getOption[T](option: ChannelOption[T]): T = option match
        case AUTO_READ               => ???
        case WRITE_BUFFER_WATER_MARK => ???
        case CONNECT_TIMEOUT_MILLIS  => connectTimeoutMillis.asInstanceOf[T]
        case BUFFER_ALLOCATOR        => ???
        case READ_HANDLE_FACTORY     => ???
        case WRITE_HANDLE_FACTORY    => ???
        case AUTO_CLOSE              => ???
        case MESSAGE_SIZE_ESTIMATOR  => msgSizeEstimator.asInstanceOf[T]
        case ALLOW_HALF_CLOSURE      => ???
        case _                       => getExtendedOption(option)

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
        option.validate(value)
        option match
            case AUTO_READ               => setAutoRead(value.asInstanceOf[Boolean])
            case WRITE_BUFFER_WATER_MARK => ???
            case CONNECT_TIMEOUT_MILLIS  => ???
            case BUFFER_ALLOCATOR        => ???
            case READ_HANDLE_FACTORY     => ???
            case WRITE_HANDLE_FACTORY    => ???
            case AUTO_CLOSE              => ???
            case MESSAGE_SIZE_ESTIMATOR  => msgSizeEstimator = value.asInstanceOf[MessageSizeEstimator]
            case ALLOW_HALF_CLOSURE      => ???

        this
    }

    /** Override to add support for more [[ChannelOption]]s. You need to also call super after handling the extra
     *  options.
     *
     *  @param option
     *    the [[ChannelOption]].
     *  @param <
     *    T> the value type.
     *  @throws UnsupportedOperationException
     *    if the [[ChannelOption]] is not supported.
     */
    protected def setExtendedOption[T](option: ChannelOption[T], value: T): Unit =
        throw new UnsupportedOperationException(s"ChannelOption not supported: $option")

    override def isOptionSupported(option: ChannelOption[?]): Boolean = SUPPORTED_CHANNEL_OPTIONS.contains(option) ||
        isExtendedOptionSupported(option)

    /** Override to add support for more [[ChannelOption]]s. You need to also call super after handling the extra
     *  options.
     *
     *  @param option
     *    the [[ChannelOption]].
     *  @return
     *    true if supported, false otherwise.
     */
    protected def isExtendedOptionSupported(option: ChannelOption[?]) = false

    private def getConnectTimeoutMillis = connectTimeoutMillis

    private def setConnectTimeoutMillis(connectTimeoutMillis: Int): Unit = {
        assert(connectTimeoutMillis >= 0, s"connectTimeoutMillis $connectTimeoutMillis (expected: >= 0)")
        this.connectTimeoutMillis = connectTimeoutMillis
    }

    protected def newReadPlan: ReadPlan = readPlanFactory.newPlan(this)

    protected def newWriteHandle: WriteHandleFactory.WriteHandle = writeHandleFactory.newHandle(this)

    @SuppressWarnings(Array("unchecked"))
    private def getWriteHandleFactory[T <: WriteHandleFactory] = writeHandleFactory.asInstanceOf[T]

    private def setWriteHandleFactory(writeHandleFactory: WriteHandleFactory): Unit = {
        this.writeHandleFactory = writeHandleFactory
    }

    private def isAutoRead = autoRead

    private def setAutoRead(auto: Boolean): Unit = {
        assertExecutor()
        val old = autoRead
        autoRead = auto
        if (auto && !old) read() else if (!auto && old) clearScheduledRead()
    }

    private def isAutoClose = autoClose

    private def setAutoClose(auto: Boolean): Unit = autoClose = auto

    private def getMessageSizeEstimator = msgSizeEstimator

    private def setMessageSizeEstimator(estimator: MessageSizeEstimator): Unit =
        msgSizeEstimator = estimator

    private def isAllowHalfClosure = allowHalfClosure

    private def setAllowHalfClosure(allow: Boolean): Unit = allowHalfClosure = allow

    /** Called once the read loop completed for this Channel. Sub-classes might override this method but should also
     *  call super.
     */
    protected def readLoopComplete(): Unit = {
        // NOOP
    }

    final protected def newPromise(): ChannelPromise = ChannelPromise()

}

object AbstractNetChannel {

    final val DEFAULT_MSG_SIZE_ESTIMATOR: MessageSizeEstimator = DefaultMessageSizeEstimator
    final val DEFAULT_CONNECT_TIMEOUT                          = 30000
    final val SUPPORTED_CHANNEL_OPTIONS: Set[ChannelOption[?]] = Set(
      AUTO_READ,
      WRITE_BUFFER_WATER_MARK,
      CONNECT_TIMEOUT_MILLIS,
      BUFFER_ALLOCATOR,
      READ_HANDLE_FACTORY,
      WRITE_HANDLE_FACTORY,
      AUTO_CLOSE,
      MESSAGE_SIZE_ESTIMATOR,
      ALLOW_HALF_CLOSURE
    )

    private def annotateConnectException(cause: Throwable, remote: SocketAddress): Throwable = {
        cause match
            case connectException: ConnectException => new AnnotatedConnectException(connectException, remote)
            case noRouteToHostException: NoRouteToHostException =>
                new AnnotatedNoRouteToHostException(noRouteToHostException, remote)
            case socketException: SocketException => new AnnotatedSocketException(socketException, remote)
            case _                                => cause
    }

    private final class AnnotatedConnectException(exception: ConnectException, remote: SocketAddress)
        extends ConnectException(exception.getMessage + ": " + remote) {

        initCause(exception)

        override def fillInStackTrace(): Throwable = this

    }

    private final class AnnotatedNoRouteToHostException(exception: NoRouteToHostException, remote: SocketAddress)
        extends NoRouteToHostException(exception.getMessage + ": " + remote) {

        initCause(exception)

        override def fillInStackTrace(): Throwable = this

    }

    private final class AnnotatedSocketException(exception: SocketException, remote: SocketAddress)
        extends SocketException(exception.getMessage + ": " + remote) {

        initCause(exception)

        override def fillInStackTrace(): Throwable = this

    }

}
