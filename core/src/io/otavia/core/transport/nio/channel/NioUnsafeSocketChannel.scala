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

import io.otavia.core.channel.ChannelShutdownDirection.{Inbound, Outbound}
import io.otavia.core.channel.message.{ReadPlan, ReadPlanFactory}
import io.otavia.core.channel.{Channel, ChannelShutdownDirection}
import io.otavia.core.message.ReactorEvent
import io.otavia.core.transport.nio.channel.NioUnsafeSocketChannel.NioSocketChannelReadPlan

import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.channels.{SelectableChannel, SelectionKey, SocketChannel}
import scala.language.unsafeNulls

class NioUnsafeSocketChannel(channel: Channel, ch: SocketChannel, readInterestOp: Int)
    extends AbstractNioUnsafeChannel[SocketChannel](channel, ch, readInterestOp) {

    private var inputShutdown  = false
    private var outputShutdown = false

    setReadPlanFactory((channel: Channel) => new NioSocketChannelReadPlan())

    override def localAddress: SocketAddress = javaChannel.getLocalAddress

    override def unsafeBind(local: SocketAddress): Unit = try {
        javaChannel.bind(local)
        executorAddress.inform(ReactorEvent.BindReply(channel))
    } catch {
        case t: Throwable =>
            executorAddress.inform(ReactorEvent.BindReply(channel, cause = Some(t)))
    }

    override def unsafeConnect(remote: SocketAddress, local: Option[SocketAddress], fastOpen: Boolean): Unit = {
        local match
            case None        =>
            case Some(value) => javaChannel.bind(value)

        try {
            val connected = javaChannel.connect(remote)
            if (!connected) {
                _selectionKey.interestOps(SelectionKey.OP_CONNECT)
            } else {
                executorAddress.inform(ReactorEvent.ConnectReply(channel, true))
            }
        } catch {
            case t: Throwable => executorAddress.inform(ReactorEvent.ConnectReply(channel, cause = Some(t)))
        }
    }

    override protected def finishConnect(): Unit = {
        try {
            javaChannel.finishConnect()
            executorAddress.inform(ReactorEvent.ConnectReply(channel, true))
        } catch {
            case t: Throwable => executorAddress.inform(ReactorEvent.ConnectReply(channel, cause = Some(t)))
        }
    }

    override def unsafeDisconnect(): Unit = ???

    override def unsafeShutdown(direction: ChannelShutdownDirection): Unit = direction match
        case Inbound =>
            javaChannel.shutdownInput()
            inputShutdown = true
        case Outbound =>
            javaChannel.shutdownOutput()
            outputShutdown = true

    override def isOpen: Boolean = ch.isOpen

    override def isActive: Boolean = ch.isOpen && ch.isConnected

    override def isShutdown(direction: ChannelShutdownDirection): Boolean = if (!isActive) true
    else {
        direction match
            case Inbound  => inputShutdown
            case Outbound => outputShutdown
    }

    override protected def doReadNow(): Boolean = {
        val page      = directAllocator.allocate()
        val attempted = page.writableBytes
        var read: Int = 0
        try {
            read = page.transferFrom(ch, attempted)
            processRead(attempted, read, 1)
        } catch {
            case t: Throwable =>
                unsafeClose()
                executorAddress.inform(ReactorEvent.ChannelClose(channel, cause = Some(t)))
        }

        if (read > 0) {
            executorAddress.inform(ReactorEvent.ReadBuffer(channel, page, recipient = null))
            false
        } else if (read == 0) {
            page.close()
            channel.directAllocator.recycle(page)
            false
        } else true

    }

}

object NioUnsafeSocketChannel {
    class NioSocketChannelReadPlan extends ReadPlan {

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
