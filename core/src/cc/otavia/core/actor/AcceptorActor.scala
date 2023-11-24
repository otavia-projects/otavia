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

import cc.otavia.core.actor.AcceptorActor.*
import cc.otavia.core.actor.ChannelsActor.{Bind, ChannelEstablished}
import cc.otavia.core.address.Address
import cc.otavia.core.channel.*
import cc.otavia.core.message.*
import cc.otavia.core.message.helper.UnitReply
import cc.otavia.core.stack.*
import cc.otavia.core.stack.helper.{ChannelFutureState, FutureState}

abstract class AcceptorActor[W <: AcceptedWorkerActor[? <: Call]] extends ChannelsActor[Bind] {

    private var workers: Address[MessageOf[W]] = _

    /** Number of worker. */
    protected def workerNumber: Int = 1

    protected def workerFactory: WorkerFactory[W]

    override def afterMount(): Unit = {
        workers = system.buildActor(workerFactory, workerNumber)
    }

    override protected def initChannel(channel: Channel): Unit = {
        channel.setOption(ChannelOption.CHANNEL_STACK_BARRIER, Channel.FALSE_FUNC)
        channel.setOption(ChannelOption.CHANNEL_MAX_STACK_INFLIGHT, Int.MaxValue)
        channel.setOption(ChannelOption.CHANNEL_STACK_HEAD_OF_LINE, false)
    }

    final override protected def newChannel(): Channel = system.channelFactory.openServerSocketChannel(family)

    final protected def bind(stack: AskStack[Bind]): Option[StackState] = {
        stack.state match
            case StackState.start =>
                val channel = createChannelAndInit()
                val state   = ChannelFutureState()
                channel.bind(stack.ask.local, state.future)
                state.suspend()
            case bindState: ChannelFutureState =>
                if (bindState.future.isSuccess) {
                    val channel = bindState.future.channel
                    afterBind(channel)
                    stack.`return`(ChannelEstablished(channel.id))
                } else {
                    stack.`throw`(ExceptionMessage(bindState.future.causeUnsafe))
                }
    }

    protected def afterBind(channel: ChannelAddress): Unit = {
        // default do nothing
    }

    override def resumeAsk(stack: AskStack[Bind]): Option[StackState] = bind(stack)

    override def resumeChannelStack(stack: ChannelStack[AnyRef]): Option[StackState] = {
        stack match
            case _: ChannelStack[?] if stack.message.isInstanceOf[Channel] =>
                handleAcceptedStack(stack.asInstanceOf[ChannelStack[Channel]])
    }

    private def handleAcceptedStack(stack: ChannelStack[Channel]): Option[StackState] = {
        stack.state match
            case StackState.start =>
                val state = FutureState[UnitReply]()
                workers.ask(AcceptedChannel(stack.message), state.future)
                state.suspend()
            case state: FutureState[?] if state.id == 0 =>
                if (state.future.isSuccess) stack.`return`()
                else
                    stack.`return`(state.future.causeUnsafe)
    }

}

object AcceptorActor {

    trait WorkerFactory[W <: AcceptedWorkerActor[? <: Call]] extends ActorFactory[W] {
        override def newActor(): W
    }

    final case class AcceptedChannel(channel: ChannelAddress) extends Ask[UnitReply]

}
