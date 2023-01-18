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
import io.otavia.core.channel.{ReadSink, WriteSink}
import io.otavia.core.reactor.{DeregisterReplyEvent, ReactorEvent, RegisterReplyEvent}
import io.otavia.core.util.ActorLogger

import java.net.{InetSocketAddress, SocketAddress}
import java.nio.channels.ClosedChannelException
import java.util.Objects.requireNonNull
import scala.collection.mutable
import scala.concurrent.Future.never

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
      ChannelInternal[L, R] {

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

    override val pipeline: ChannelPipeline = newChannelPipeline()

    private var writable: Boolean                     = true
    private var local: L | Null                       = null
    private var remote: R | Null                      = null
    private val outboundBuffer: ChannelOutboundBuffer = new ChannelOutboundBuffer()
    @volatile private var registered: Boolean         = false

    private var readHandleFactory: ReadHandleFactory   = defaultReadHandleFactory
    private var writeHandleFactory: WriteHandleFactory = defaultWriteHandleFactory

    private var msgSizeEstimator: MessageSizeEstimator = DEFAULT_MSG_SIZE_ESTIMATOR
    private var connectTimeoutMillis: Int              = DEFAULT_CONNECT_TIMEOUT

    @SuppressWarnings(Array("FieldMayBeFinal"))
    private val autoRead  = 1
    private var autoClose = true
    // writeBufferWaterMark
    private var waterMarkLow: Int  = WriteBufferWaterMark.DEFAULT_LOW_WATER_MARK
    private var waterMarkHigh: Int = WriteBufferWaterMark.DEFAULT_HIGH_WATER_MARK
    private var allowHalfClosure   = false

    /** Cache for the string representation of this channel */
    private var strValActive           = false
    private var strVal: Option[String] = None

    // All fields below are only called from within the EventLoop thread.
    private var closeInitiated                        = false
    private var initialCloseCause: Throwable          = _
    private var readBeforeActive: ReadBufferAllocator = _

    private lazy val estimatorHandle: MessageSizeEstimator.Handle = msgSizeEstimator.newHandle
    private var inWriteFlushed                                    = false

    /** true if the channel has never been registered, false otherwise */
    private var neverRegistered = true
    private var neverActive     = true

    private var inputClosedSeenErrorOnRead = false

    private val laterTasks = mutable.ArrayDeque.empty[Runnable]

    private var actor: ChannelsActor[?] = _

    override def executor: ChannelsActor[?] = actor

    final private[core] def setExecutor(channelsActor: ChannelsActor[?]): Unit = {
        actor = channelsActor
        logger = ActorLogger.getLogger(getClass)(using executor)
        channelId = executor.generateChannelId()
    }

    override def id: Int = channelId

    private def readSink: ReadSink   = this
    private def writeSink: WriteSink = this

    /** Returns a new [[ChannelPipeline]] instance. */
    private def newChannelPipeline(): ChannelPipeline = new OtaviaChannelPipeline(this)

    override final def bufferAllocator: BufferAllocator = executor.system.allocator

    private def closeIfClosed(): Unit = if (!isOpen) closeTransport()

    private def invokeLater(task: Runnable): Unit = laterTasks.append(task)

    override def localAddress: Option[SocketAddress] = ???

    override def remoteAddress: Option[SocketAddress] = ???

    override def isRegistered: Boolean = registered

    private def totalPending: Long =
        if (outboundBuffer == null) -1 else outboundBuffer.totalPendingWriteBytes + pipeline.pendingOutboundBytes

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
        val active = isActive
        if (strValActive == active && strVal.nonEmpty) strVal.get
        else {
            if (remoteAddress.nonEmpty) {
                val buf = new mutable.StringBuilder(128)
                    .append("[id: ")
                    .append(id)
                    .append(", L:")
                    .append(if localAddress.nonEmpty then localAddress.get else "None")
                    .append(if active then " - " else " ! ")
                    .append("R:")
                    .append(remoteAddress.get)
                    .append(']')
                strVal = Some(buf.toString())
            } else if (localAddress.nonEmpty) {
                val buf = new mutable.StringBuilder(128)
                    .append("[id: ")
                    .append(id)
                    .append(", L:")
                    .append(localAddress.get)
                    .append(']')
                strVal = Some(buf.toString())
            } else {
                val buf = new mutable.StringBuilder(64)
                    .append("[id: ")
                    .append(id)
                    .append(']')
                strVal = Some(buf.toString())
            }
            strValActive = active
            strVal.get
        }
    }

    protected final def readIfIsAutoRead(): Unit = if (readBeforeActive != null) {
        val readBufferAllocator = readBeforeActive
        readBeforeActive = null
        readTransport(readBufferAllocator)
    } else if (isAutoRead) read()

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
     *  send channel register to reactor, and handle reactor reply at [[onRegisterTransportReply]]
     */
    private[channel] def registerTransport(): Unit = {
        if (isRegistered) throw new IllegalStateException("registered to an event loop already")
        reactor.register(this)
    }

    override private[core] def handleChannelRegisterReplyEvent(event: ReactorEvent.RegisterReply): Unit =
        event.cause match
            case None =>
                val firstRegistration = neverRegistered
                neverRegistered = false
                registered = true
                pipeline.fireChannelRegistered()
                if (isActive) {
                    if (firstRegistration) fireChannelActiveIfNotActiveBefore()
                    readIfIsAutoRead()
                }
            case Some(cause) => closeNowAndFail(cause)

    private[channel] def bindTransport(local: SocketAddress): Unit = {
        local match {
            case address: InetSocketAddress
                if isOptionSupported(ChannelOption.SO_BROADCAST) && getOption[Boolean](ChannelOption.SO_BROADCAST) &&
                    !address.getAddress.isAnyLocalAddress && !PlatformDependent.isWindows && !PlatformDependent
                        .maybeSuperUser() =>
                logger.logWarn(
                  "A non-root user can't receive a broadcast packet if the socket " +
                      "is not bound to a wildcard address; binding to a non-wildcard " +
                      s"address (${local}) anyway as requested."
                )
            case _ =>
        }

        val wasActive = isActive
        try { doBind(local) }
        catch { case t: Throwable => closeIfClosed(); return }

        if (!wasActive && isActive) invokeLater(() => if (fireChannelActiveIfNotActiveBefore()) readIfIsAutoRead())

    }

    private[channel] def disconnectTransport(): Unit = {
        val wasActive = isActive
        try {
            doDisconnect()
            // Reset remoteAddress and localAddress
            remote = null
            local = null
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
        readBeforeActive = readBufferAllocator
    } else if (isShutdown(ChannelShutdownDirection.Inbound)) {} else {
        ???
    }

    /** Reading from the underlying transport now until there is nothing more to read or the
     *  [[ReadHandleFactory.ReadHandle]] is telling us to stop.
     */
    protected final def readNow(): Unit = {

        ???

        readSink.readLoop()
    }

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
        outboundBuffer.addFlush()
        writeFlushed()
    }

    /** Returns true if flushed messages should not be tried to write when calling [[flush()]] . Instead these will be
     *  written once [[writeFlushedNow]] is called, which is typically done once the underlying transport becomes
     *  writable again.
     *
     *  @return
     *    true if write will be done later on by calling [[writeFlushedNow]], false otherwise.
     */
    protected def isWriteFlushedScheduled = false

    /** Writing previous flushed messages if [[isWriteFlushedScheduled]] returns false, otherwise do nothing. */
    protected final def writeFlushed(): Unit = if (!isWriteFlushedScheduled) writeFlushedNow()

    protected final def writeFlushedNow(): Unit = {}

    protected def writeLoopComplete(allWriten: Boolean): Unit = if (!allWriten) invokeLater(() => writeFlushed())

    private[channel] def connectTransport(): Unit = try {
        if (remoteAddress.nonEmpty) throw new IllegalStateException(s"$this already connected!")
        val wasActive       = isActive
        var message: Buffer = null
        if (isOptionSupported(ChannelOption.TCP_FASTOPEN_CONNECT) && getOption(ChannelOption.TCP_FASTOPEN_CONNECT)) {
            val current = this.outboundBuffer.current
            current match {
                case buffer: Buffer => message = buffer
                case _              =>
            }
        }
        if (doConnect(message)) {
            // TODO: fulfillConnectPromise
            if (message != null && message.readableBytes() == 0) this.outboundBuffer.remove
        } else {}
    } catch { case t: Throwable => }

    /** Should be called once the connect request is ready to be completed and [[isConnectPending]] is `true`. Calling
     *  this method if no [[isConnectPending]] connect is pending will result in an [[AlreadyConnectedException]].
     *
     *  @return
     *    `true` if the connect operation completed, `false` otherwise.
     */
    protected def finishConnect(): Boolean = ???

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
            case AUTO_READ               => ???
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

