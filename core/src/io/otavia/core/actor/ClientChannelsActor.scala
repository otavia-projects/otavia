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
import io.otavia.core.reactor.{Reactor, ReactorEvent}
import io.otavia.core.util.ActorLogger

import java.net.{InetAddress, InetSocketAddress, SocketAddress}
import java.nio.channels.SelectionKey
import scala.reflect.ClassTag

abstract class ClientChannelsActor[M <: Ask[?] | Notice] extends ChannelsActor[M] {

    protected def connect(host: String, port: Int): Unit = connect(InetSocketAddress.createUnresolved(host, port).nn)

    protected def connect(host: InetAddress, port: Int): Unit = connect(new InetSocketAddress(host, port))

    /** Request to connect to the given [[SocketAddress]]. This method return a channel which is not connected to the
     *  remote address, it only register this channel to [[Reactor]], when register operation completes, this actor will
     *  receive a [[ReactorEvent.RegisterReply]] event, then this actor will call [[handleChannelRegisterReplyEvent]] to
     *  handle register result and connect to remote address.
     *
     *  @param remoteAddress
     *    remote address to connect.
     *  @return
     *    a channel which is registering to [[Reactor]].
     */
    protected def connect(remoteAddress: SocketAddress): Unit = {
        val channel = initAndRegister()
        channel.setUnresolvedRemoteAddress(remoteAddress)
    }

    override protected def newChannel(): Channel = system.channelFactory.newChannel(this)

    override def init(channel: Channel): Unit = {
        handler match
            case Some(h) => channel.pipeline.addLast(h)
            case None    => logger.logWarn(s"The channel $channel is not add any handler!")
    }

    override protected def handleChannelRegisterReplyEvent(event: ReactorEvent.RegisterReply): Unit = {
        super.handleChannelRegisterReplyEvent(event)
        try {
            event.channel.connect()
            finshConnect(event.channel)
        } catch {
            case e: Throwable => event.channel.close()
        }
    }

    protected def finshConnect(channel: Channel): Unit = {}

    protected def disconnect(channel: Channel): Unit = {
        channel.pipeline.disconnect()
    }

}

object ClientChannelsActor {

    final case class Connect(remoteAddress: SocketAddress) extends Ask[UnitReply], Notice
    object Connect {

        def apply(host: String, port: Int)(using IdAllocator: IdAllocator): Connect = Connect(
          InetSocketAddress.createUnresolved(host, port).nn
        )
        def apply(host: InetAddress, port: Int): Connect = Connect(new InetSocketAddress(host, port))

    }

}
