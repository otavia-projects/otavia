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

import io.otavia.core.channel.estimator.ServerChannelWriteHandleFactory
import io.otavia.core.channel.internal.{ReadSink, WriteSink}
import io.otavia.core.channel.{AbstractNetChannel, ChannelShutdownDirection}
import io.otavia.core.message.ReactorEvent

import java.net.SocketAddress

class ServerSocketChannel
    extends AbstractNetChannel[SocketAddress, SocketAddress](
      false,
      new ServerChannelWriteHandleFactory()
    ) {

    override protected def doDisconnect(): Unit = ???

    override protected def doClose(): Unit = ???

    override protected def doShutdown(direction: ChannelShutdownDirection): Unit = ???

    override protected def doRead(): Unit = ???

    override protected def doWriteNow(writeSink: WriteSink): Unit = ???

    override protected def doConnect(remote: SocketAddress, local: Option[SocketAddress], fastOpen: Boolean): Boolean =
        ???

    override protected def doFinishConnect(requestedRemoteAddress: SocketAddress): Boolean = ???

    override protected def localAddress0: Option[SocketAddress] = ???

    override protected def remoteAddress0: Option[SocketAddress] = ???

    override def isOpen: Boolean = ???

    override def isActive: Boolean = ???

    override def isShutdown(direction: ChannelShutdownDirection): Boolean = ???

    override protected[core] def doReadNow(readSink: ReadSink): Boolean = ???

}
