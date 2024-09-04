/*
 * Copyright 2022 Yan Kun <yan_kun_1992@foxmail.com>
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

package cc.otavia.core.transport.nio

import cc.otavia.core.channel.socket.SocketProtocolFamily
import cc.otavia.core.channel.{Channel, ChannelException}
import cc.otavia.core.reactor.{IoHandler, Reactor}
import cc.otavia.core.slf4a.Logger
import cc.otavia.core.system.ActorSystem
import cc.otavia.core.transport.TransportFactory
import cc.otavia.core.transport.nio.channel.*
import cc.otavia.core.transport.reactor.nio.{NioHandler, NioReactor}
import sun.nio.ch.Net

import java.io.IOException
import java.net.{ProtocolFamily, StandardProtocolFamily}
import java.nio.channels.*
import java.nio.channels.spi.SelectorProvider
import java.util.Random
import scala.language.unsafeNulls

class NIOTransportFactory(val system: ActorSystem) extends TransportFactory {

    private val selectorProvider: SelectorProvider = SelectorProvider.provider()
    private var reactor: Reactor                   = _

    private val logger: Logger = Logger.getLogger(this.getClass, system)

    private def initialJdkChannel(ch: SelectableChannel): Unit = try {
        ch.configureBlocking(false)
    } catch {
        case e: IOException =>
            try {
                ch.close()
            } catch {
                case e2: IOException => logger.warn("Failed to close a partially initialized socket.", e2)
            }

            throw new ChannelException("Failed to enter non-blocking mode.", e)
    }

    override def createServerSocketChannel(): Channel = {
        var family: ProtocolFamily  = null
        var ch: ServerSocketChannel = null

        try {
            family = StandardProtocolFamily.INET6
            ch = selectorProvider.openServerSocketChannel(family)
        } catch {
            case unsupportedOperationException: UnsupportedOperationException =>
                family = StandardProtocolFamily.INET
                ch = selectorProvider.openServerSocketChannel(family)
        }

        initialJdkChannel(ch)

        createServerChannel0(ch)
    }

    override def createServerSocketChannel(family: ProtocolFamily): Channel = {
        val ch = selectorProvider.openServerSocketChannel(family)
        initialJdkChannel(ch)
        createServerChannel0(ch)
    }

    private def createServerChannel0(ch: ServerSocketChannel): Channel = {
        val channel = new NioServerSocketChannel(system)
        val unsafe  = new NioUnsafeServerSocketChannel(channel, ch, readInterestOp = SelectionKey.OP_ACCEPT)
        channel.setUnsafeChannel(unsafe)

        channel
    }

    override def createSocketChannel(): Channel = {
        var family: ProtocolFamily = null
        var ch: SocketChannel      = null

        try {
            family = StandardProtocolFamily.INET6
            ch = selectorProvider.openSocketChannel(family)
        } catch {
            case unsupportedOperationException: UnsupportedOperationException =>
                family = StandardProtocolFamily.INET
                ch = selectorProvider.openSocketChannel(family)
        }
        initialJdkChannel(ch)
        createSocketChannel0(ch)
    }

    override def createSocketChannel(family: ProtocolFamily): Channel = {
        val ch = selectorProvider.openSocketChannel(family)
        initialJdkChannel(ch)
        createSocketChannel0(ch)
    }

    private def createSocketChannel0(ch: SocketChannel): Channel = {
        val channel = new NioSocketChannel(system)
        val unsafe  = new NioUnsafeSocketChannel(channel, ch, SelectionKey.OP_READ)
        channel.setUnsafeChannel(unsafe)
        channel
    }

    override def createDatagramChannel(): Channel = {
        var family: ProtocolFamily = null
        var ch: DatagramChannel    = null

        try {
            family = StandardProtocolFamily.INET6
            ch = selectorProvider.openDatagramChannel(family)
        } catch {
            case unsupportedOperationException: UnsupportedOperationException =>
                family = StandardProtocolFamily.INET
                ch = selectorProvider.openDatagramChannel(family)
        }
        initialJdkChannel(ch)
        createDatagramChannel0(ch)
    }

    override def createDatagramChannel(family: ProtocolFamily): Channel = {
        val ch = selectorProvider.openDatagramChannel(family)
        initialJdkChannel(ch)
        createDatagramChannel0(ch)
    }

    private def createDatagramChannel0(ch: DatagramChannel): Channel = {
        val channel = new NioDatagramChannel(system)
        val unsafe  = new NioUnsafeDatagramChannel(channel, ch, SelectionKey.OP_READ)
        channel.setUnsafeChannel(unsafe)
        channel
    }

    override def createFileChannel(): Channel = {
        val channel = new NioFileChannel(system)
        val unsafe  = new NioUnsafeFileChannel(channel)
        channel.setUnsafeChannel(unsafe)
        channel
    }

    override def openReactor(system: ActorSystem): Reactor = if (reactor != null) reactor
    else {
        reactor = new NioReactor(system, this)
        reactor
    }

    override def openIoHandler(system: ActorSystem): IoHandler = new NioHandler(system)

}
