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

import io.otavia.core.channel.AbstractNetChannel.{DEFAULT_CONNECT_TIMEOUT, SUPPORTED_CHANNEL_OPTIONS}
import io.otavia.core.message.ReactorEvent
import io.otavia.core.stack.{ChannelPromise, Promise}
import io.otavia.core.system.ActorSystem
import io.otavia.core.timer.{TimeoutTrigger, Timer}
import io.otavia.core.util.Platform

import java.net.{InetSocketAddress, SocketAddress}
import java.nio.channels.{AlreadyConnectedException, ClosedChannelException, ConnectionPendingException}
import java.nio.file.attribute.FileAttribute
import java.nio.file.{OpenOption, Path}
import scala.language.unsafeNulls

abstract class AbstractNetworkChannel(system: ActorSystem) extends AbstractChannel(system) {

    private var connectTimeoutMillis: Int      = DEFAULT_CONNECT_TIMEOUT
    private var connectTimeoutRegisterId: Long = Timer.INVALID_TIMEOUT_REGISTER_ID

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

    override private[core] def registerTransport(promise: ChannelPromise): Unit =
        if (registering) promise.setFailure(new IllegalStateException(s"The channel $this is registering to reactor!"))
        else if (registered) promise.setFailure(new IllegalStateException("registered to reactor already"))
        else {
            registering = true
            ongoingChannelPromise = promise
            reactor.register(this)
        }

    override private[core] def handleChannelRegisterReplyEvent(event: ReactorEvent.RegisterReply): Unit = {
        val promise = ongoingChannelPromise
        ongoingChannelPromise = null
        event.cause match
            case None =>
                val firstRegistration = neverRegistered
                neverRegistered = false
                registering = false
                registered = true
                pipeline.fireChannelRegistered()
                promise.setSuccess(event)
                // Only fire a channelActive if the channel has never been registered. This prevents firing
                // multiple channel actives if the channel is deregistered and re-registered.
                if (event.active) {
                    if (firstRegistration) fireChannelActiveIfNotActiveBefore()
                    readIfIsAutoRead()
                }

            case Some(cause) =>
                closeNowAndFail(promise, cause)
    }

    override private[core] def bindTransport(local: SocketAddress, channelPromise: ChannelPromise): Unit = {
        if (!mounted) channelPromise.setFailure(new IllegalStateException(s"channel $this is not mounted to actor!"))
        else if (!registered) { // if not register
            if (registering) channelPromise.setFailure(new IllegalStateException("A registering operation is running"))
            else {
                pipeline.register(newPromise())
                ongoingChannelPromise.onCompleted {
                    case self if self.isSuccess =>
                        this.ongoingChannelPromise = null
                        bindTransport0(local, channelPromise)
                    case self if self.isFailed =>
                        this.ongoingChannelPromise = null
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

        ongoingChannelPromise = promise

        setUnresolvedLocalAddress(local)
        reactor.bind(this, local)
    }

    override private[core] def handleChannelBindReplyEvent(event: ReactorEvent.BindReply): Unit = {
        val promise = ongoingChannelPromise
        ongoingChannelPromise = null
        event.cause match
            case None =>
                bound = true
                binding = false
                if (event.firstActive) // first active
                    if (fireChannelActiveIfNotActiveBefore()) readIfIsAutoRead()
                promise.setSuccess(ReactorEvent.EMPTY_EVENT)
            case Some(cause) =>
                promise.setFailure(cause)
                closeTransport(newPromise())
    }

    override private[core] def connectTransport(
        remote: SocketAddress,
        local: Option[SocketAddress],
        promise: ChannelPromise
    ): Unit = {
        if (!mounted) promise.setFailure(new IllegalStateException(s"channel $this is not mounted to actor!"))
        else if (connected) promise.setFailure(new AlreadyConnectedException())
        else if (connecting) promise.setFailure(new ConnectionPendingException())
        else if (closed || closing) promise.setFailure(new ClosedChannelException())
        else if (registering) promise.setFailure(new IllegalStateException("A registering operation is running"))
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

        reactor.connect(this, remote, local, fastOpen)

        // setup connect timeout
        // register connect timeout trigger. When timeout, the timer will send a timeout event, the
        // handleConnectTimeout method will handle this timeout event.
        if (connectTimeoutMillis > 0) {
            val tid = timer.registerChannelTimeout(TimeoutTrigger.DelayTime(connectTimeoutMillis), this)
            connectTimeoutRegisterId = tid
            promise.setTimeoutId(tid)
        }
    }

    override private[core] def disconnectTransport(promise: ChannelPromise): Unit = ???

    override private[core] def handleChannelConnectReplyEvent(event: ReactorEvent.ConnectReply): Unit = {
        val promise = ongoingChannelPromise
        this.ongoingChannelPromise = null
        connecting = false
        event.cause match
            case None =>
                connected = true
                if (promise.canTimeout) timer.cancelTimerTask(promise.timeoutId) // cancel timeout trigger
                promise.setSuccess(event)
                if (event.firstActive) if (fireChannelActiveIfNotActiveBefore()) readIfIsAutoRead()
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

    override private[core] def handleChannelTimeoutEvent(eventRegisterId: Long): Unit = {
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
    ): Unit = promise.setFailure(new UnsupportedOperationException())

    override private[core] def closeTransport(promise: ChannelPromise): Unit = ???

    override private[core] def shutdownTransport(direction: ChannelShutdownDirection, promise: ChannelPromise): Unit =
        ???

    override private[core] def deregisterTransport(promise: ChannelPromise): Unit = ???

    override private[core] def writeTransport(msg: AnyRef): Unit = ???

    override private[core] def flushTransport(): Unit = ???

    protected final def readIfIsAutoRead(): Unit = if (autoRead) pipeline.read()

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
            reactor.close(this)
        } catch {
            case e: Exception => logger.warn("Failed to close a channel.", e)
        }
        closed = true
        closing = false
        promise.setFailure(cause)
    }

    private def closeIfClosed(): Unit = if (!isOpen) closeTransport(newPromise())

}
