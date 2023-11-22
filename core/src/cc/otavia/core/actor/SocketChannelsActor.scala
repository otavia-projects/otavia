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

package cc.otavia.core.actor

import cc.otavia.core.actor.ChannelsActor.ChannelEstablished
import cc.otavia.core.actor.SocketChannelsActor.Connect
import cc.otavia.core.channel.{Channel, ChannelAddress}
import cc.otavia.core.message.*
import cc.otavia.core.reactor.Reactor
import cc.otavia.core.slf4a.Logger
import cc.otavia.core.stack.*
import cc.otavia.core.stack.helper.ChannelFutureState

import java.net.{InetAddress, InetSocketAddress, SocketAddress}

abstract class SocketChannelsActor[M <: Call] extends ChannelsActor[M] {

    /** Request to connect to the given [[SocketAddress]]. This method return a channel which is not connected to the
     *  remote address, it only register this channel to [[Reactor]], when register operation completes, this actor will
     *  receive a [[ReactorEvent.RegisterReply]] event, then this actor will call [[afterChannelRegisterReplyEvent]] to
     *  handle register result and connect to remote address.
     *
     *  @param stack
     *    remote address to connect.
     *  @return
     *    a [[ChannelEstablished]] which is registering to [[Reactor]].
     */
    protected def connect(stack: AskStack[Connect]): Option[StackState] = {
        stack.state match
            case StackState.start =>
                // TODO: check remote whether resolved, if not send ask message AddressResolver actor
                val remote  = stack.ask.remote
                val channel = createChannelAndInit()
                val state   = ChannelFutureState()
                channel.connect(remote, stack.ask.local, state.future)
                state.suspend()
            case connectState: ChannelFutureState =>
                val ch = connectState.future.channel
                afterConnected(ch)
                stack.`return`(ChannelEstablished(ch.id))
    }

    final protected def connect(connect: Connect): Option[ChannelFutureState] = {
        val channel = createChannelAndInit()
        val state   = ChannelFutureState()
        channel.connect(connect.remote, connect.local, state.future)
        state.suspend().asInstanceOf[Option[ChannelFutureState]]
    }

    final protected def connect(connect: Connect, future: ChannelFuture): ChannelFuture = {
        val channel = createChannelAndInit()
        channel.connect(connect.remote, connect.local, future)
        future
    }

    final protected def connect(remote: SocketAddress, local: Option[SocketAddress]): Option[ChannelFutureState] = {
        val channel = createChannelAndInit()
        val state   = ChannelFutureState()
        channel.connect(remote, local, state.future)
        state.suspend().asInstanceOf[Option[ChannelFutureState]]
    }

    final protected def connect(remote: SocketAddress, l: Option[SocketAddress], fu: ChannelFuture): ChannelFuture = {
        val channel = createChannelAndInit()
        channel.connect(remote, l, fu)
        fu
    }

    protected def afterConnected(channel: ChannelAddress): Unit = {}

    override protected def newChannel(): Channel = system.channelFactory.openSocketChannel(family)

}

object SocketChannelsActor {

    trait Connect extends Ask[ChannelEstablished] {

        def remote: SocketAddress

        def local: Option[SocketAddress]

    }

    case class ConnectChannel(remote: SocketAddress, local: Option[SocketAddress])
        extends Connect
        with Ask[ChannelEstablished]

}
