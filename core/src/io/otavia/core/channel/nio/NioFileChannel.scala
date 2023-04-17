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

import io.netty5.util.{Attribute, AttributeKey}
import io.otavia.core.actor.ChannelsActor
import io.otavia.core.channel.*
import io.otavia.core.reactor.ReactorEvent
import io.otavia.core.stack.ChannelReplyFuture

import java.io.File
import java.net.SocketAddress
import java.nio.channels.FileChannel
import java.nio.file.{Path, StandardOpenOption}
import scala.language.unsafeNulls

class NioFileChannel(private val ch: FileChannel) extends AbstractFileChannel {

    def this(file: File) = this(FileChannel.open(file.toPath, StandardOpenOption.READ))

    def this(path: Path) = this(FileChannel.open(path, StandardOpenOption.READ))

    private def javaChannel: FileChannel = ch

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

    override private[core] def handleChannelCloseEvent(event: ReactorEvent.ChannelClose): Unit = ???

    override private[core] def handleChannelRegisterReplyEvent(event: ReactorEvent.RegisterReply): Unit = ???

    override private[core] def handleChannelDeregisterReplyEvent(event: ReactorEvent.DeregisterReply): Unit = ???

    override private[core] def handleChannelReadinessEvent(event: ReactorEvent.ChannelReadiness): Unit = ???

    override private[core] def handleChannelTimeoutEvent(eventRegisterId: Long): Unit = ???

    override def attr[T](key: AttributeKey[T]): Attribute[T] = ???

    override def hasAttr[T](key: AttributeKey[T]): Boolean = ???

    override def inboundMessageBarrier: AnyRef => Boolean = ???

    override def setInboundMessageBarrier(barrier: AnyRef => Boolean): Unit = ???

    override def outboundMessageBarrier: AnyRef => Boolean = ???

    override def setOutboundMessageBarrier(barrier: AnyRef => Boolean): Unit = ???

    override def outboundInflightSize: Int = ???

    override def outboundPendingSize: Int = ???

    override def inboundInflightSize: Int = ???

    override def inboundPendingSize: Int = ???

    override def ask(value: AnyRef, future: ChannelReplyFuture): ChannelReplyFuture = ???

    override def batchAsk(asks: Seq[AnyRef], futures: Seq[ChannelReplyFuture]): Seq[ChannelReplyFuture] = ???

    override def notice(value: AnyRef): Unit = ???

    override def batchNotice(notices: Seq[AnyRef]): Unit = ???

    override def generateMessageId: Long = ???

    override private[core] def onInboundMessage(msg: AnyRef): Unit = ???

    override private[core] def onInboundMessage(msg: AnyRef, id: Long): Unit = ???

}
