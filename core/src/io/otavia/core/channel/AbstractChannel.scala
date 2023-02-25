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

import io.netty5.buffer.{Buffer, BufferAllocator}
import io.netty5.util.DefaultAttributeMap
import io.netty5.util.internal.ObjectUtil.{checkPositiveOrZero, longValue}
import io.netty5.util.internal.PlatformDependent
import io.otavia.core.actor.ChannelsActor
import io.otavia.core.channel.AbstractChannel.*
import io.otavia.core.channel.ChannelOption.*
import io.otavia.core.channel.estimator.*
import io.otavia.core.channel.internal.{ChannelOutboundBuffer, ReadSink, WriteBufferWaterMark, WriteSink}
import io.otavia.core.reactor.{ReactorEvent, TimeoutEvent}
import io.otavia.core.system.ActorThread
import io.otavia.core.timer.Timer
import io.otavia.core.timer.Timer.TimeoutTrigger
import io.otavia.core.util.ActorLogger

import java.net.{InetSocketAddress, SocketAddress}
import java.nio.channels.ClosedChannelException
import java.util.Objects.requireNonNull
import scala.collection.mutable
import scala.concurrent.Future.never
import scala.language.unsafeNulls
import scala.util.{Failure, Success, Try}

/** A skeletal Channel implementation.
 *
 *  @param executor
 *    the [[ChannelsActor]] which will be used.
 *  @param supportingDisconnect
 *    true if and only if the channel has the [[disconnect]] operation that allows a user to disconnect and then call
 *    [[Channel.connect]] again, such as UDP/IP.
 *  @param defaultReadHandleFactory
 *    the [[ReadHandleFactory]] that is used by default.
 *  @param defaultWriteHandleFactory
 *    the [[WriteHandleFactory]] that is used by default.
 *  @tparam L
 *    type of local address
 *  @tparam R
 *    type of remote address
 */
