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

import cc.otavia.core.actor.ChannelsActor.{Connect, ConnectReply}
import cc.otavia.core.channel.{Channel, ChannelAddress}
import cc.otavia.core.message.*
import cc.otavia.core.reactor.Reactor
import cc.otavia.core.slf4a.Logger
import cc.otavia.core.stack.*
import cc.otavia.core.stack.helper.ChannelFutureState

import java.net.{InetAddress, InetSocketAddress, SocketAddress}

abstract class SocketChannelsActor[M <: Call] extends ChannelsActor[M | Connect] {

    /** Request to connect to the given [[SocketAddress]]. This method return a channel which is not connected to the
     *  remote address, it only register this channel to [[Reactor]], when register operation completes, this actor will
     *  receive a [[ReactorEvent.RegisterReply]] event, then this actor will call [[afterChannelRegisterReplyEvent]] to
     *  handle register result and connect to remote address.
     *
     *  @param stack
     *    remote address to connect.
     *  @return
     *    a [[ConnectReply]] which is registering to [[Reactor]].
     */
    protected def connect(stack: AskStack[Connect]): Option[StackState] = {
        stack.state match
            case StackState.start =>
                // TODO: check remote whether resolved, if not send ask message AddressResolver actor
                val remote  = stack.ask.remote
                val channel = newChannelAndInit()
                val state   = ChannelFutureState()
                channel.connect(remote, stack.ask.local, state.future)
                state.suspend()
            case connectWaitState: ChannelFutureState =>
                val ch = connectWaitState.future.channel
                channels.put(ch.id, ch)
                afterConnected(ch)
                stack.`return`(ConnectReply(ch.id))
    }

    protected def afterConnected(channel: ChannelAddress): Unit = {}

    override protected def newChannel(): Channel = system.channelFactory.openSocketChannel(family)

    override def init(channel: Channel): Unit = {
        handler match
            case Some(h) => channel.pipeline.addLast(h)
            case None    => logger.warn(s"The channel $channel is not add any handler!")
    }

}