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
import cc.otavia.core.actor.ChannelsActor.RegisterWaitState
import cc.otavia.core.channel.{Channel, ChannelAddress}
import cc.otavia.core.message.*
import cc.otavia.core.stack.{AskStack, StackState}

import scala.reflect.{ClassTag, classTag}

abstract class AcceptedWorkerActor[M <: Call] extends ChannelsActor[M | AcceptedChannel] {

    override protected def newChannel(): Channel = throw new UnsupportedOperationException()

    /** handle [[AcceptedChannel]] message, this method will called by [[continueAsk]] */
    final protected def handleAccepted(stack: AskStack[AcceptedChannel]): Option[StackState] = {
        stack.state match
            case StackState.`start` =>
                val channel = stack.ask.channel
                initAndRegister(channel, stack)
            case registerWaitState: RegisterWaitState =>
                val future = registerWaitState.registerFuture
                if (future.isSuccess) {
                    afterAccepted(future.channel)
                    stack.`return`(UnitReply())
                } else {
                    stack.`throw`(ExceptionMessage(future.causeUnsafe))
                }
    }

    protected def afterAccepted(channel: ChannelAddress): Unit = {
        // default, do nothing
    }

}
