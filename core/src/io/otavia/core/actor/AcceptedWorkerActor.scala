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

import io.otavia.core.actor.AccepterActor.AcceptedChannel
import io.otavia.core.message.{Ask, ExceptionMessage, Notice, UnitReply}
import io.otavia.core.reactor.RegisterReplyEvent
import io.otavia.core.stack.StackState

import scala.reflect.ClassTag

abstract class AcceptedWorkerActor[M <: Ask[?] | Notice] extends ChannelsActor[M | AcceptedChannel] {
    private val registering = collection.mutable.HashMap.empty[Long, AcceptedChannel]
    override private[core] def receiveAsk(ask: Ask[_]): Unit = {
        if (!ask.isInstanceOf[AcceptedChannel]) super.receiveAsk(ask)
        else {
            val accepted = ask.asInstanceOf[AcceptedChannel]
            init(accepted.channel)
            system.reactor.register(accepted.channel)

            registering.put(accepted.channel.id, accepted)
        }
    }

    private[core] override def receiveRegisterReply(registerReplyEvent: RegisterReplyEvent): Unit = {
        val accepted = registering.remove(registerReplyEvent.channel.id).get
        if (registerReplyEvent.succeed) {
            accepted.reply(UnitReply())
            registerReplyEvent.channel.pipeline.fireChannelRegistered()
            registerReplyEvent.channel.pipeline.fireChannelActive()
        } else {
            accepted.channel.close()
            accepted.throws(ExceptionMessage(registerReplyEvent.cause))
        }

    }

}
