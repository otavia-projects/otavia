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

import io.otavia.core.actor.AcceptorActor.*
import io.otavia.core.actor.ChannelsActor.{Bind, RegisterWaitState}
import io.otavia.core.address.Address
import io.otavia.core.channel.*
import io.otavia.core.reactor.ReactorEvent
import io.otavia.core.stack.*
//import io.otavia.core.channel.impl.NioServerSocketChannel
import io.otavia.core.message.*
import io.otavia.core.stack.{ChannelFrame, ExceptionWaiter, ReplyWaiter, StackState}

import java.net.{InetAddress, InetSocketAddress, SocketAddress}
import scala.runtime.Nothing$

abstract class AcceptorActor[W <: AcceptedWorkerActor[_ <: Call]] extends ChannelsActor[Bind] {

    private var localAddress: SocketAddress    = _
    private var bound: Boolean                 = false
    private var workers: Address[MessageOf[W]] = _

    /** Number of worker. */
    protected def workerNumber: Int = 1

    protected def workerFactory: WorkerFactory[W]

    override def afterMount(): Unit = {
        workers = system.crateActor(workerFactory, workerNumber)
    }

    override def init(channel: Channel): Unit = {
        if (handler.nonEmpty) {
            channel.pipeline.addLast(handler.get)
        }
        channel.pipeline.addLast(new AcceptorHandler)
    }

    final override protected def newChannel(): Channel = system.serverChannelFactory.newChannel(this)

    final protected def bind(stack: AskStack[Bind]): Option[StackState] = {
        stack.stackState match
            case StackState.initialState =>
                val channel = newChannel()
                channel.setUnresolvedLocalAddress(stack.ask.local)
                initAndRegister(channel, stack)
            case registerWaitState: RegisterWaitState =>
                val event = registerWaitState.registerFuture.getNow
                try {
                    if (event.cause.isEmpty) {
                        event.channel.pipeline.bind()
                        bound = true
                        afterBind(event.channel)
                        stack.`return`(UnitReply())
                    } else {
                        event.channel.close()
                        stack.`throw`(ExceptionMessage(event.cause.get))
                    }
                } catch {
                    case cause: Throwable =>
                        event.channel.close()
                        stack.`throw`(ExceptionMessage(cause))
                }
    }

    protected def afterBind(channel: Channel): Unit = {
        // default do nothing
    }

    override def continueChannel(stack: ChannelStack[AnyRef]): Option[StackState] = {
        stack match
            case _: ChannelStack[_] if stack.message.isInstanceOf[Channel] =>
                handleAcceptedStack(stack.asInstanceOf[ChannelStack[Channel]])
    }

    private def handleAcceptedStack(stack: ChannelStack[Channel]): Option[StackState] = {
        stack.stackState match
            case StackState.initialState =>
                val state = new DispatchState()
                workers.ask(AcceptedChannel(stack.message), state.dispatchFuture)
                state.suspend()
            case state: DispatchState =>
                ???
    }

}

object AcceptorActor {

    trait WorkerFactory[W <: AcceptedWorkerActor[_ <: Call]] extends ActorFactory[W] {
        override def newActor(): W
    }

    final case class AcceptedChannel(channel: Channel) extends Ask[UnitReply]

    private class AcceptorHandler extends ChannelHandler {
        override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef): Unit = {
            val accepted = msg.asInstanceOf[Channel]
            val msgId    = ctx.channel.generateMessageId
            ctx.fireChannelRead(accepted, msgId)
        }
    }

    final class DispatchState extends StackState {

        val dispatchFuture: ReplyFuture[UnitReply] = ReplyFuture[UnitReply]()

        override def resumable(): Boolean = dispatchFuture.isDone

    }

}
