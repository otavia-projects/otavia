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

package cc.otavia.core.stack

import cc.otavia.core.actor.AbstractActor
import cc.otavia.core.address.Address
import cc.otavia.core.message.*
import cc.otavia.core.system.ActorThread

import scala.language.unsafeNulls

final class BatchAskStack[A <: Ask[? <: Reply]] extends Stack {

    private var messages: Seq[Ask[?]] = _
    private var reply: Reply          = _
    private var done: Boolean         = false

    private[core] def setAsks(asks: Seq[Ask[?]]): Unit = this.messages = asks

    def asks: Seq[A] = messages.asInstanceOf[Seq[A]]

    def `return`(ret: ReplyOf[A]): None.type = {
        reply = ret
        val none = end(ret)
        done = true
        none
    }

    def `return`(rets: Seq[ReplyOf[A]]): None.type = {
        ???
    }

    def `throw`(cause: ExceptionMessage): None.type = {
        reply = cause
        this.setFailed()
        val none = end(cause)
        done = true
        none
    }

    private def end(ret: Reply): None.type = {
        ret.setReplyId(asks.map(ask => (ask.senderId, ask.askId)))
        val set = ActorThread.threadSet[Address[Call]]
        for (elem <- asks) set.addOne(elem.sender)
        if (isFailed) for (sender <- set) sender.`throw`(reply.asInstanceOf[ExceptionMessage], runtimeActor)
        else
            for (sender <- set) sender.reply(reply, runtimeActor)
        set.clear()
        None
    }

    def isDone: Boolean = done

    override def recycle(): Unit = BatchAskStack.pool.recycle(this)

    override protected def cleanInstance(): Unit = {
        messages = null
        reply = null
        done = false
        super.cleanInstance()
    }

}

object BatchAskStack {

    private val pool = new StackPool[BatchAskStack[?]] {
        override protected def newObject(): BatchAskStack[?] = new BatchAskStack[Nothing]
    }

    def apply[A <: Ask[? <: Reply]](actor: AbstractActor[?]): BatchAskStack[A] = {
        val stack = pool.get().asInstanceOf[BatchAskStack[A]]
        stack.setRuntimeActor(actor)
        stack
    }

}
