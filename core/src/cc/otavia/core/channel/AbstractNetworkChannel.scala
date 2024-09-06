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

import cc.otavia.core.channel.ChannelOption.*
import cc.otavia.core.channel.ChannelShutdownDirection.{Inbound, Outbound}
import cc.otavia.core.channel.internal.WriteBufferWaterMark
import cc.otavia.core.channel.message.ReadPlanFactory
import cc.otavia.core.message.*
import cc.otavia.core.stack.{ChannelPromise, Promise}
import cc.otavia.core.system.ActorSystem
import cc.otavia.core.timer.{TimeoutTrigger, Timer}
import cc.otavia.internal.Platform

import java.net.{InetSocketAddress, SocketAddress}
import java.nio.channels.{AlreadyConnectedException, ClosedChannelException, ConnectionPendingException, NotYetConnectedException}
import java.nio.file.attribute.FileAttribute
import java.nio.file.{OpenOption, Path}
import scala.language.unsafeNulls

abstract class AbstractNetworkChannel(system: ActorSystem) extends AbstractChannel(system) {

    import AbstractNetworkChannel.*

    private var connectTimeoutMillis: Int      = DEFAULT_CONNECT_TIMEOUT
    private var connectTimeoutRegisterId: Long = Timer.INVALID_TIMEOUT_REGISTER_ID

    // writeBufferWaterMark
    private var waterMarkLow: Int  = WriteBufferWaterMark.DEFAULT_LOW_WATER_MARK
    private var waterMarkHigh: Int = WriteBufferWaterMark.DEFAULT_HIGH_WATER_MARK

    override protected def getExtendedOption[T](option: ChannelOption[T]): T = option match
        case AUTO_READ               => autoRead
        case WRITE_BUFFER_WATER_MARK => new WriteBufferWaterMark(waterMarkLow, waterMarkHigh)
        case CONNECT_TIMEOUT_MILLIS  => connectTimeoutMillis
        case READ_PLAN_FACTORY       => unsafeChannel.readPlanFactory
        case AUTO_CLOSE              => autoClose
        case ALLOW_HALF_CLOSURE      => allowHalfClosure
        case _                       => getTransportExtendedOption(option)

    /** Override to add support for more [[ChannelOption]]s for networks [[Channel]]. You need to also call super after
     *  handling the extra options.
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
    protected def getTransportExtendedOption[T](option: ChannelOption[T]): T =
        throw new UnsupportedOperationException(s"ChannelOption not supported: $option")

    override final protected def setExtendedOption[T](option: ChannelOption[T], value: T): Unit = {
        option match
            case AUTO_READ               => setAutoRead(value)
            case WRITE_BUFFER_WATER_MARK => setWriteBufferWaterMark(value)
            case CONNECT_TIMEOUT_MILLIS  => setConnectTimeoutMillis(value)
            case READ_PLAN_FACTORY       => setReadPlanFactory(value)
            case AUTO_CLOSE              => setAutoClose(value)
            case ALLOW_HALF_CLOSURE      => allowHalfClosure = value
            case _                       => setTransportExtendedOption(option, value)
    }

    /** Override to add support for more [[ChannelOption]]s for networks [[Channel]]. You need to also call super after
     *  handling the extra options.
     *
     *  @param option
     *    the [[ChannelOption]].
     *  @tparam T
     *    the value type.
     *  @throws UnsupportedOperationException
     *    if the [[ChannelOption]] is not supported.
     */
    protected def setTransportExtendedOption[T](option: ChannelOption[T], value: T): Unit =
        throw new UnsupportedOperationException("ChannelOption not supported: " + option)

    override protected def isExtendedOptionSupported(option: ChannelOption[?]): Boolean =
        SUPPORTED_CHANNEL_OPTIONS.contains(option) || isTransportExtendedOptionSupported(option)

    /** Override to add support for more [[ChannelOption]]s. You need to also call super after handling the extra
     *  options.
     *
     *  @param option
     *    the [[ChannelOption]].
     *  @return
     *    true if supported, false otherwise.
     */
    protected def isTransportExtendedOptionSupported(option: ChannelOption[?]) = false

