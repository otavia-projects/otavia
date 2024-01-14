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
import cc.otavia.core.system.{ActorSystem, ActorThread}

class RobinAddress[M <: Call](val underlying: Array[Address[M]]) extends ProxyAddress[M] {

    private var noticeCursor: Int = 0
    private var askCursor: Int    = 0

    override def notice(notice: M & Notice): Unit = {
        val index = noticeCursor % underlying.length
        noticeCursor += 1
        underlying(index).notice(notice)
    }

    override def ask[A <: M & Ask[_ <: Reply]](ask: A, future: MessageFuture[ReplyOf[A]])(using
        sender: AbstractActor[? <: Call]
    ): MessageFuture[ReplyOf[A]] = {
        val address = getAddress
        address.ask(ask, future)
    }

    override def askUnsafe(ask: Ask[?], f: MessageFuture[?])(using sender: AbstractActor[?]): MessageFuture[?] = {
        val addr = getAddress
        addr.askUnsafe(ask, f)
    }

    override def ask[A <: M & Ask[_ <: Reply]](ask: A, f: MessageFuture[ReplyOf[A]], timeout: Long)(using
        sender: AbstractActor[? <: Call]
    ): MessageFuture[ReplyOf[A]] = {
        val address = getAddress
        address.ask(ask, f, timeout)
    }

    final private def getAddress: Address[M] = if (underlying.length == ActorSystem.global.actorWorkerSize) {
        underlying(ActorThread.currentThread().index)
    } else {
        val index = askCursor % underlying.length
        askCursor += 1
        underlying(index)
    }

    override private[core] def reply(reply: Reply, replyId: Long, sender: AbstractActor[?]): Unit =
        throw new UnsupportedOperationException()

    override private[core] def reply(reply: Reply, replyIds: Array[Long], sender: AbstractActor[?]): Unit =
        throw new UnsupportedOperationException()

    override private[core] def `throw`(cause: ExceptionMessage, replyId: Long, sender: AbstractActor[?]): Unit =
        throw new UnsupportedOperationException()

    override private[core] def `throw`(cause: ExceptionMessage, replyIds: Array[Long], sender: AbstractActor[?]): Unit =
        throw new UnsupportedOperationException()

}
