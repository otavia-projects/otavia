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

import io.otavia.core.message.ReactorEvent
import io.otavia.core.stack.{ChannelPromise, Promise}
import io.otavia.core.util.Platform

import java.net.{InetSocketAddress, SocketAddress}
import java.nio.file.attribute.FileAttribute
import java.nio.file.{OpenOption, Path}
import scala.language.unsafeNulls

abstract class AbstractNetworkChannel extends AbstractChannel {

    protected var ongoingChannelPromise: ChannelPromise = _

    override private[core] def registerTransport(promise: ChannelPromise): Unit =
        if (registering) promise.setFailure(new IllegalStateException(s"The channel $this is registering to reactor!"))
        else if (isRegistered) promise.setFailure(new IllegalStateException("registered to reactor already"))
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
                if (isActive) {
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
                closeIfClosed()
    }

    override private[core] def connectTransport(
        remote: SocketAddress,
        local: Option[SocketAddress],
        promise: ChannelPromise
    ): Unit = ???

    override private[core] def openTransport(
        path: Path,
        options: Seq[OpenOption],
        attrs: Seq[FileAttribute[?]],
        promise: ChannelPromise
    ): Unit = promise.setFailure(new UnsupportedOperationException())

    override private[core] def disconnectTransport(promise: ChannelPromise): Unit = ???

    override private[core] def closeTransport(promise: ChannelPromise): Unit = ???

    override private[core] def shutdownTransport(direction: ChannelShutdownDirection, promise: ChannelPromise): Unit =
        ???

    override private[core] def deregisterTransport(promise: ChannelPromise): Unit = ???

    override private[core] def writeTransport(msg: AnyRef): Unit = ???

    override private[core] def flushTransport(): Unit = ???

    protected final def readIfIsAutoRead(): Unit = if (autoRead) read()

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
