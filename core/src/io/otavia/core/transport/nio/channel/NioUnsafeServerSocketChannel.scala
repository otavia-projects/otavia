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

import io.otavia.core.channel.message.ReadPlan
import io.otavia.core.channel.{Channel, ChannelShutdownDirection}
import io.otavia.core.message.ReactorEvent
import io.otavia.core.transport.nio.channel.NioUnsafeServerSocketChannel.{BACKLOG, NioServerSocketReadPlan}
import io.otavia.core.util.SystemPropertyUtil

import java.net.SocketAddress
import java.nio.channels.{SelectableChannel, SelectionKey, ServerSocketChannel}
import java.nio.file.attribute.FileAttribute
import java.nio.file.{OpenOption, Path}

class NioUnsafeServerSocketChannel(channel: Channel, ch: ServerSocketChannel, readInterestOp: Int)
    extends AbstractNioUnsafeChannel[ServerSocketChannel](channel, ch, readInterestOp) {

    private var backlog: Int = BACKLOG

    private var bound: Boolean = false

    setReadPlanFactory((channel: Channel) => new NioServerSocketReadPlan())

    private def getBacklog(): Int = backlog

    private[nio] def setBacklog(back: Int): Unit = {
        assert(back >= 0, s"in setBacklog(back: Int) back:$back (expected: >= 0)")
        this.backlog = back
    }

    override def unsafeBind(local: SocketAddress): Unit = {
        val bindWasActive = isActive
        javaChannel.bind(local, getBacklog())
        bound = true
        val firstActive = !bindWasActive && channel.unsafeChannel.isActive
        channel.executorAddress.inform(ReactorEvent.BindReply(channel, firstActive, None))
    }

    override def unsafeOpen(path: Path, options: Seq[OpenOption], attrs: Seq[FileAttribute[?]]): Unit =
        throw new UnsupportedOperationException()

    override def unsafeDisconnect(): Unit = throw new UnsupportedOperationException()

    override def unsafeConnect(remote: SocketAddress, local: Option[SocketAddress], fastOpen: Boolean): Unit =
        throw new UnsupportedOperationException()

    override def unsafeShutdown(direction: ChannelShutdownDirection): Unit =
        throw new UnsupportedOperationException()

    override def isOpen: Boolean = ch.isOpen

    override def isActive: Boolean = isOpen && bound

    override def isShutdown(direction: ChannelShutdownDirection): Boolean = !isActive

    override protected def doReadNow(): Boolean = {
        val socket = javaChannel.accept()

        socket match
            case null =>
                processRead(0, 0, 0)
            case _ =>
                socket.configureBlocking(false)
                val c = new NioSocketChannel(channel.system)
                val u = new NioUnsafeSocketChannel(c, socket, SelectionKey.OP_READ)
                c.setUnsafeChannel(u)
                executorAddress.inform(ReactorEvent.AcceptedEvent(channel, c))
                processRead(0, 0, 1)

        false
    }

}

object NioUnsafeServerSocketChannel {

    private val DEFAULT_BACKLOG: Int = 200
    private val BACKLOG: Int = SystemPropertyUtil.getInt("io.otavia.core.transport.nio.backlog", DEFAULT_BACKLOG)

    class NioServerSocketReadPlan extends ReadPlan {

        private var continue: Boolean  = true
        private var readCompleted: Int = 0

        override def estimatedNextSize: Int = 0

        override def lastRead(attemptedBytesRead: Int, actualBytesRead: Int, numMessagesRead: Int): Boolean = {
            readCompleted += actualBytesRead
            continue = numMessagesRead != 0
            continue
        }

        override def readComplete(): Unit = {
            continue = true
            readCompleted = 0
        }

        override def continueReading: Boolean = continue

    }

}
