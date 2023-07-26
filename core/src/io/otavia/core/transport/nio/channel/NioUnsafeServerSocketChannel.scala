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

import io.otavia.core.channel.{Channel, ChannelShutdownDirection}
import io.otavia.core.message.ReactorEvent

import java.net.SocketAddress
import java.nio.channels.{SelectableChannel, ServerSocketChannel}
import java.nio.file.attribute.FileAttribute
import java.nio.file.{OpenOption, Path}

class NioUnsafeServerSocketChannel(channel: Channel, ch: SelectableChannel, readInterestOp: Int)
    extends AbstractNioUnsafeChannel(channel, ch, readInterestOp) {

    private var backlog: Int = -1 // NetUtil.SOMAXCONN

    private var bound: Boolean = false

    private def getBacklog(): Int = backlog

    private[nio] def setBacklog(back: Int): Unit = {
        assert(back >= 0, s"in setBacklog(back: Int) back:$back (expected: >= 0)")
        this.backlog = back
    }

    override protected def javaChannel: ServerSocketChannel = super.javaChannel.asInstanceOf[ServerSocketChannel]

    override protected def doReadNow(): Boolean = ???

    override def unsafeBind(local: SocketAddress): Unit = {
        val bindWasActive = isActive
        javaChannel.bind(local, getBacklog())
        bound = true
        val firstActive = !bindWasActive && channel.unsafeChannel.isActive
        channel.executorAddress.inform(ReactorEvent.BindReply(channel, firstActive, None))
    }

    override def unsafeOpen(path: Path, options: Seq[OpenOption], attrs: Seq[FileAttribute[?]]): Unit =
        throw new UnsupportedOperationException()

    override def unsafeDisconnect(): Unit = ???

    override def unsafeClose(): Unit = ???

    override def unsafeConnect(remote: SocketAddress, local: Option[SocketAddress], fastOpen: Boolean): Unit = ???

    override def unsafeShutdown(direction: ChannelShutdownDirection): Unit = ???

    override def isOpen: Boolean = ch.isOpen

    override def isActive: Boolean = isOpen && bound

}
