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
import cc.otavia.core.channel.ChannelShutdownDirection.{Inbound, Outbound}
import cc.otavia.core.channel.message.{ReadPlan, ReadPlanFactory}
import cc.otavia.core.channel.{AbstractChannel, Channel, ChannelShutdownDirection, FileRegion}
import cc.otavia.core.message.*
import cc.otavia.core.transport.nio.channel.NioUnsafeDatagramChannel.NioDatagramChannelReadPlan

import java.io.IOException
import java.net.SocketAddress
import java.nio.channels.{DatagramChannel, SelectableChannel}
import scala.language.unsafeNulls

class NioUnsafeDatagramChannel(channel: AbstractChannel, ch: DatagramChannel, readInterestOp: Int)
    extends AbstractNioUnsafeChannel[DatagramChannel](channel, ch, readInterestOp) {

    setReadPlanFactory((channel: Channel) => new NioDatagramChannelReadPlan())

    override def localAddress: SocketAddress = try {
        javaChannel.getLocalAddress
    } catch {
        case e: IOException => null
    }

    override def unsafeBind(local: SocketAddress): Unit = {
        javaChannel.bind(local)
        bound = true
    }

    override def unsafeConnect(remote: SocketAddress, local: Option[SocketAddress], fastOpen: Boolean): Unit = {
        local match
            case None        =>
            case Some(value) => unsafeBind(value)

        try {
            javaChannel.connect(remote)
            // When connected we are also bound
            bound = true
            connected = true
            executorAddress.inform(ConnectReply(channel, true))
        } catch {
            case t: Throwable =>
                executorAddress.inform(ConnectReply(channel, cause = Some(t)))
        }
    }

    override def unsafeDisconnect(): Unit = {
        ch.disconnect()
        channel.executorAddress.inform(DisconnectReply(channel))
    }

    override def unsafeShutdown(direction: ChannelShutdownDirection): Unit = {
        direction match
            case ChannelShutdownDirection.Inbound  => shutdownedInbound = true
            case ChannelShutdownDirection.Outbound => shutdownedOutbound = true

        executorAddress.inform(ShutdownReply(channel, direction))
    }

    override def unsafeFlush(payload: FileRegion | RecyclablePageBuffer): Unit = {
        payload match
            case fileRegion: FileRegion =>
                ???
            case buffer: RecyclablePageBuffer => // TODO: UDP packet
                var cursor = buffer
                while (cursor != null) {
                    val buf = cursor
                    cursor = cursor.next
                    buf.next = null
                    val byteBuffer = buf.byteBuffer
                    byteBuffer.limit(buffer.writerOffset)
                    byteBuffer.position(buffer.readerOffset)
                    ch.write(byteBuffer)
                    buf.close()
                }

    }

    override def isOpen: Boolean = ch.isOpen

    override def isActive: Boolean = isOpen && bound

    override def isShutdown(direction: ChannelShutdownDirection): Boolean = if (!isActive) true
    else {
        this.synchronized {
            direction match
                case Inbound  => shutdownedInbound
                case Outbound => shutdownedOutbound
        }
    }

    override protected def doReadNow(): Boolean = {
        val page                   = directAllocator.allocate()
        val attempted              = page.writableBytes
        var read: Int              = 0
        var address: SocketAddress = null
        try {
            val byteBuffer = page.byteBuffer
            address = ch.receive(page.byteBuffer)
            read = byteBuffer.position()
            if (address != null) processRead(attempted, read, 1)
            else processRead(attempted, read, 0)
        } catch {
            case t: Throwable => unsafeClose(Some(t))
        }

        if (read > 0) {
            executorAddress.inform(
              ReadBuffer(channel, page, sender = Some(address), recipient = localAddress)
            )
            false
        } else if (read == 0) {
            page.close()
            channel.directAllocator.recycle(page)
            false
        } else true
    }

}

object NioUnsafeDatagramChannel {
    class NioDatagramChannelReadPlan extends ReadPlan {

        private var continue: Boolean = true

        override def estimatedNextSize: Int = 0

        override def lastRead(attemptedBytesRead: Int, actualBytesRead: Int, numMessagesRead: Int): Boolean = {
            continue = attemptedBytesRead == actualBytesRead && actualBytesRead > 0
            continue
        }

        override def readComplete(): Unit = {
            continue = true
        }

        override def continueReading: Boolean = continue

    }
}
