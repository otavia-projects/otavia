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

import io.netty5.util.NetUtil
import io.otavia.core.channel.{AbstractUnsafeChannel, Channel, ChannelShutdownDirection}

import java.net.{ProtocolFamily, SocketAddress, StandardProtocolFamily}
import java.nio.channels.{SelectionKey, ServerSocketChannel}
import scala.language.unsafeNulls

class NioServerSocketUnsafeChannel(channel: Channel, ch: ServerSocketChannel, val family: ProtocolFamily)
    extends AbstractNioUnsafeChannel(channel, ch, SelectionKey.OP_ACCEPT) {

    private var bound: Boolean = false

    @volatile private var backlog = NetUtil.SOMAXCONN

    private def getBacklog(): Int = backlog

    private def setBacklog(back: Int): Unit = {
        assert(back >= 0, s"in setBacklog(back: Int) back:$back (expected: >= 0)")
        this.backlog = back
    }

    override protected def doReadNow(): Boolean = ???

    override def unsafeBind(local: SocketAddress): Unit = {
        ch.bind(local, getBacklog())
        bound = true // TODO: Thread Safe
    }

    override protected def unsafeDisconnect(): Unit = ???

    override protected def unsafeClose(): Unit = ???

    override protected def unsafeShutdown(direction: ChannelShutdownDirection): Unit = ???

    override def isOpen: Boolean = ch.isOpen

    override def isActive: Boolean = isOpen && bound

}
