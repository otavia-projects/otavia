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
import io.otavia.core.log4a.ActorLogger
import io.otavia.core.message.*
import io.otavia.core.reactor.*
import io.otavia.core.stack.*
import io.otavia.core.system.ActorThread
import io.otavia.core.timer.Timer

import java.net.{InetAddress, InetSocketAddress, SocketAddress}
import java.nio.channels.SelectionKey
import java.util.concurrent.CancellationException
import scala.reflect.ClassTag
import scala.util.*

abstract class ChannelsActor[M <: Call] extends AbstractActor[M] {

    override def self: ChannelsActorAddress[M] = super.self.asInstanceOf[ChannelsActorAddress[M]]

    final def reactor: Reactor = system.reactor

    private var channelCursor = 0
    private[core] def generateChannelId(): Int = {
        val channelId = channelCursor
        channelCursor += 1
        channelId
    }

    final override protected def receiveIOEvent(event: Event): Unit = event match
        case e: ReactorEvent.RegisterReply =>
            e.channel.handleChannelRegisterReplyEvent(e)
            afterChannelRegisterReplyEvent(e)
        case e: ReactorEvent.DeregisterReply =>
            e.channel.handleChannelDeregisterReplyEvent(e)
            afterChannelDeregisterReplyEvent(e)
        case e: ReactorEvent.ChannelClose =>
            e.channel.handleChannelCloseEvent(e)
            afterChannelCloseEvent(e)
        case e: ReactorEvent.ChannelReadiness =>
            e.channel.handleChannelReadinessEvent(e)
            afterChannelReadinessEvent(e)
        case channelTimeoutEvent: ChannelTimeoutEvent =>
            channelTimeoutEvent.channel.handleChannelTimeoutEvent(channelTimeoutEvent.registerId)
            afterChannelTimeoutEvent(channelTimeoutEvent)

    // Event from Reactor

    /** Handle channel close event */
    protected def afterChannelCloseEvent(event: ReactorEvent.ChannelClose): Unit = {}

    /** Handle channel register result event */
    protected def afterChannelRegisterReplyEvent(event: ReactorEvent.RegisterReply): Unit = {}

    /** Handle channel deregister result event */
    private def afterChannelDeregisterReplyEvent(event: ReactorEvent.DeregisterReply): Unit = {}

    /** Handle channel readiness event */
    private def afterChannelReadinessEvent(event: ReactorEvent.ChannelReadiness): Unit = {}

    // Event from Timer

    private def afterChannelTimeoutEvent(channelTimeoutEvent: ChannelTimeoutEvent): Unit = {}

    // End handle event.

    /** call by pipeline tail context
     *  @param msg
     */
    def receiveChannelMessage(channel: Channel, msg: AnyRef, msgId: Long): Unit = {
        val frame = new ChannelFrame(null, msgId)
    }

    def continueChannelMessage(msg: AnyRef | ChannelFrame): Option[StackState]

    val handler: Option[ChannelInitializer[? <: Channel]] = None

    /** Initial and register a channel for this [[ChannelsActor]]. It do the flowing things:
     *    1. Create the [[Channel]].
     *    1. Initial the [[Channel]] with [[init]].
     *    1. Register the [[Channel]] to [[Reactor]]. When register channel success, the [[Reactor]] will send a
     *       [[ReactorEvent.RegisterReply]] event to this actor, then the [[afterChannelRegisterReplyEvent]] will be
     *       called to handle the register result [[Event]].
     */
    protected def initAndRegister(): Channel = {
        val channel = newChannel()
        Try { init(channel) } match
            case Success(_) => channel.register()
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