//  private def getBufferAllocator = bufferAllocator

//  private def setBufferAllocator(bufferAllocator: BufferAllocator): Unit = {
//    this.bufferAllocator = requireNonNull(bufferAllocator, "bufferAllocator")
//  }

    @SuppressWarnings(Array("unchecked"))
    private[channel] def getReadHandleFactory[T <: ReadHandleFactory] = readHandleFactory.asInstanceOf[T]

    private def setReadHandleFactory(readHandleFactory: ReadHandleFactory): Unit = {
        this.readHandleFactory = requireNonNull(readHandleFactory, "readHandleFactory")
    }

    protected def readHandle: ReadHandleFactory.ReadHandle    = readHandleFactory.newHandle(this)
    protected def writeHandle: WriteHandleFactory.WriteHandle = writeHandleFactory.newHandle(this)

    @SuppressWarnings(Array("unchecked"))
    private def getWriteHandleFactory[T <: WriteHandleFactory] = writeHandleFactory.asInstanceOf[T]

    private def setWriteHandleFactory(writeHandleFactory: WriteHandleFactory): Unit = {
        this.writeHandleFactory = requireNonNull(writeHandleFactory, "writeHandleFactory")
    }

    private def isAutoRead = autoRead == 1

    private def setAutoRead(autoRead: Boolean): Unit = ???

    private def isAutoClose = autoClose

    private def setAutoClose(autoClose: Boolean): Unit = this.autoClose = autoClose

    private def getMessageSizeEstimator = msgSizeEstimator

    private def setMessageSizeEstimator(estimator: MessageSizeEstimator): Unit =
        msgSizeEstimator = requireNonNull(estimator, "estimator")

    private def isAllowHalfClosure = allowHalfClosure

    private def setAllowHalfClosure(allowHalfClosure: Boolean): Unit =
        this.allowHalfClosure = allowHalfClosure

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
