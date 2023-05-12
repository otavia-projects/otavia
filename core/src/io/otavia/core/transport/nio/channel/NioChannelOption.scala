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

import io.otavia.core.channel.{ChannelException, ChannelOption}

import java.io.IOException
import java.net.{SocketOption, StandardSocketOptions}
import java.nio.channels.{NetworkChannel, ServerSocketChannel}
import scala.language.unsafeNulls

/** Provides [[ChannelOption]] over a given [[SocketOption]] which is then passed through the underlying
 *  [[NetworkChannel]].
 */
final class NioChannelOption[T](private val option: SocketOption[T]) extends ChannelOption[T](option.name())

object NioChannelOption {

    /** Returns a [[ChannelOption]] for the given [[SocketOption]]. */
    def of[T](option: SocketOption[T]): ChannelOption[T] = new NioChannelOption[T](option)

    // Internal helper methods to remove code duplication between Nio*Channel implementations.
    def setOption[T](channel: NetworkChannel, option: SocketOption[T], value: T): Unit = {
        if (channel.isInstanceOf[ServerSocketChannel] && option == StandardSocketOptions.IP_TOS) {
            // Skip IP_TOS as a workaround for a JDK bug:
            // See https://mail.openjdk.java.net/pipermail/nio-dev/2018-August/005365.html
        } else {
            try { channel.setOption(option, value) }
            catch { case e: IOException => throw new ChannelException(e) }
        }
    }

    def getOption[T](channel: NetworkChannel, option: SocketOption[T]): Option[T] = {
        if (channel.isInstanceOf[ServerSocketChannel] && option == StandardSocketOptions.IP_TOS) {
            // Skip IP_TOS as a workaround for a JDK bug:
            // See https://mail.openjdk.java.net/pipermail/nio-dev/2018-August/005365.html
            None
        } else {
            try { Some(channel.getOption(option)) }
            catch {
                case e: IOException => throw new ChannelException(e)
            }
        }
    }

    def isOptionSupported(channel: NetworkChannel, option: SocketOption[?]): Boolean =
        channel.supportedOptions().contains(option)

    def toSocketOption[T](option: ChannelOption[T]): SocketOption[T] | Null = option match
        case nio: NioChannelOption[T]   => nio.option
        case ChannelOption.SO_RCVBUF    => StandardSocketOptions.SO_RCVBUF.asInstanceOf[SocketOption[T]]
        case ChannelOption.SO_SNDBUF    => StandardSocketOptions.SO_SNDBUF.asInstanceOf[SocketOption[T]]
        case ChannelOption.TCP_NODELAY  => StandardSocketOptions.TCP_NODELAY.asInstanceOf[SocketOption[T]]
        case ChannelOption.SO_KEEPALIVE => StandardSocketOptions.SO_KEEPALIVE.asInstanceOf[SocketOption[T]]
        case ChannelOption.SO_REUSEADDR => StandardSocketOptions.SO_REUSEADDR.asInstanceOf[SocketOption[T]]
        case ChannelOption.SO_LINGER    => StandardSocketOptions.SO_LINGER.asInstanceOf[SocketOption[T]]
        case ChannelOption.SO_BROADCAST => StandardSocketOptions.SO_BROADCAST.asInstanceOf[SocketOption[T]]
        case ChannelOption.IP_MULTICAST_LOOP_DISABLED =>
            StandardSocketOptions.IP_MULTICAST_LOOP.asInstanceOf[SocketOption[T]]
        case ChannelOption.IP_MULTICAST_IF  => StandardSocketOptions.IP_MULTICAST_IF.asInstanceOf[SocketOption[T]]
        case ChannelOption.IP_MULTICAST_TTL => StandardSocketOptions.IP_MULTICAST_TTL.asInstanceOf[SocketOption[T]]
        case ChannelOption.IP_TOS           => StandardSocketOptions.IP_TOS.asInstanceOf[SocketOption[T]]
        case _                              => null

}
