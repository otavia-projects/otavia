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

import cc.otavia.core.channel.*
import cc.otavia.core.channel.message.{ReadPlan, ReadPlanFactory}
import cc.otavia.core.message.ReactorEvent
import cc.otavia.core.system.ActorSystem

import java.net.SocketAddress

class NioServerSocketChannel(system: ActorSystem) extends AbstractServerChannel(system) {

    override def unsafeChannel: NioUnsafeServerSocketChannel =
        super.unsafeChannel.asInstanceOf[NioUnsafeServerSocketChannel]

    override def localAddress: Option[SocketAddress] = Some(unsafeChannel.localAddress)

    override def remoteAddress: Option[SocketAddress] = ???

    override protected def getTransportExtendedOption[T](option: ChannelOption[T]): T = {
        if (option == ChannelOption.SO_BACKLOG) unsafeChannel.getBacklog.asInstanceOf[T]
        else {
            val socketOption = NioChannelOption.toSocketOption(option)
            if (socketOption != null)
                NioChannelOption
                    .getOption(unsafeChannel.ch, socketOption)
                    .getOrElse(super.getTransportExtendedOption(option))
            else super.getTransportExtendedOption(option)
        }
    }

    override protected def setTransportExtendedOption[T](option: ChannelOption[T], value: T): Unit = {
        if (option == ChannelOption.SO_BACKLOG) unsafeChannel.setBacklog(value.asInstanceOf[Int])
        else {
            val socketOption = NioChannelOption.toSocketOption(option)
            if (socketOption != null) {
                NioChannelOption.setOption(unsafeChannel.ch, socketOption, value)
            } else
                super.setExtendedOption(option, value)
        }
    }

    override protected def isTransportExtendedOptionSupported(option: ChannelOption[?]): Boolean = {
        if (option == ChannelOption.SO_BACKLOG) true
        else {
            val socketOption = NioChannelOption.toSocketOption(option)
            if (socketOption != null) NioChannelOption.isOptionSupported(unsafeChannel.ch, socketOption)
            else super.isTransportExtendedOptionSupported(option)
        }
    }

}
