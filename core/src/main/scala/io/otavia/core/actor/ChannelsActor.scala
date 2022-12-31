/*
 * Copyright 2022 Yan Kun
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
            case registerReplyEvent: RegisterReplyEvent => receiveRegisterReply(registerReplyEvent)
            case channelEvent: ChannelEvent =>
                if (!channelEvent.selectionKey.isValid) {
                    // close channel
                }
                try {
                    val readyOps = channelEvent.selectionKey.readyOps()
                    if ((readyOps & SelectionKey.OP_CONNECT) != 0) {
                        var ops = channelEvent.selectionKey.interestOps()
                        ops = ops & ~SelectionKey.OP_CONNECT
                        channelEvent.selectionKey.interestOps(ops)
                        // finishConnect for this channel
//            channelEvent.channel.unsafe.finishConnect()
                        channelEvent.channel.pipeline.fireChannelActive()
                        // fireChannelActive
                    }
                    if ((readyOps & SelectionKey.OP_WRITE) != 0) {
                        // forceFlush for this channel
                    }
                    // Also check for readOps of 0 to workaround possible JDK bug which may otherwise lead
                    // to a spin loop
                    if ((readyOps & (SelectionKey.OP_READ | SelectionKey.OP_ACCEPT)) != 0 || readyOps == 0) {
                        // fire read inbound event
                    }
                } catch {
                    case ignored: CancellationException => // close channel
                }
    }

    private[core] def receiveRegisterReply(registerReplyEvent: RegisterReplyEvent): Unit

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

//  val channelFactory: ChannelFactory[? <: Channel]

    def initAndRegister(): Channel = {
        val channel = ??? // channelFactory.newChannel()
        init(channel)
//    channel.unsafe.register()
        //    system.reactor.register(ChannelRegister(channel, self))
        channel
    }

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
