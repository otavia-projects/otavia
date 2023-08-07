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

package cc.otavia.core.address

import cc.otavia.core.actor.AbstractActor
import cc.otavia.core.message.{Ask, Call, Event, Notice, Reply}
import cc.otavia.core.actor.{AbstractActor, Actor, ChannelsActor, StateActor}
import cc.otavia.core.message.*
import cc.otavia.core.slf4a.Appender
import cc.otavia.core.stack.ReplyFuture
import cc.otavia.core.system.{ActorHouse, House}
import cc.otavia.core.timer.TimeoutTrigger

/** every actor instance has one and only one physical address.
 *
 *  @tparam M
 *    the message type that this actor can receive.
 *  @tparam H
 *    actor house
 */
abstract class PhysicalAddress[M <: Call] extends Address[M] {

    private[core] val house: ActorHouse

    override def ask[A <: M & Ask[? <: Reply]](ask: A, future: ReplyFuture[ReplyOf[A]])(using
        sender: AbstractActor[?]
    ): ReplyFuture[ReplyOf[A]] = {
        ask.setMessageContext(sender)
        sender.attachStack(ask.askId, future)
        house.putAsk(ask)
        future
    }

    override def ask[A <: M & Ask[? <: Reply]](ask: A, future: ReplyFuture[ReplyOf[A]], timeout: Long)(using
        sender: AbstractActor[?]
    ): ReplyFuture[ReplyOf[A]] = {
        this.ask(ask, future)
        val promise = future.promise

        val id =
            sender.system.timer.registerAskTimeout(TimeoutTrigger.DelayTime(timeout), sender.self, ask.askId)

        promise.setTimeoutId(id)
        future
    }

    override def notice(notice: M & Notice): Unit = {
        house.putNotice(notice)
    }

    override private[core] def reply(reply: Reply, sender: AbstractActor[?]): Unit = {
        // reply.setMessageContext(sender)
        house.putReply(reply)
    }

    override private[core] def inform(event: Event): Unit = house.putEvent(event)

}
