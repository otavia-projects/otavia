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

import io.otavia.core.actor.Actor
import io.otavia.core.address.{Address, ChannelsActorAddress}
import io.otavia.core.channel.*
import io.otavia.core.message.*
import io.otavia.core.reactor.*
import io.otavia.core.stack.*
import io.otavia.core.system.ActorThread

import java.net.{InetAddress, InetSocketAddress, SocketAddress}
import java.nio.channels.SelectionKey
import java.util.concurrent.CancellationException
import scala.reflect.ClassTag
import scala.util.*

abstract class ChannelsActor[M <: Ask[?] | Notice] extends Actor[M] {

    override def self: ChannelsActorAddress[M] = super.self.asInstanceOf[ChannelsActorAddress[M]]

    final def reactor: Reactor = system.reactor

    private var channelCursor = 0
    private[core] def generateChannelId(): Int = {
        val channelId = channelCursor
        channelCursor += 1
        channelId
    }

    private[core] def attachFrame(askId: Long, waiter: ChannelReplyWaiter[_]): Unit = {}

    override private[core] def receiveNotice(notice: Notice): Unit = {}
    override private[core] def receiveAsk(ask: Ask[?]): Unit       = {} // flight and pending stack frame
    override private[core] def receiveReply(reply: Reply): Unit    = {}

    /** fire inbound event to pipeline */
    def receiveEvent(event: Event): Unit = {
        event match
            case e: ReactorEvent.RegisterReply    => handleChannelRegisterReplyEvent(e)
            case e: ReactorEvent.DeregisterReply  => handleChannelDeregisterReplyEvent(e)
            case e: ReactorEvent.ChannelClose     => handleChannelCloseEvent(e)
            case e: ReactorEvent.ChannelReadiness => handleChannelReadinessEvent(e)
    }

    // Event from Reactor

    /** Handle channel close event */
    protected def handleChannelCloseEvent(event: ReactorEvent.ChannelClose): Unit =
        event.channel.handleChannelCloseEvent(event)

    /** Handle channel register result event */
    protected def handleChannelRegisterReplyEvent(event: ReactorEvent.RegisterReply): Unit =
        event.channel.handleChannelRegisterReplyEvent(event)

    /** Handle channel deregister result event */
    protected def handleChannelDeregisterReplyEvent(event: ReactorEvent.DeregisterReply): Unit =
        event.channel.handleChannelDeregisterReplyEvent(event)

    /** Handle channel readiness event */
    protected def handleChannelReadinessEvent(event: ReactorEvent.ChannelReadiness): Unit =
        event.channel.handleChannelReadinessEvent(event)

    // Event from Timer
    // ...

    /** call by pipeline tail context
     *  @param msg
     */
    def receiveChannelMessage(channel: Channel, msg: AnyRef, msgId: Long): Unit = {
        val frame = new ChannelFrame(null, msgId)
    }

    def continueAsk(state: M & Ask[?] | AskFrame): Option[StackState] = throw new NotImplementedError(
      getClass.getName + ": an implementation is missing"
    )
    def continueNotice(state: M & Notice | NoticeFrame): Option[StackState] = throw new NotImplementedError(
      getClass.getName + ": an implementation is missing"
    )
    def continueReply(state: M & Reply): Option[StackState] = throw new NotImplementedError(
      getClass.getName + ": an implementation is missing"
    )
    def continueChannelMessage(msg: AnyRef | ChannelFrame): Option[StackState]

    val handler: Option[ChannelHandler] = None

    /** Initial and register a channel for this [[ChannelsActor]]. It do the flowing things:
     *    1. Create the [[Channel]].
     *    1. Initial the [[Channel]] with [[init]].
     *    1. Register the [[Channel]] to [[Reactor]]. When register channel success, the [[Reactor]] will send a
     *       [[ReactorEvent.RegisterReply]] event to this actor, then the [[handleChannelRegisterReplyEvent]] will be
     *       called to handle the register result [[Event]].
     */
    protected def initAndRegister(): Channel = {
        val channel = newChannel()
        Try { init(channel) } match
            case Success(_) => reactor.register(channel)
            case Failure(e) => channel.close()
        channel
    }

    /** Create a new channel and set executor. */
    protected def newChannel(): Channel

    @throws[Exception]
    def init(channel: Channel): Unit

    def close(): Unit

    // 1. how to design tail handler, channel group self ?
    // 2. how to use message codec in handler
    // 3. channel chooser

    final def inExecutor(): Boolean = {
        Thread.currentThread() match
            case thread: io.otavia.core.system.ActorThread => thread.currentRunningActor() == this
            case _                                         => false
    }

}

object ChannelsActor {}
