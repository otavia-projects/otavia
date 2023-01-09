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

package io.otavia.core.actor

import io.otavia.core.channel.Channel
import io.otavia.core.message.*
import io.otavia.core.reactor.RegisterReplyEvent

import java.net.{InetAddress, InetSocketAddress, SocketAddress}
import java.nio.channels.SelectionKey
import scala.reflect.ClassTag

abstract class ClientChannelsActor[M <: Ask[?] | Notice] extends ChannelsActor[M] {

    protected def connect(host: String, port: Int): Channel = connect(InetSocketAddress.createUnresolved(host, port))
    protected def connect(host: InetAddress, port: Int): Channel = connect(new InetSocketAddress(host, port))
    protected def connect(remoteAddress: SocketAddress): Channel = {
        val channel = initAndRegister()
        // 1. create channel
        // 2. init: pipeline and config
        // 3. register to reactor, then set channel state registered and fireChannelRegistered
        // 4. connect then set channel state active and fireChannelActive

        throw new NotImplementedError(getClass.getName + ": an implementation is missing")
    }

    protected def disconnect(channel: Channel): Unit = {
        channel.pipeline.disconnect()
    }

    private[core] override def receiveRegisterReply(event: RegisterReplyEvent): Unit = if (event.succeed) {
        event.channel.pipeline.fireChannelRegistered()
        val key: SelectionKey = ??? // event.channel.unsafe.selectionKey()
        key.interestOps(key.interestOps() | SelectionKey.OP_CONNECT)
        event.channel.pipeline.connect(event.channel.remoteAddress.get)
    } else {
        // close channel
    }

}

object ClientChannelsActor {
    final case class Connect(remoteAddress: SocketAddress)(using IdAllocator) extends Ask[UnitReply], Notice
    object Connect {
        def apply(host: String, port: Int)(using IdAllocator: IdAllocator): Connect = Connect(
          InetSocketAddress.createUnresolved(host, port)
        )
        def apply(host: InetAddress, port: Int)(using IdAllocator): Connect = Connect(new InetSocketAddress(host, port))
    }
}