    private def setAutoRead(auto: Boolean): Unit = {
        if (!registered) autoRead = auto
        else {
            if (auto && !autoRead) {
                autoRead = true
                pipeline.read()
            } else if (!auto && autoRead) {
                autoRead = false
                unsafeChannel.setAutoRead(false)
            }
        }
    }

    private def setWriteBufferWaterMark(mark: WriteBufferWaterMark): Unit = {
        waterMarkLow = mark.low
        waterMarkHigh = mark.high
    }

    private def setReadPlanFactory(factory: ReadPlanFactory): Unit = unsafeChannel.setReadPlanFactory(factory)

    private def setConnectTimeoutMillis(connectTimeoutMillis: Int): Unit = {
        assert(connectTimeoutMillis >= 0, s"connectTimeoutMillis $connectTimeoutMillis (expected: >= 0)")
        this.connectTimeoutMillis = connectTimeoutMillis
    }

    private def setAutoClose(auto: Boolean): Unit = {
        autoClose = auto
        unsafeChannel.setAutoClose(auto)
    }

    private def setAllowHalfClosure(allow: Boolean): Unit = unsafeChannel.setAllowHalfClosure(allow)

    override private[core] def registerTransport(promise: ChannelPromise): Unit =
        if (registering)
            invokeLater(() =>
                promise.setFailure(new IllegalStateException(s"The channel $this is registering to reactor!"))
            )
        else if (registered)
            invokeLater(() => promise.setFailure(new IllegalStateException("registered to reactor already")))
        else {
            registering = true
            ongoingChannelPromise = promise
            mountedThread.ioHandler.register(this)
            // reactor.register(this)
        }

    override private[otavia] def handleChannelRegisterReply(active: Boolean, cause: Option[Throwable]): Unit = {
        val promise = ongoingChannelPromise
        ongoingChannelPromise = null
        cause match
            case None =>
                val firstRegistration = neverRegistered
                neverRegistered = false
                registering = false
                registered = true
                pipeline.fireChannelRegistered()
                promise.setSuccess(EMPTY_EVENT)
                // Only fire a channelActive if the channel has never been registered. This prevents firing
                // multiple channel actives if the channel is deregistered and re-registered.
                if (active) {
                    if (firstRegistration) fireChannelActiveIfNotActiveBefore()
                    readIfIsAutoRead()
                }

            case Some(cause) =>
                closeNowAndFail(promise, cause)
    }

    override private[core] def bindTransport(local: SocketAddress, promise: ChannelPromise): Unit = {
        if (!mounted)
            invokeLater(() => promise.setFailure(new IllegalStateException(s"channel $this is not mounted to actor!")))
        else if (closed || closing) invokeLater(() => promise.setFailure(new ClosedChannelException()))
        else if (!registered) { // if not register
            if (registering)
                invokeLater(() =>
                    promise.setFailure(new IllegalStateException("A register operation is already running"))
                )
            else {
                pipeline.register(newPromise())
                ongoingChannelPromise.onCompleted {
                    case self if self.isSuccess =>
                        this.ongoingChannelPromise = null
                        bindTransport0(local, promise)
                    case self if self.isFailed =>
                        this.ongoingChannelPromise = null
                        promise.setFailure(self.causeUnsafe)
                }
            }
        } else bindTransport0(local, promise)
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

        ongoingChannelPromise = promise

        setUnresolvedLocalAddress(local)
        mountedThread.ioHandler.bind(this, local)
        // reactor.bind(this, local)
    }

    override final private[otavia] def handleChannelBindReply(firstActive: Boolean, cause: Option[Throwable]): Unit = {
        val promise = ongoingChannelPromise
        ongoingChannelPromise = null
        cause match
            case None =>
                bound = true
                binding = false
                if (firstActive) // first active
                    if (fireChannelActiveIfNotActiveBefore()) readIfIsAutoRead()
                promise.setSuccess(EMPTY_EVENT)
            case Some(cause) =>
                promise.setFailure(cause)
                closeTransport(newPromise())
    }

