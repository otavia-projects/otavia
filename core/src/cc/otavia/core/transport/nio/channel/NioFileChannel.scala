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

import cc.otavia.core.actor.ChannelsActor
import cc.otavia.core.channel.*
import cc.otavia.core.channel.message.{FileReadPlan, ReadPlan, ReadPlanFactory}
import cc.otavia.core.message.ReactorEvent
import cc.otavia.core.stack.{ChannelPromise, ChannelReplyFuture}
import cc.otavia.core.system.ActorSystem

import java.io.File
import java.net.SocketAddress
import java.nio.channels.FileChannel
import java.nio.file.{OpenOption, Path, StandardOpenOption}
import scala.jdk.CollectionConverters.*
import scala.language.unsafeNulls

class NioFileChannel(system: ActorSystem) extends AbstractFileChannel(system) {

    override def unsafeChannel: NioUnsafeFileChannel = super.unsafeChannel.asInstanceOf[NioUnsafeFileChannel]

    override def getOption[T](option: ChannelOption[T]): T = ???

    override def setOption[T](option: ChannelOption[T], value: T): Channel = ???

    override def isOptionSupported(option: ChannelOption[?]): Boolean = ???

    override def isOpen: Boolean = ???

    override def isActive: Boolean = ???

    override def isRegistered: Boolean = ???

    override def isShutdown(direction: ChannelShutdownDirection): Boolean = ???

    override def localAddress: Option[SocketAddress] = ???

    override def remoteAddress: Option[SocketAddress] = ???

    override def writableBytes: Long = ???

    override private[core] def closeAfterCreate(): Unit = ???

}