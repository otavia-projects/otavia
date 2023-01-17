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

import io.netty5.buffer.Buffer
import io.netty5.util.internal.SocketUtils
import io.otavia.core.actor.ChannelsActor
import io.otavia.core.channel.*
import io.otavia.core.channel.ChannelShutdownDirection.{Inbound, Outbound}
import io.otavia.core.channel.socket.DatagramChannel
import io.otavia.core.channel.socket.SocketProtocolFamily.*

import java.net.{InetAddress, NetworkInterface, ProtocolFamily, SocketAddress}
import java.nio.channels.{SelectableChannel, SelectionKey, DatagramChannel as JDatagramChannel}
import scala.util.Try

/** An NIO [[DatagramChannel]] that sends and receives an [[AddressedEnvelope]].
 *
 *  @see
 *    [[AddressedEnvelope]]
 *  @see
 *    [[DatagramPacket]]
 *
 *  <h3>Available options</h3>
 *
 *  In addition to the options provided by [[DatagramChannel]], [[NioDatagramChannel]] allows the following options in
 *  the option map:
 *
 *  <table border="1" cellspacing="0" cellpadding="6"> <tr> <th>[[ChannelOption]]</th> <th>[[INET]]</th>
 *  <th>[[INET6]]</th> <th>[[UNIX]] </th> </tr><tr> <td>[[NioChannelOption]] </td><td>X</td><td>X</td><td>X</td> </tr>
 *  </table>
 */
class NioDatagramChannel(executor: ChannelsActor[_], socket: JDatagramChannel, protocolFamily: ProtocolFamily)
    extends AbstractNioMessageChannel[SocketAddress, SocketAddress](
      executor,
      true,
      new FixedReadHandleFactory(2048),
      new MaxMessagesWriteHandleFactory(Int.MaxValue),
      socket,
      SelectionKey.OP_READ
    ),
      DatagramChannel {

    private val family = NioChannelUtil.toJdkFamily(protocolFamily)

    @volatile private var inputShutdown  = false
    @volatile private var outputShutdown = false

    @volatile private var activeOnOpen = false
    @volatile private var bound        = false

    override protected def doShutdown(direction: ChannelShutdownDirection): Unit = direction match
        case Inbound  => inputShutdown = true
        case Outbound => outputShutdown = true

    override def isShutdown(direction: ChannelShutdownDirection): Boolean = if (!isActive) true
    else
        direction match
            case Inbound  => inputShutdown
            case Outbound => outputShutdown

    override def isActive: Boolean = {
        val sock = javaChannel
        sock.isOpen && (getOption(ChannelOption.DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION) && isRegistered || bound)
    }

    override def isConnected: Boolean = javaChannel.isConnected

    override protected def javaChannel: JDatagramChannel = super.javaChannel.asInstanceOf[JDatagramChannel]

    override protected def localAddress0: Option[SocketAddress] = Try {
        val address = javaChannel.getLocalAddress
        if (NioChannelUtil.isDomainSocket(family)) NioChannelUtil.toDomainSocketAddress(address) else address
    }.toOption

    override protected def remoteAddress0: Option[SocketAddress] = Try {
        val address = javaChannel.getRemoteAddress
        if (NioChannelUtil.isDomainSocket(family)) NioChannelUtil.toDomainSocketAddress(address) else address
    }.toOption

    override protected def doBind(local: SocketAddress): Unit = {
        if (NioChannelUtil.isDomainSocket(family))
            SocketUtils.bind(javaChannel, NioChannelUtil.toUnixDomainSocketAddress(local))
        else
            SocketUtils.bind(javaChannel, local)
        bound = true
    }

    override protected def doConnect(
        remote: SocketAddress,
        local: Option[SocketAddress],
        initialData: Buffer
    ): Boolean = {
        if (local.nonEmpty) doBind(local.get)

        ???
    }

    override protected def doFinishConnect(requestedRemoteAddress: SocketAddress) = true

    override protected def doDisconnect(): Unit = javaChannel.disconnect()

    override protected def doReadMessages(readSink: ReadSink): Int = ???

    override protected def doWriteNow(writeSink: WriteSink): Unit = ???

    override def joinGroup(multicast: InetAddress): Unit = ???

    override def joinGroup(multicast: InetAddress, interface: NetworkInterface, source: Option[InetAddress]): Unit = ???

    override def leaveGroup(multicast: InetAddress): Unit = ???

    override def leaveGroup(multicast: InetAddress, interface: NetworkInterface, source: Option[InetAddress]): Unit = ???

    override def block(multicast: InetAddress, interface: NetworkInterface, source: InetAddress): Unit = ???

    override def block(multicast: InetAddress, source: InetAddress): Unit = ???
}
