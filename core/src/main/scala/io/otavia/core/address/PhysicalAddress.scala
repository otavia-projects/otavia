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

package io.otavia.core.address

import io.otavia.core.actor.{Actor, ChannelsActor, NormalActor}
import io.otavia.core.house.House
import io.otavia.core.message.*
import io.otavia.core.stack.ReplyWaiter
import io.otavia.core.util.Logger

/** every actor instance has one and only one physical address.
 *
 *  @tparam M
 *    the message type that this actor can receive.
 *  @tparam H
 *    actor house
 */
trait PhysicalAddress[M <: Ask[?] | Notice, H <: House] extends Address[M] {
    private[core] val house: H

    protected def check(msg: Message)(using sender: Actor[?]): Unit =
        assert(msg.senderId == sender.actorId, "")

    override def ask[A <: M & Ask[?]](ask: A, waiter: ReplyWaiter[ReplyOf[A]])(using sender: Actor[?]): Unit = {
        check(ask)
        sender match
            case actor: NormalActor[_]   => actor.attachFrame(ask.id, waiter)
            case group: ChannelsActor[_] => ???
        house.putAsk(ask)
    }

    override def notice(notice: M & Notice): Unit = house.putNotice(notice)

    override private[core] def reply(reply: Reply): Unit = {
        house.putReply(reply)
    }

}
