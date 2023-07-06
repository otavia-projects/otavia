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

package io.otavia.core.transport.nio

import io.otavia.core.channel.socket.SocketProtocolFamily
import io.otavia.core.channel.{Channel, ChannelException}
import io.otavia.core.reactor.{DefaultSelectStrategy, IoHandler, Reactor}
import io.otavia.core.slf4a.Logger
import io.otavia.core.system.ActorSystem
import io.otavia.core.transport.TransportFactory
import io.otavia.core.transport.nio.channel.{NioDatagramChannel, NioFileChannel, NioServerSocketChannel, NioSocketChannel}
import io.otavia.core.transport.reactor.nio.{NioHandler, NioReactor}
import sun.nio.ch.Net

import java.io.IOException
import java.net.{ProtocolFamily, StandardProtocolFamily}
import java.nio.channels.*
import java.nio.channels.spi.SelectorProvider
import scala.language.unsafeNulls

class NIOTransportFactory(system: ActorSystem) extends TransportFactory {

    private val selectorProvider = SelectorProvider.provider()
    private var reactor: Reactor = _

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

    override def openServerSocketChannel(): Channel = {
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

        new NioServerSocketChannel(ch, family)
    }

    override def openServerSocketChannel(family: ProtocolFamily): Channel = {
        val ch = selectorProvider.openServerSocketChannel(family)
        initialJdkChannel(ch)
        new NioServerSocketChannel(ch, family)
    }

    override def openSocketChannel(): Channel = {
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
        new NioSocketChannel(ch, family)
    }

    override def openSocketChannel(family: ProtocolFamily): Channel = {
        val ch = selectorProvider.openSocketChannel(family)
        new NioSocketChannel(ch, family)
    }

    override def openDatagramChannel(): Channel = {
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

        new NioDatagramChannel(ch, family)
    }

    override def openDatagramChannel(family: SocketProtocolFamily): Channel = {
        val ch = selectorProvider.openDatagramChannel(family)
        new NioDatagramChannel(ch, family)
    }

    override def openFileChannel(): Channel = new NioFileChannel()

    override def openReactor(system: ActorSystem): Reactor = if (reactor != null) reactor
    else {
        reactor = new NioReactor(system, this)
        reactor
    }

    override def openIoHandler(system: ActorSystem): IoHandler =
        new NioHandler(selectorProvider, DefaultSelectStrategy, system)

}
