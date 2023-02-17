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
import io.otavia.core.channel.ChannelShutdownDirection.{Inbound, Outbound}
import io.otavia.core.channel.socket.SocketChannelWriteHandleFactory
import io.otavia.core.channel.{ChannelOption, ChannelShutdownDirection, FileRegion}

import java.net.{ProtocolFamily, SocketAddress}
import java.nio.channels.{SelectableChannel, SelectionKey, SocketChannel}
import scala.util.Try

/** <h3>Available options</h3>
 *
 *  In addition to the options provided by [[NioSocketChannel]] allows the following options in the option map:
 *
 *  <table border="1" cellspacing="0" cellpadding="6"> <tr> <th>[[ChannelOption]]</th> <th>INET</th> <th>INET6</th>
 *  <th>UNIX </th> </tr><tr> <td> [[NioChannelOption]]</td><td>X</td><td>X</td><td>X</td> </tr> </table>
 *  @param executor
 *    the [[ChannelsActor]] which will be used.
 *  @param socket
 *    the [[SocketChannel]] which will be used
 *  @param protocolFamily
 *    the [[ProtocolFamily]] that was used to create th [[SocketChannel]]
 */
class NioSocketChannel(socket: SocketChannel, protocolFamily: ProtocolFamily)
    extends AbstractNioByteChannel[SocketAddress, SocketAddress](
      defaultWriteHandleFactory = new SocketChannelWriteHandleFactory(Int.MaxValue),
      ch = socket
    ) {

    private val family: ProtocolFamily = NioChannelUtil.toJdkFamily(protocolFamily)

    private var inputShutdown: Boolean  = false
    private var outputShutdown: Boolean = false

    private var unresolvedRemote: SocketAddress | Null = null
    private var unresolvedLocal: SocketAddress | Null  = null

    override def setUnresolvedRemoteAddress(address: SocketAddress): Unit = unresolvedRemote = address

    override protected def unresolvedRemoteAddress: Option[SocketAddress] = Option(unresolvedRemote)

    override def setUnresolvedLocalAddress(address: SocketAddress): Unit = unresolvedLocal = address

    override protected def unresolvedLocalAddress: Option[SocketAddress] = Option(unresolvedLocal)

    override protected def clearRemoteAddress(): Unit = unresolvedRemote = null

    override protected def clearLocalAddress(): Unit = unresolvedLocal = null

    override final protected def javaChannel: SocketChannel = super.javaChannel.asInstanceOf[SocketChannel]

    override def isActive: Boolean = javaChannel.isOpen && javaChannel.isConnected

    override def isShutdown(direction: ChannelShutdownDirection): Boolean = if (!isActive) true
    else {
        direction match
            case Inbound  => inputShutdown
            case Outbound => outputShutdown
    }

    override protected def doShutdown(direction: ChannelShutdownDirection): Unit = direction match
        case Inbound =>
            javaChannel.shutdownInput()
            inputShutdown = true
        case Outbound =>
            javaChannel.shutdownOutput()
            outputShutdown = true

    override protected def localAddress0: Option[SocketAddress] = Try {
        val address = javaChannel.getLocalAddress
        if (NioChannelUtil.isDomainSocket(family)) NioChannelUtil.toDomainSocketAddress(address) else address
    }.toOption

    override protected def remoteAddress0: Option[SocketAddress] = Try {
        val address = javaChannel.getRemoteAddress
        if (NioChannelUtil.isDomainSocket(family)) NioChannelUtil.toDomainSocketAddress(address) else address
    }.toOption

    override protected def doBind(): Unit = if (NioChannelUtil.isDomainSocket(family)) {
        SocketUtils.bind(javaChannel, NioChannelUtil.toUnixDomainSocketAddress(unresolvedLocal.nn))
    } else SocketUtils.bind(javaChannel, unresolvedLocal.nn)

    override protected def doConnect(initialData: Buffer | Null): Boolean = {
        unresolvedLocalAddress match
            case Some(_) => doBind()
            case None    =>

        var success   = false
        var connected = false
        try {
            if (NioChannelUtil.isDomainSocket(family))
                unresolvedRemote = NioChannelUtil.toUnixDomainSocketAddress(unresolvedRemote.nn)

            connected = SocketUtils.connect(javaChannel, unresolvedRemote.nn)

            if (!connected) selectionKey.interestOps(SelectionKey.OP_CONNECT)
            success = true
        } finally if (!success) doClose()
        connected
    }

    override protected def doFinishConnect(requestedRemoteAddress: SocketAddress): Boolean = {
        javaChannel.finishConnect()
    }

    override protected def doDisconnect(): Unit = doClose()

    override protected def doReadBytes(buf: Buffer): Int = buf.transferFrom(javaChannel, buf.writableBytes())

    override protected def doWriteBytes(buf: Buffer): Int = buf.transferTo(javaChannel, buf.readableBytes())

    override protected def doWriteFileRegion(region: FileRegion): Long = region.transferTo(javaChannel, region.position)

}