    override private[core] def connectTransport(
        remote: SocketAddress,
        local: Option[SocketAddress],
        promise: ChannelPromise
    ): Unit = {
        if (!mounted)
            invokeLater(() => promise.setFailure(new IllegalStateException(s"channel $this is not mounted to actor!")))
        else if (connected) invokeLater(() => promise.setFailure(new AlreadyConnectedException()))
        else if (connecting) invokeLater(() => promise.setFailure(new ConnectionPendingException()))
        else if (closed || closing) invokeLater(() => promise.setFailure(new ClosedChannelException()))
        else if (registering)
            invokeLater(() => promise.setFailure(new IllegalStateException("A registering operation is running")))
        else if (!registered) { // if not register
            if (!registering) pipeline.register(newPromise())
            ongoingChannelPromise.onCompleted {
                case self if self.isSuccess =>
                    this.ongoingChannelPromise = null
                    connectTransport0(remote, local, promise)
                case self if self.isFailed =>
                    this.ongoingChannelPromise = null
                    promise.setFailure(self.causeUnsafe)
            }
        } else connectTransport0(remote, local, promise)
    }

    private def connectTransport0(
        remote: SocketAddress,
        local: Option[SocketAddress],
        promise: ChannelPromise
    ): Unit = {
        connecting = true
        ongoingChannelPromise = promise
        val fastOption = ChannelOption.TCP_FASTOPEN_CONNECT
        val fastOpen   = if (isOptionSupported(fastOption) && getOption(fastOption)) true else false

        mountedThread.ioHandler.connect(this, remote, local, fastOpen)
        // reactor.connect(this, remote, local, fastOpen)

        // setup connect timeout
        // register connect timeout trigger. When timeout, the timer will send a timeout event, the
        // handleConnectTimeout method will handle this timeout event.
        if (connectTimeoutMillis > 0 && !unsafeChannel.isConnected) {
            val tid = timer.registerChannelTimeout(TimeoutTrigger.DelayTime(connectTimeoutMillis), this)
            connectTimeoutRegisterId = tid
            promise.setTimeoutId(tid)
        }
    }

    override final private[otavia] def handleChannelConnectReply(firstActive: Boolean, cause: Option[Throwable]): Unit =
        if (ongoingChannelPromise != null) {
            val promise = ongoingChannelPromise
            this.ongoingChannelPromise = null
            connecting = false
            cause match
                case None =>
                    connected = true
                    if (promise.canTimeout) timer.cancelTimerTask(promise.timeoutId) // cancel timeout trigger
                    if (firstActive) if (fireChannelActiveIfNotActiveBefore()) readIfIsAutoRead()
                    promise.setSuccess(EMPTY_EVENT)
                case Some(cause) =>
                    promise.setFailure(cause)
                    closeTransport(newPromise())
        }

    private def handleConnectTimeout(): Unit = {
        val promise = ongoingChannelPromise
        this.ongoingChannelPromise = null
        closeTransport(newPromise()) // close the channel and ignore close result.

        promise.setFailure(new ConnectTimeoutException(s"connection timed out: $this"))
    }

    override final private[core] def handleChannelTimeoutEvent(eventRegisterId: Long): Unit = {
        if (eventRegisterId == connectTimeoutRegisterId) {
            // handle connect timeout event.
            if (connecting) handleConnectTimeout() // else ignore the event
        } else {
            // fire other timeout event to pipeline.
            pipeline.fireChannelTimeoutEvent(eventRegisterId)
        }
    }

    override private[core] def openTransport(
        path: Path,
        options: Seq[OpenOption],
        attrs: Seq[FileAttribute[?]],
        promise: ChannelPromise
    ): Unit = invokeLater(() => promise.setFailure(new UnsupportedOperationException()))

    override private[core] def closeTransport(promise: ChannelPromise): Unit = {
        if (connecting || registering || connected) {
            val ongoing = ongoingChannelPromise

            if (ongoing != null) ongoing.setFailure(new ClosedChannelException())

            connecting = false
            registering = false
            closing = true
            ongoingChannelPromise = promise
            mountedThread.ioHandler.close(this)
            // reactor.close(this)
        } else if (closed) { promise.setSuccess(EMPTY_EVENT) }
        else if (closing) { promise.setFailure(new IllegalStateException("A close operation is running")) }
        else promise.setFailure(new NotYetConnectedException())
    }

