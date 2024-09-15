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

import cc.otavia.core.channel.message.ReadPlan
import cc.otavia.core.message.*
import cc.otavia.core.stack.ChannelPromise
import cc.otavia.core.system.ActorSystem

import java.net.SocketAddress
import java.nio.channels.ClosedChannelException
import java.nio.file.attribute.FileAttribute
import java.nio.file.{OpenOption, Path}
import scala.language.unsafeNulls

/** Abstract channel for file, support aio. */
abstract class AbstractFileChannel(system: ActorSystem) extends AbstractChannel(system) {

    private var path: Path = _

    override final def localAddress: Option[SocketAddress] = None

    override final def remoteAddress: Option[SocketAddress] = None

    override final def isShutdown(direction: ChannelShutdownDirection): Boolean = !isOpen

    override private[core] def bindTransport(local: SocketAddress, channelPromise: ChannelPromise): Unit =
        channelPromise.setFailure(new UnsupportedOperationException())

    override private[core] def connectTransport(
        remote: SocketAddress,
        local: Option[SocketAddress],
        promise: ChannelPromise
    ): Unit = promise.setFailure(new UnsupportedOperationException())

    override private[core] def disconnectTransport(promise: ChannelPromise): Unit =
        promise.setFailure(new UnsupportedOperationException())

    override private[core] def shutdownTransport(
        direction: ChannelShutdownDirection,
        promise: ChannelPromise
    ): Unit = promise.setFailure(new UnsupportedOperationException())

    override private[core] def registerTransport(promise: ChannelPromise): Unit = {
        registered = true
        promise.setSuccess(EMPTY_EVENT)
    }

    override private[otavia] def handleChannelRegisterReply(active: Boolean, cause: Option[Throwable]): Unit = {}

    override private[core] def deregisterTransport(promise: ChannelPromise): Unit = {
        promise.setSuccess(EMPTY_EVENT)
    }

    override private[core] def openTransport(
        path: Path,
        options: Seq[OpenOption],
        attrs: Seq[FileAttribute[?]],
        promise: ChannelPromise
    ): Unit = {
        if (!mounted)
            invokeLater(() => promise.setFailure(new IllegalStateException(s"channel $this is not mounted to actor!")))
        else if (opening) invokeLater(() => promise.setFailure(new IllegalStateException("Channel is opening!")))
        else if (closed || closing) invokeLater(() => promise.setFailure(new ClosedChannelException()))
        else if (opened) invokeLater(() => promise.setFailure(new IllegalStateException("Open already!")))
        else {
            opening = true
            this.ongoingChannelPromise = promise
            this.path = path
            // mountedThread.ioHandler.open(this, path, options, attrs)
            reactor.open(this, path, options, attrs) // file io use aio
        }
    }

    override private[core] def readTransport(readPlan: ReadPlan): Unit = reactor.read(this, readPlan)

    override final private[core] def handleChannelOpenReply(cause: Option[Throwable]): Unit = {
        val promise = ongoingChannelPromise
        ongoingChannelPromise = null
        cause match
            case None =>
                opening = false
                opened = true
                pipeline.fireChannelActive()
                promise.setSuccess(EMPTY_EVENT)
            case Some(cause) =>
                promise.setFailure(cause)
                closeTransport(newPromise())
    }

    override private[core] def closeTransport(promise: ChannelPromise): Unit = {
        if (!opened) promise.setFailure(new IllegalStateException("File not opened!"))
        else if (closing || closed) promise.setSuccess(EMPTY_EVENT)
        else {
            closing = true
            this.ongoingChannelPromise = promise
            // mountedThread.ioHandler.close(this)
            reactor.close(this)
        }
    }

    override private[core] def handleChannelClose(cause: Option[Throwable]): Unit = {
        val promise = ongoingChannelPromise
        ongoingChannelPromise = null
        cause match
            case None =>
                closed = true
                closing = false
                pipeline.fireChannelInactive()
                promise.setSuccess(EMPTY_EVENT)
            case Some(cause) =>
                promise.setFailure(cause)
    }

    override def toString: String = s"FileChannel(path=${path.toAbsolutePath}, state=${getStateString})"

}
