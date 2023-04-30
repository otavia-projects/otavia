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

import io.otavia.core.actor.ChannelsActor.{Connect, ConnectReply, RegisterWaitState}
import io.otavia.core.actor.ClientChannelsActor.ConnectWaitState
import io.otavia.core.channel.Channel
import io.otavia.core.slf4a.ActorLogger
import io.otavia.core.message.*
import io.otavia.core.reactor.{Reactor, ReactorEvent}
import io.otavia.core.stack.*

import java.net.{InetAddress, InetSocketAddress, SocketAddress}

abstract class ClientChannelsActor[M <: Call] extends ChannelsActor[M] {

    protected def connect(host: String, port: Int): Unit = connect(InetSocketAddress.createUnresolved(host, port).nn)

    protected def connect(host: InetAddress, port: Int): Unit = connect(new InetSocketAddress(host, port))

    /** Request to connect to the given [[SocketAddress]]. This method return a channel which is not connected to the
     *  remote address, it only register this channel to [[Reactor]], when register operation completes, this actor will
     *  receive a [[ReactorEvent.RegisterReply]] event, then this actor will call [[afterChannelRegisterReplyEvent]] to
     *  handle register result and connect to remote address.
     *
     *  @param remoteAddress
     *    remote address to connect.
     *  @return
     *    a channel which is registering to [[Reactor]].
     */
    protected def connect(remoteAddress: SocketAddress): Unit = {}

    protected def connect(stack: AskStack[Connect]): Option[StackState] = {
        stack.stackState match
            case StackState.initialState =>
                // TODO: check remote whether resolved, if not send ask message AddressResolver actor
                val remote = stack.ask.remote

                val channel = newChannelAndInit()
                initAndRegister(channel, stack)
            case registerWaitState: RegisterWaitState =>
                val channel: Channel = registerWaitState.registerFuture.getNow
                val state            = new ConnectWaitState()
                channel.connect(stack.ask.remote, stack.ask.local, state.connectFuture)
                state.suspend()
            case connectWaitState: ConnectWaitState =>
                val ch = connectWaitState.connectFuture.getNow
                channels.put(ch.id, ch)
                afterConnected(ch)
                stack.`return`(ConnectReply(ch.id))
    }



    protected def afterConnected(channel: Channel): Unit = {}

    override protected def newChannel(): Channel = system.channelFactory.newChannel(this)

    override def init(channel: Channel): Unit = {
        handler match
            case Some(h) => channel.pipeline.addLast(h)
            case None    => logWarn(s"The channel $channel is not add any handler!")
    }

}

object ClientChannelsActor {

    final class ConnectWaitState extends StackState {
        val connectFuture: ChannelFuture = ChannelFuture()
    }

}
