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

package cc.otavia.redis

import cc.otavia.core
import cc.otavia.core.actor.ChannelsActor.{Connect, ConnectReply}
import cc.otavia.core.actor.SocketChannelsActor.ConnectWaitState
import cc.otavia.core.actor.{ChannelsActor, SocketChannelsActor}
import cc.otavia.core.channel.*
import cc.otavia.core.message.*
import cc.otavia.core.stack.*
import cc.otavia.handler.codec.redis.RedisCodec
import cc.otavia.redis.cmd.*

import java.nio.channels.{AlreadyConnectedException, SelectionKey, SocketChannel}
import scala.language.unsafeNulls

object Client {
    type RIDES_CALL = Select | Set
}

class Client extends SocketChannelsActor[Command[? <: CommandResponse]] {

    private var channel: ChannelAddress = _

    override def handler: Option[ChannelInitializer[? <: Channel]] = Some(
      new ChannelInitializer[Channel] {
          override protected def initChannel(ch: Channel): Unit = {
              ch.pipeline.addFirst(new RedisCodec())
              ch.setOutboundMessageBarrier(_ => false)
              ch.setMaxOutboundInflight(512)
          }
      }
    )

    override def continueAsk(stack: AskStack[Command[? <: CommandResponse] | Connect]): Option[StackState] = {
        stack match
            case s: AskStack[Connect] if s.ask.isInstanceOf[Connect] =>
                if (channel == null) connect(s) else stack.`throw`(ExceptionMessage(new AlreadyConnectedException()))
            case _ => handleCommand(stack.asInstanceOf[AskStack[Command[? <: CommandResponse]]])
    }

    private def handleCommand(stack: AskStack[Command[? <: CommandResponse]]): Option[StackState] = {
        stack.state match
            case StackState.start =>
                val state = new StackState.ChannelReplyState()
                channel.ask(stack.ask, state.future)
                state.suspend()
            case state: StackState.ChannelReplyState =>
                if (state.future.isSuccess)
                    stack.`return`(state.future.getNow.asInstanceOf[ReplyOf[Command[? <: CommandResponse]]])
                else stack.`throw`(ExceptionMessage(state.future.causeUnsafe))
    }

    override protected def afterConnected(channel: ChannelAddress): Unit = this.channel = channel

}