    override private[core] def handleChannelClose(cause: Option[Throwable]): Unit = {
        closing = false
        closed = true

        if (ongoingChannelPromise != null) {
            ongoingChannelPromise.setSuccess(EMPTY_EVENT)
            ongoingChannelPromise = null
        }

        val cause = new ClosedChannelException()

        this.failedFutures(cause)
        this.failedStacks(cause)
        this.closeAdaptiveBuffers()

        pipeline.fireChannelInactive()
        if (registered) pipeline.fireChannelUnregistered()

        while (!pipeline.isEmpty) {
            pipeline.removeLast()
        }

    }

    override private[core] def shutdownTransport(direction: ChannelShutdownDirection, promise: ChannelPromise): Unit = {
        if (!isActive) {
            if (isOpen) promise.setFailure(new NotYetConnectedException())
            else promise.setFailure(new ClosedChannelException())
        } else if (isShutdown(direction)) {
            promise.setSuccess(EMPTY_EVENT)
        } else {
            mountedThread.ioHandler.shutdown(this, direction)
            // reactor.shutdown(this, direction)
            promise.setSuccess(EMPTY_EVENT)
        }
    }

    final private[core] def handleShutdownReply(event: ShutdownReply): Unit = {
        event.direction match
            case Inbound  => pipeline.fireChannelShutdown(event.direction)
            case Outbound => shutdownOutput(new ClosedChannelException())
    }

    private def shutdownOutput(cause: Throwable): Unit = {
        if (this.outboundQueue != null) {
            this.outboundQueue = null
            this.failedFutures(cause)
            pipeline.fireChannelShutdown(ChannelShutdownDirection.Outbound)
        }
    }

    override private[core] def deregisterTransport(promise: ChannelPromise): Unit =
        if (!registered) promise.setSuccess(EMPTY_EVENT)
        else {
            // TODO: add deregistering state
            mountedThread.ioHandler.deregister(this)
            // reactor.deregister(this)
            this.ongoingChannelPromise = promise
        }

    override final private[otavia] def handleChannelDeregisterReply(
        firstInactive: Boolean,
        isOpen: Boolean,
        cause: Option[Throwable]
    ): Unit = {
        cause match
            case Some(value) =>
                logger.warn("Unexpected exception occurred while deregistering a channel.", value)
            case None =>

        if (firstInactive) pipeline.fireChannelInactive()

        // TODO: add deregistering state
        if (registered) {
            registered = false
            pipeline.fireChannelUnregistered()

            if (!isOpen) {
                // Remove all handlers from the ChannelPipeline. This is needed to ensure
                // handlerRemoved(...) is called and so resources are released.
                while (!pipeline.isEmpty) {
                    try {
                        pipeline.removeLast()
                    } catch {
                        case t: Throwable =>
                    }
                }
            }
        }

        if (this.ongoingChannelPromise != null) {
            val promise = this.ongoingChannelPromise
            this.ongoingChannelPromise = null
            promise.setSuccess(EMPTY_EVENT)
        }

    }

    private final def readIfIsAutoRead(): Unit = if (autoRead) pipeline.read()

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
            mountedThread.ioHandler.close(this)
            // reactor.close(this)
        } catch {
            case e: Exception => logger.warn("Failed to close a channel.", e)
        }
        closed = true
        closing = false
        promise.setFailure(cause)
    }

    private def closeIfClosed(): Unit = if (!isOpen) closeTransport(newPromise())

    private def totalPending: Long = -1

    override final def writableBytes: Long = {
        val totalPending = this.totalPending
        if (totalPending == -1) 0
        else {
            val bytes = ???
        }
        ???
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

    override def toString: String = s"${getClass.getSimpleName}(id=${id}, state=${getStateString})"

}

object AbstractNetworkChannel {

    final val DEFAULT_CONNECT_TIMEOUT = 30000

    private val SUPPORTED_CHANNEL_OPTIONS: Set[ChannelOption[?]] = Set(
      AUTO_READ,
      WRITE_BUFFER_WATER_MARK,
      CONNECT_TIMEOUT_MILLIS,
      READ_PLAN_FACTORY,
      AUTO_CLOSE,
      ALLOW_HALF_CLOSURE
    )

}