abstract class AbstractChannel[L <: SocketAddress, R <: SocketAddress] protected (
    val supportingDisconnect: Boolean,
    val defaultReadHandleFactory: ReadHandleFactory,
    val defaultWriteHandleFactory: WriteHandleFactory
) extends DefaultAttributeMap,
      Channel,
      WriteSink,
      ReadSink,
      ChannelInflight,
      ChannelInternal[L, R],
      ChannelLifecycle,
      ChannelInboundBuffer {

    /** Creates a new instance.
     *
     *  @param executor
     *    the [[ChannelsActor]] which will be used.
     *  @param supportingDisconnect
     *    true if and only if the channel has the [[disconnect]] operation that allows a user to disconnect and then
     *    call [[Channel.connect]] again, such as UDP/IP.
     */
    protected def this(supportingDisconnect: Boolean) = this(
      supportingDisconnect,
      new AdaptiveReadHandleFactory(),
      new MaxMessagesWriteHandleFactory(Int.MaxValue)
    )

    protected var logger: ActorLogger = _

    private var channelId: Int = -1

    // initial channel state on constructing
    created = true
    registering = false
    registered = false

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
    private var initialCloseCause: Throwable | Null = null

    override val pipeline: ChannelPipeline = newChannelPipeline()

    private var outboundBuffer: ChannelOutboundBuffer | Null = new ChannelOutboundBuffer()

    private var readHandleFactory: ReadHandleFactory   = defaultReadHandleFactory
    private var writeHandleFactory: WriteHandleFactory = defaultWriteHandleFactory

    private var msgSizeEstimator: MessageSizeEstimator = DEFAULT_MSG_SIZE_ESTIMATOR
    private var connectTimeoutMillis: Int              = DEFAULT_CONNECT_TIMEOUT
    private var connectTimeoutRegisterId: Long         = Timer.INVALID_TIMEOUT_REGISTER_ID

    // writeBufferWaterMark
    private var waterMarkLow: Int  = WriteBufferWaterMark.DEFAULT_LOW_WATER_MARK
    private var waterMarkHigh: Int = WriteBufferWaterMark.DEFAULT_HIGH_WATER_MARK

    private var readBeforeActive: ReadBufferAllocator = _

    private lazy val estimatorHandle: MessageSizeEstimator.Handle = msgSizeEstimator.newHandle

    private var actor: ChannelsActor[?] | Null = _

    override def executor: ChannelsActor[?] = actor match
        case a: ChannelsActor[?] => a
        case null =>
            throw new IllegalStateException(s"The channel $this is not mounted, use setExecutor to mount channel.")

    final private[core] def setExecutor(channelsActor: ChannelsActor[?]): Unit = {
        assert(!mounted, s"The channel $this has been mounted already, you can't mount it twice!")
        actor = channelsActor
        logger = ActorLogger.getLogger(getClass)(using executor)
        channelId = executor.generateChannelId()
        resetInboundBuffer()
        mounted = true
    }

    protected def currentThread: ActorThread = Thread.currentThread().asInstanceOf[ActorThread]

    private def laterTasks = currentThread.laterTasks

    override def id: Int = channelId

    private def readSink: ReadSink   = this
    private def writeSink: WriteSink = this

    /** Returns a new [[ChannelPipeline]] instance. */
    private def newChannelPipeline(): ChannelPipeline = new OtaviaChannelPipeline(this)

    private def closeIfClosed(): Unit = if (!isOpen) closeTransport()

    private def invokeLater(task: Runnable): Unit = laterTasks.append(task)

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

    override def toString: String = if (remoteAddress.nonEmpty) {
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

    private def closeNowAndFail(cause: Throwable): Unit = try { doClose() }
    catch { case e: Exception => logger.logWarn("Failed to close a channel.", e) }

    /** Call by head handler on pipeline register oubound event.
     *
     *  send channel register to reactor, and handle reactor reply at [[handleChannelRegisterReplyEvent]]
     */
    private[channel] def registerTransport(): Unit = {
        if (registering) throw new IllegalStateException(s"The channel ${this} is registering to reactor!")
        if (isRegistered) throw new IllegalStateException("registered to reactor already")
        registering = true
        reactor.register(this)
    }

    override private[core] def handleChannelRegisterReplyEvent(event: ReactorEvent.RegisterReply): Unit =
        event.cause match
            case None =>
                val firstRegistration = neverRegistered
                neverRegistered = false
                registering = false
                registered = true
                pipeline.fireChannelRegistered()
                if (isActive) {
                    if (firstRegistration) fireChannelActiveIfNotActiveBefore()
                    readIfIsAutoRead()
                }
            case Some(cause) => closeNowAndFail(cause)

    private[channel] def bindTransport(): Unit = {
        binding = true
        unresolvedLocalAddress match {
            case Some(address): Option[InetSocketAddress]
                if isOptionSupported(ChannelOption.SO_BROADCAST) && getOption[Boolean](ChannelOption.SO_BROADCAST) &&
                    !address.getAddress.nn.isAnyLocalAddress && !PlatformDependent.isWindows && !PlatformDependent
                        .maybeSuperUser() =>
                logger.logWarn(
                  "A non-root user can't receive a broadcast packet if the socket " +
                      "is not bound to a wildcard address; binding to a non-wildcard " +
                      s"address ($address) anyway as requested."
                )
            case _ =>
        }

        val wasActive = isActive
        Try {
            doBind()
        } match
            case Failure(_) => closeIfClosed()
            case Success(_) =>
                bound = true
                binding = false
                if (!wasActive && isActive)
                    invokeLater(() => if (fireChannelActiveIfNotActiveBefore()) readIfIsAutoRead())

    }

    private[channel] def disconnectTransport(): Unit = {
        val wasActive = isActive
        try {
            doDisconnect()
            // Reset remoteAddress and localAddress
            clearRemoteAddress()
            clearLocalAddress()
            neverActive = true
        } catch { case t: Throwable => closeIfClosed(); return }

        if (wasActive && !isActive) invokeLater(() => pipeline.fireChannelInactive())

        closeIfClosed()
    }

    private[channel] def closeTransport(): Unit = ???

    private def close(cause: Throwable, closeCause: ClosedChannelException): Unit = if (!closeInitiated) {
        closeInitiated = true
        val wasActive = isActive
        ???
    }

    private[channel] def shutdownTransport(direction: ChannelShutdownDirection): Unit = ???

    private[channel] def deregisterTransport(): Unit = ???

    private def deregister(fireChannelInactive: Boolean): Unit = if (registered) invokeLater(() => {
        reactor.deregister(this)
    })

    override private[core] def handleChannelDeregisterReplyEvent(event: ReactorEvent.DeregisterReply): Unit = ???

    private[channel] def readTransport(readBufferAllocator: ReadBufferAllocator): Unit = if (!isActive) {
        throw new IllegalStateException(s"channel $this is not active!")
    } else if (isShutdown(ChannelShutdownDirection.Inbound)) {
        // Input was shutdown so not try to read.
    } else {
        readSink.setReadBufferAllocator(readBufferAllocator)
        try { doRead() }
        catch {
            case e: Exception =>
                invokeLater(() => pipeline.fireChannelExceptionCaught(e))
                closeTransport()
        }
    }

    /** Reading from the underlying transport now until there is nothing more to read or the
     *  [[ReadHandleFactory.ReadHandle]] is telling us to stop.
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
        if (isAllowHalfClosure) shutdownTransport(ChannelShutdownDirection.Inbound)
        else closeTransport()
    } else inputClosedSeenErrorOnRead = true

    private def clearScheduledRead(): Unit = doClearScheduledRead()

    private[channel] def writeTransport(msg: AnyRef): Unit = {
        assertExecutor()
        var size: Int = 0
        try {
            val message = filterOutboundMessage(msg)
//      size =
        } catch {
            case t: Throwable =>
        }

    }

    private[channel] def flushTransport(): Unit = {
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

    private[channel] def connectTransport(): Unit = try {
        if (connected) throw new IllegalStateException(s"$this already connected!")
        val wasActive              = isActive
        var message: Buffer | Null = null
        if (isOptionSupported(ChannelOption.TCP_FASTOPEN_CONNECT) && getOption(ChannelOption.TCP_FASTOPEN_CONNECT)) {
            outboundBuffer.nn.addFlush()
            val current = this.outboundBuffer.nn.current
            current match {
                case buffer: Buffer => message = buffer
                case _              =>
            }
        }
        if (doConnect(message)) {
            fulfillConnect(wasActive)
            if (message != null && message.nn.readableBytes() == 0) this.outboundBuffer.nn.remove
        } else {
            // The reactor will send a [ReactorEvent.ChannelReadiness] event to ChannelsActor, then ChannelsActor will
            // call the channel's handleChannelReadinessEvent method which call finishConnect.

            // register connect timeout trigger. When timeout, the timer will send a timeout event, the
            // handleConnectTimeout method will handle this timeout event.
            if (connectTimeoutMillis > 0)
                connectTimeoutRegisterId = timer.registerTimerTask(TimeoutTrigger.DelayTime(connectTimeoutMillis), this)
        }
    } catch { case t: Throwable => }

    private def handleConnectTimeout(): Unit = closeTransport()

    override private[core] def handleChannelTimeoutEvent(eventRegisterId: Long): Unit =
        if (eventRegisterId == connectTimeoutRegisterId) {
            // handle connect timeout event.
            if (!connected) handleConnectTimeout()
        } else {
            // fire timeout event to pipeline.
            pipeline.fireChannelTimeoutEvent(eventRegisterId)
        }

    /** Should be called once the connect request is ready to be completed and [[isConnectPending]] is `true`. Calling
     *  this method if no [[isConnectPending]] connect is pending will result in an [[AlreadyConnectedException]].
     *
     *  @return
     *    `true` if the connect operation completed, `false` otherwise.
     */
    protected def finishConnect(): Boolean = { ??? } // TODO: cancel connect timeout trigger.

    private def fulfillConnect(wasActive: Boolean): Unit = {
        val active = isActive
        if (!wasActive && active) if (fireChannelActiveIfNotActiveBefore()) readIfIsAutoRead()
    }

    private[channel] def updateWritabilityIfNeeded(notify: Boolean, notifyLater: Boolean): Unit = {
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

    /** Invoked when a new message is added to to the outbound queue of this [[AbstractChannel]], so that the
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

    override def isOptionSupported(option: ChannelOption[_]): Boolean = SUPPORTED_CHANNEL_OPTIONS.contains(option) ||
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
        this.connectTimeoutMillis = checkPositiveOrZero(connectTimeoutMillis, "connectTimeoutMillis")
    }

    @SuppressWarnings(Array("unchecked"))
    private[channel] def getReadHandleFactory[T <: ReadHandleFactory] = readHandleFactory.asInstanceOf[T]

    private def setReadHandleFactory(readHandleFactory: ReadHandleFactory): Unit = {
        this.readHandleFactory = requireNonNull(readHandleFactory, "readHandleFactory").nn
    }

    protected def newReadHandle: ReadHandleFactory.ReadHandle    = readHandleFactory.newHandle(this)
    protected def newWriteHandle: WriteHandleFactory.WriteHandle = writeHandleFactory.newHandle(this)

    @SuppressWarnings(Array("unchecked"))
    private def getWriteHandleFactory[T <: WriteHandleFactory] = writeHandleFactory.asInstanceOf[T]

    private def setWriteHandleFactory(writeHandleFactory: WriteHandleFactory): Unit = {
        this.writeHandleFactory = requireNonNull(writeHandleFactory, "writeHandleFactory").nn
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
        msgSizeEstimator = requireNonNull(estimator, "estimator").nn

    private def isAllowHalfClosure = allowHalfClosure

    private def setAllowHalfClosure(allow: Boolean): Unit = allowHalfClosure = allow

    /** Called once the read loop completed for this Channel. Sub-classes might override this method but should also
     *  call super.
     */
    protected def readLoopComplete(): Unit = {
        // NOOP
    }

}

object AbstractChannel {

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

}
