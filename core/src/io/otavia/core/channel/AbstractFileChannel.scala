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

import io.netty5.util.{Attribute, AttributeKey}
import io.otavia.core.actor.ChannelsActor
import io.otavia.core.channel.message.ReadPlan
import io.otavia.core.message.ReactorEvent
import io.otavia.core.stack.{ChannelPromise, ChannelReplyFuture}

import java.net.SocketAddress
import java.nio.file.attribute.FileAttribute
import java.nio.file.{OpenOption, Path}

/** Abstract channel for file, support aio. */
abstract class AbstractFileChannel extends AbstractChannel {

    override private[core] def bindTransport(local: SocketAddress, channelPromise: ChannelPromise): Unit =
        channelPromise.setFailure(new UnsupportedOperationException())

    override private[core] def connectTransport(
        remote: SocketAddress,
        local: Option[SocketAddress],
        promise: ChannelPromise
    ): Unit = promise.setFailure(new UnsupportedOperationException())

    override private[core] def openTransport(
        path: Path,
        options: Seq[OpenOption],
        attrs: Seq[FileAttribute[?]],
        promise: ChannelPromise
    ): Unit = {
        ???
    }

    override private[core] def disconnectTransport(promise: ChannelPromise): Unit =
        promise.setFailure(new UnsupportedOperationException())

    override private[core] def closeTransport(promise: ChannelPromise): Unit = {
        ???
    }

    override private[core] def shutdownTransport(
        direction: ChannelShutdownDirection,
        promise: ChannelPromise
    ): Unit = promise.setFailure(new UnsupportedOperationException())

    override private[core] def registerTransport(promise: ChannelPromise): Unit = {
        promise.setSuccess(ReactorEvent.EMPTY_EVENT)
    }

    override private[core] def deregisterTransport(promise: ChannelPromise): Unit = {
        promise.setSuccess(ReactorEvent.EMPTY_EVENT)
    }

    override private[core] def readTransport(readPlan: ReadPlan): Unit = {
        ???
    }

    override private[core] def writeTransport(msg: AnyRef): Unit = {
        ???
    }

    override private[core] def flushTransport(): Unit = ???

}
