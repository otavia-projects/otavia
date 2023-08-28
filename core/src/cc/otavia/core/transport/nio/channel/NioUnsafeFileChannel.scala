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

package cc.otavia.core.transport.nio.channel

import cc.otavia.buffer.pool.RecyclablePageBuffer
import cc.otavia.core.channel.estimator.ReadBufferAllocator
import cc.otavia.core.channel.message.{FileReadPlan, MaxMessagesReadPlanFactory, ReadPlan}
import cc.otavia.core.channel.{AbstractUnsafeChannel, Channel, ChannelShutdownDirection, FileRegion}
import cc.otavia.core.message.ReactorEvent

import java.net.SocketAddress
import java.nio.channels.FileChannel
import java.nio.file.attribute.FileAttribute
import java.nio.file.{OpenOption, Path}
import scala.jdk.CollectionConverters.*
import scala.language.unsafeNulls

class NioUnsafeFileChannel(channel: Channel) extends AbstractUnsafeChannel(channel) {

    private var ch: FileChannel = _

    setReadPlanFactory((channel: Channel) => FileReadPlan())

    private def javaChannel: FileChannel = ch

    override def unsafeBind(local: SocketAddress): Unit =
        executorAddress.inform(ReactorEvent.BindReply(channel, cause = Some(new UnsupportedOperationException())))

    override def unsafeOpen(path: Path, options: Seq[OpenOption], attrs: Seq[FileAttribute[?]]): Unit = {
        try {
            ch = FileChannel.open(path, options.toSet.asJava, attrs: _*)
            executorAddress.inform(ReactorEvent.OpenReply(channel))
        } catch {
            case t: Throwable =>
                executorAddress.inform(ReactorEvent.OpenReply(channel, Some(t)))
        }
    }

    override def unsafeDisconnect(): Unit =
        executorAddress.inform(ReactorEvent.DisconnectReply(channel, cause = Some(new UnsupportedOperationException())))

    override def unsafeClose(): Unit = {
        ch.close()
    }

    override def unsafeShutdown(direction: ChannelShutdownDirection): Unit =
        executorAddress.inform(ReactorEvent.ShutdownReply(channel, cause = Some(new UnsupportedOperationException())))

    override def unsafeRead(readPlan: ReadPlan): Unit = {
        readPlan match
            case FileReadPlan(length, position) =>
                if (length > 0) {
                    try {
                        if (position != -1) {
                            channel.channelInboundAdaptiveBuffer.transferFrom(ch, position, length)
                        } else {
                            channel.channelInboundAdaptiveBuffer.transferFrom(ch, length)
                        }
                        executorAddress.inform(ReactorEvent.ReadEvent(channel))
                    } catch {
                        case t: Throwable =>
                            executorAddress.inform(ReactorEvent.ReadEvent(channel, cause = Some(t)))
                    }

                } else {
                    val cause = new IndexOutOfBoundsException(s"read length is $length")
                    executorAddress.inform(ReactorEvent.ReadCompletedEvent(channel, cause = Some(cause)))
                }
            case _ =>
                val cause = new IllegalArgumentException(
                  s"${getClass.getSimpleName} not support ${readPlan.getClass.getSimpleName} read plan"
                )
                executorAddress.inform(ReactorEvent.ReadCompletedEvent(channel, cause = Some(cause)))
    }

    override def unsafeFlush(payload: FileRegion | RecyclablePageBuffer): Unit = ???

    override def unsafeConnect(remote: SocketAddress, local: Option[SocketAddress], fastOpen: Boolean): Unit =
        executorAddress.inform(ReactorEvent.ConnectReply(channel, cause = Some(new UnsupportedOperationException())))

    override def isOpen: Boolean = ch != null && ch.isOpen

    override def isActive: Boolean = isOpen

    override def isShutdown(direction: ChannelShutdownDirection): Boolean = ???

}
