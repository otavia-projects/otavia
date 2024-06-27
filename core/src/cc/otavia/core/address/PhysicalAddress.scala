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
import cc.otavia.core.message.*
import cc.otavia.core.stack.MessageFuture
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

    private def packaging[T <: Message](message: T, sender: AbstractActor[?]): Envelope[T] = {
        val envelope = Envelope[T]()
        envelope.setSender(sender.self.asInstanceOf[Address[Call]])
        envelope.setMessageId(sender.generateSendMessageId())
        envelope.setContent(message)
        envelope
    }

    override def ask[A <: M & Ask[? <: Reply]](ask: A, future: MessageFuture[ReplyOf[A]])(using
        sender: AbstractActor[?]
    ): MessageFuture[ReplyOf[A]] = {
        val envelope = packaging(ask, sender)
        sender.attachStack(envelope.messageId, future)
        house.putAsk(envelope)
        future
    }

    override def askUnsafe(ask: Ask[?], future: MessageFuture[?])(using sender: AbstractActor[?]): MessageFuture[?] = {
        val envelope = packaging(ask, sender)
        sender.attachStack(envelope.messageId, future)
        house.putAsk(envelope)
        future
    }

    override def ask[A <: M & Ask[? <: Reply]](ask: A, future: MessageFuture[ReplyOf[A]], timeout: Long)(using
        sender: AbstractActor[?]
    ): MessageFuture[ReplyOf[A]] = {
        this.ask(ask, future)
        val promise = future.promise

        val id = sender.timer.registerAskTimeout(TimeoutTrigger.DelayTime(timeout), sender.self, promise.id)

        promise.setTimeoutId(id)
        future
    }

    override def notice(notice: M & Notice): Unit = {
        val envelope = Envelope[Notice]()
        envelope.setContent(notice)
        house.putNotice(envelope)
    }

    override private[core] def reply(reply: Reply, replyId: Long, sender: AbstractActor[?]): Unit = {
        val envelope = packaging(reply, sender)
        envelope.setReplyId(replyId)
        house.putReply(envelope)
    }

    override private[core] def reply(reply: Reply, replyIds: Array[Long], sender: AbstractActor[?]): Unit = {
        val envelope = packaging(reply, sender)
        envelope.setReplyIds(replyIds)
        house.putReply(envelope)
    }

    override private[core] def `throw`(cause: ExceptionMessage, replyId: Long, sender: AbstractActor[?]): Unit = {
        val envelope = packaging(cause, sender)
        envelope.setReplyId(replyId)
        house.putException(envelope)
    }

    override private[core] def `throw`(cause: ExceptionMessage, ids: Array[Long], sender: AbstractActor[?]): Unit = {
        val envelope = packaging(cause, sender)
        envelope.setReplyIds(ids)
        house.putException(envelope)
    }

    override private[core] def inform(event: Event): Unit = house.putEvent(event)

}
