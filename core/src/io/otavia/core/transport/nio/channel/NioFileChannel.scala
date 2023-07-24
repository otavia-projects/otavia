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

package io.otavia.core.transport.nio.channel

import io.netty5.util.{Attribute, AttributeKey}
import io.otavia.core.actor.ChannelsActor
import io.otavia.core.channel.*
import io.otavia.core.message.ReactorEvent
import io.otavia.core.stack.{ChannelPromise, ChannelReplyFuture}

import java.io.File
import java.net.SocketAddress
import java.nio.channels.FileChannel
import java.nio.file.attribute.FileAttribute
import java.nio.file.{OpenOption, Path, StandardOpenOption}
import scala.jdk.CollectionConverters.*
import scala.language.unsafeNulls

class NioFileChannel() extends AbstractFileChannel {

    setUnsafeChannel(new NioFileUnsafeChannel(this))

    override def unsafeChannel: NioFileUnsafeChannel = super.unsafeChannel.asInstanceOf[NioFileUnsafeChannel]

    override private[core] def openTransport(
        path: Path,
        options: Seq[OpenOption],
        attrs: Seq[FileAttribute[_]],
        promise: ChannelPromise
    ): Unit = {
        var success = false
        try {
            unsafeChannel.doOpen(path, options, attrs)
            success = true
        } catch {
            case e: Throwable =>
                promise.setFailure(e)
        }
        if (success) promise.setSuccess(ReactorEvent.EMPTY_EVENT)
    }

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

}
