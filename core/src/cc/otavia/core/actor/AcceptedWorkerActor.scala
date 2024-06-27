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

import cc.otavia.core.actor.AcceptorActor.AcceptedChannel
import cc.otavia.core.channel.{Channel, ChannelAddress}
import cc.otavia.core.message.*
import cc.otavia.core.message.helper.UnitReply
import cc.otavia.core.stack.*
import cc.otavia.core.stack.helper.{ChannelFutureState, StartState}

import scala.language.unsafeNulls

abstract class AcceptedWorkerActor[M <: Call] extends ChannelsActor[M | AcceptedChannel] {

    override protected def newChannel(): Channel = throw new UnsupportedOperationException()

    /** handle [[AcceptedChannel]] message, this method will called by [[resumeAsk]] */
    final protected def handleAccepted(stack: AskStack[AcceptedChannel]): StackYield = {
        stack.state match
            case _: StartState =>
                val channel = stack.ask.channel.asInstanceOf[Channel]
                if (!channel.isMounted) channel.mount(this)
                var res: StackYield = null
                try {
                    initChannel(channel)
                    val state = ChannelFutureState()
                    channel.register(state.future)
                    res = stack.suspend(state)
                } catch {
                    case cause: Throwable =>
                        channel.close(ChannelFuture()) // ignore close result.
                        res = stack.`throw`(ExceptionMessage(cause))
                }
                res
            case registerState: ChannelFutureState =>
                val future = registerState.future
                if (future.isSuccess) {
                    afterAccepted(future.channel)
                    stack.`return`(UnitReply())
                } else stack.`throw`(future.causeUnsafe)
    }

    protected def afterAccepted(channel: ChannelAddress): Unit = {
        // default, do nothing
    }

}
