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
import cc.otavia.core.channel.message.{FileReadPlan, MaxMessagesReadPlanFactory, ReadPlan}
import cc.otavia.core.channel.{AbstractUnsafeChannel, Channel, ChannelShutdownDirection, FileRegion}
import cc.otavia.core.message.*

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
        executorAddress.inform(BindReply(channel, cause = Some(new UnsupportedOperationException())))

    override def unsafeOpen(path: Path, options: Seq[OpenOption], attrs: Seq[FileAttribute[?]]): Unit = {
        try {
            ch = FileChannel.open(path, options.toSet.asJava, attrs: _*)
            executorAddress.inform(OpenReply(channel))
        } catch {
            case t: Throwable =>
                executorAddress.inform(OpenReply(channel, Some(t)))
        }
    }

    override def unsafeDisconnect(): Unit =
        executorAddress.inform(DisconnectReply(channel, cause = Some(new UnsupportedOperationException())))

    override def unsafeClose(cause: Option[Throwable]): Unit = {
        try {
            ch.close()
            ch = null
            executorAddress.inform(ChannelClose(channel, cause))
        } catch {
            case t: Throwable =>
                executorAddress.inform(ChannelClose(channel, Some(t)))
        }
    }

    override def unsafeShutdown(direction: ChannelShutdownDirection): Unit =
        throw new IllegalStateException("Never should be call!")

    override def unsafeRead(readPlan: ReadPlan): Unit = {
        readPlan match
            case FileReadPlan(length, position) =>
                if (length > 0) {
                    try {
                        if (position != -1) {
                            var remaining = length
                            var pos       = position
                            var continue  = true
                            while (continue) {
                                val buffer     = directAllocator.allocate()
                                val read       = Math.min(buffer.writableBytes, remaining)
                                val byteBuffer = buffer.byteBuffer
                                byteBuffer.limit(read)
                                val realRead = ch.read(byteBuffer, pos)
                                if (realRead > 0) {
                                    pos += realRead
                                    remaining -= realRead
                                    byteBuffer.clear()
                                    buffer.writerOffset(realRead)
                                    executorAddress.inform(ReadBuffer(channel, buffer, recipient = null))
                                    if (remaining == 0) {
                                        continue = false
                                        executorAddress.inform(ReadCompletedEvent(channel))
                                    }
                                } else if (realRead == 0) {
                                    continue = false
                                } else {
                                    continue = false
                                    val cause = new IndexOutOfBoundsException(
                                      s"require read length [${length}] is large than file remaining [${length - remaining}], actual read [${length - remaining}]"
                                    )
                                    executorAddress.inform(ReadEvent(channel, cause = Some(cause)))
                                }
                            }
                        } else {
                            var remaining = length
                            var continue  = true
                            while (continue) {
                                val buffer     = directAllocator.allocate()
                                val byteBuffer = buffer.byteBuffer
                                byteBuffer.limit(Math.min(buffer.writableBytes, remaining))
                                val realRead = ch.read(byteBuffer)
                                if (realRead > 0) {
                                    remaining -= realRead
                                    byteBuffer.clear()
                                    buffer.writerOffset(realRead)
                                    executorAddress.inform(ReadBuffer(channel, buffer, recipient = null))
                                    if (remaining == 0) {
                                        continue = false
                                        executorAddress.inform(ReadCompletedEvent(channel))
                                    }
                                } else if (realRead == 0) {
                                    continue = false
                                } else {
                                    continue = false
                                    val cause = new IndexOutOfBoundsException(
                                      s"require read length [${length}] is large than file remaining [${length - remaining}], actual read [${length - remaining}]"
                                    )
                                    executorAddress.inform(
                                      ReadCompletedEvent(channel, cause = Some(cause))
                                    )
                                }
                            }
                        }
                    } catch {
                        case t: Throwable =>
                            executorAddress.inform(ReadEvent(channel, cause = Some(t)))
                    }
                } else if (length == -1) {
                    try {
                        if (position != -1) {
                            val readLength = ch.size()
                            var remaining  = readLength
                            var pos        = position
                            var continue   = true
                            while (continue) {
                                val buffer     = directAllocator.allocate()
                                val byteBuffer = buffer.byteBuffer
                                byteBuffer.limit(Math.min(buffer.writableBytes.toLong, remaining).toInt)
                                val realRead = ch.read(byteBuffer, pos)
                                if (realRead > 0) {
                                    pos += realRead
                                    remaining -= realRead
                                    byteBuffer.clear()
                                    buffer.writerOffset(realRead)
                                    executorAddress.inform(ReadBuffer(channel, buffer, recipient = null))
                                    if (remaining == 0) {
                                        continue = false
                                        executorAddress.inform(ReadCompletedEvent(channel))
                                    }
                                } else if (realRead == 0) {
                                    continue = false
                                }
                            }
                        } else {
                            val readLength = ch.size()
                            var remaining  = readLength
                            var continue   = true
                            while (continue) {
                                val buffer     = directAllocator.allocate()
                                val byteBuffer = buffer.byteBuffer
                                byteBuffer.limit(Math.min(buffer.writableBytes.toLong, remaining).toInt)
                                val realRead = ch.read(byteBuffer)
                                if (realRead > 0) {
                                    remaining -= realRead
                                    byteBuffer.clear()
                                    buffer.writerOffset(realRead)
                                    executorAddress.inform(ReadBuffer(channel, buffer, recipient = null))
                                    if (remaining == 0) {
                                        continue = false
                                        executorAddress.inform(ReadCompletedEvent(channel))
                                    }
                                } else if (realRead == 0) {
                                    continue = false
                                }
                            }
                        }
                    } catch {
                        case t: Throwable =>
                            executorAddress.inform(ReadEvent(channel, cause = Some(t)))
                    }
                } else {
                    val cause = new UnsupportedOperationException(s"read length is $length")
                    executorAddress.inform(ReadCompletedEvent(channel, cause = Some(cause)))
                }
            case _ =>
                val cause = new IllegalArgumentException(
                  s"${getClass.getSimpleName} not support ${readPlan.getClass.getSimpleName} read plan"
                )
                executorAddress.inform(ReadCompletedEvent(channel, cause = Some(cause)))
    }

    override def unsafeFlush(payload: FileRegion | RecyclablePageBuffer): Unit = ???

    override def unsafeConnect(remote: SocketAddress, local: Option[SocketAddress], fastOpen: Boolean): Unit =
        executorAddress.inform(ConnectReply(channel, cause = Some(new UnsupportedOperationException())))

    override def isOpen: Boolean = ch != null && ch.isOpen

    override def isActive: Boolean = isOpen

    override def isShutdown(direction: ChannelShutdownDirection): Boolean = !isOpen

}
