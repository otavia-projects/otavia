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

package io.otavia.core.channel.nio

import io.netty5.util.NetUtil
import io.otavia.core.actor.ChannelsActor
import io.otavia.core.channel.socket.SocketProtocolFamily
import io.otavia.core.channel.{ServerChannelReadHandleFactory, ServerChannelWriteHandleFactory}

import java.net.{ProtocolFamily, SocketAddress}
import java.nio.channels.{SelectionKey, ServerSocketChannel}

/** A implementation which uses NIO selector based implementation to accept new connections.
 *
 *  <h3>Available options</h3>
 *
 *  In addition to the options provided by [[NioSocketChannel]] allows the following options in the option map:
 *
 *  <table border="1" cellspacing="0" cellpadding="6"> <tr> <th>[[ChannelOption]]</th>
 *  <th>[[SocketProtocolFamily.INET]]</th> <th>[[SocketProtocolFamily.INET6]]</th>
 *  <th>[[SocketProtocolFamily.UNIX]]</th> </tr><tr> <td>[[NioChannelOption]]</td> <td>X</td><td>X</td><td>X</td> </tr>
 *  </table>
 */
class NioServerSocketChannel(socket: ServerSocketChannel, protocolFamily: ProtocolFamily)
    extends AbstractNioMessageChannel[SocketAddress, SocketAddress](
      false,
      new ServerChannelReadHandleFactory(),
      new ServerChannelWriteHandleFactory(),
      socket,
      SelectionKey.OP_ACCEPT
    ) {

    private val family = NioChannelUtil.toJdkFamily(protocolFamily)

    @volatile private var backlog = NetUtil.SOMAXCONN
    @volatile private var bound   = false

    override def isActive: Boolean = isOpen && bound

}
