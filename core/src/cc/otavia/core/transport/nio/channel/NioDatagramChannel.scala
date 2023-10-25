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

package cc.otavia.core.transport.nio.channel

import cc.otavia.common.Platform
import cc.otavia.core.channel.socket.DomainSocketAddress
import cc.otavia.core.channel.{AbstractDatagramChannel, ChannelOption}
import cc.otavia.core.system.ActorSystem

import java.net.{InetSocketAddress, SocketAddress, StandardSocketOptions, UnixDomainSocketAddress}
import scala.language.unsafeNulls

class NioDatagramChannel(system: ActorSystem) extends AbstractDatagramChannel(system) {

    private var activeOnOpen: Boolean = false

    override def unsafeChannel: NioUnsafeDatagramChannel = super.unsafeChannel.asInstanceOf[NioUnsafeDatagramChannel]

    override final protected def getTransportExtendedOption[T](option: ChannelOption[T]): T = {
        if (option == ChannelOption.DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION) {
            activeOnOpen.asInstanceOf[T]
        } else {
            val socketOption = NioChannelOption.toSocketOption(option)
            if (socketOption != null)
                NioChannelOption
                    .getOption(unsafeChannel.ch, socketOption)
                    .getOrElse(super.getTransportExtendedOption(option))
            else super.getTransportExtendedOption(option)

        }
    }

    override final protected def setTransportExtendedOption[T](option: ChannelOption[T], value: T): Unit = {
        if (option == ChannelOption.DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION) {
            activeOnOpen = value.asInstanceOf[Boolean]
        } else {
            val socketOption = NioChannelOption.toSocketOption(option)
            if (socketOption != null) {
                // See: https://github.com/netty/netty/issues/576
                if (
                  socketOption == StandardSocketOptions.SO_BROADCAST &&
                  !isAnyLocalAddress &&
                  !Platform.isWindows && !Platform.maybeSuperUser
                ) {
                    logger.warn(
                      "A non-root user can't receive a broadcast packet if the socket " +
                          "is not bound to a wildcard address; setting the SO_BROADCAST flag " +
                          "anyway as requested on the socket which is bound to " +
                          unsafeChannel.ch.getLocalAddress() + '.'
                    )
                }
                Platform.maybeSuperUser
                NioChannelOption.setOption(unsafeChannel.ch, socketOption, value)
            } else super.setTransportExtendedOption(option, value)
        }
    }

    private def isAnyLocalAddress: Boolean = {
        val address: SocketAddress = unsafeChannel.ch.getLocalAddress()
        address match
            case address: InetSocketAddress => address.getAddress.isAnyLocalAddress
            case _                          => false
    }

    override protected def isTransportExtendedOptionSupported(option: ChannelOption[?]): Boolean = {
        if (option == ChannelOption.DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION) true
        else {
            val socketOption = NioChannelOption.toSocketOption(option)
            if (socketOption != null) NioChannelOption.isOptionSupported(unsafeChannel.ch, socketOption)
            else super.isTransportExtendedOptionSupported(option)
        }
    }

}
