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
import io.netty5.util.NetUtil
import io.netty5.util.internal.SocketUtils
import io.otavia.core.actor.ChannelsActor
import io.otavia.core.channel.*
import io.otavia.core.channel.estimator.{ServerChannelReadHandleFactory, ServerChannelWriteHandleFactory}
import io.otavia.core.channel.internal.{ReadSink, WriteSink}
import io.otavia.core.channel.socket.SocketProtocolFamily

import java.net.{ProtocolFamily, SocketAddress}
import java.nio.channels.{SelectableChannel, SelectionKey, ServerSocketChannel}
import scala.language.unsafeNulls
import scala.util.Try

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
//    @volatile private var bound   = false

    private var unresolvedLocal: SocketAddress | Null = null

    override def setUnresolvedLocalAddress(address: SocketAddress): Unit = unresolvedLocal = address

    override protected def unresolvedLocalAddress: Option[SocketAddress] = Option(unresolvedLocal)

    override protected def clearLocalAddress(): Unit = unresolvedLocal = null

    override def isActive: Boolean = isOpen && bound

    override protected def getExtendedOption[T](option: ChannelOption[T]): T = {
        if (option == ChannelOption.SO_BACKLOG) getBacklog().asInstanceOf[T]
        val socketOption = NioChannelOption.toSocketOption(option)
        if (socketOption != null)
            NioChannelOption.getOption(javaChannel, socketOption).get
        else
            super.getExtendedOption(option)
    }

    override protected def setExtendedOption[T](option: ChannelOption[T], value: T): Unit = {
        if (option == ChannelOption.SO_BACKLOG) setBacklog(value.asInstanceOf[Int])
        else {
            val socketOption = NioChannelOption.toSocketOption(option)
            if (socketOption != null)
                NioChannelOption.setOption(javaChannel, socketOption, value)
            else
                super.setExtendedOption(option, value)
        }
    }

    override protected def isExtendedOptionSupported(option: ChannelOption[?]): Boolean = {
        if (option == ChannelOption.SO_BACKLOG) true
        else {
            val socketOption = NioChannelOption.toSocketOption(option)
            if (socketOption != null)
                NioChannelOption.isOptionSupported(javaChannel, socketOption)
            else
                super.isExtendedOptionSupported(option)
        }
    }

    private def getBacklog(): Int = backlog

    private def setBacklog(back: Int): Unit = {
        assert(back >= 0, s"in setBacklog(back: Int) back:$back (expected: >= 0)")
        this.backlog = back
    }

    override protected def javaChannel: ServerSocketChannel = super.javaChannel.asInstanceOf[ServerSocketChannel]

    override protected def localAddress0: Option[SocketAddress] = Try {
        var address = javaChannel.getLocalAddress
        if (NioChannelUtil.isDomainSocket(family)) address = NioChannelUtil.toDomainSocketAddress(address)
        address
    }.toOption

    override protected def remoteAddress0: Option[SocketAddress] = None

    override protected def doShutdown(direction: ChannelShutdownDirection): Unit =
        throw new UnsupportedOperationException()

    override def isShutdown(direction: ChannelShutdownDirection): Boolean = !isActive

    @throws[Exception]
    override protected def doBind(): Unit = {
        if (NioChannelUtil.isDomainSocket(family))
            unresolvedLocal = NioChannelUtil.toUnixDomainSocketAddress(unresolvedLocal.nn)

        javaChannel.bind(unresolvedLocal.nn, getBacklog())
        bound = true
    }

    override protected def doReadMessages(readSink: ReadSink): Int = {
        val client = SocketUtils.accept(javaChannel)
        Option(client) match
            case Some(ch) =>
                try {
                    readSink.processRead(0, 0, new NioSocketChannel(ch, family))
                    1
                } catch {
                    case t: Throwable =>
                        logger.logWarn("Failed to create a new channel from an accepted socket.", t)
                        try { ch.close() }
                        catch { case t2: Throwable => logger.logWarn("Failed to close a socket.", t2) }
                        readSink.processRead(0, 0, null)
                        0
                }
            case None =>
                readSink.processRead(0, 0, null)
                0
    }

    override protected def doConnect(remote: SocketAddress, local: Option[SocketAddress], fastOpen: Boolean): Boolean =
        throw new UnsupportedOperationException()

    override protected def doFinishConnect(requestedRemoteAddress: SocketAddress): Boolean =
        throw new UnsupportedOperationException()

    override protected def doDisconnect(): Unit =
        throw new UnsupportedOperationException()

    override protected def doWriteNow(writeSink: WriteSink): Unit =
        throw new UnsupportedOperationException()

    override protected def filterOutboundMessage(msg: AnyRef): AnyRef =
        throw new UnsupportedOperationException()

}
