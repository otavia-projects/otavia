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

package io.otavia.core.stack

import io.otavia.core.message.{Ask, ExceptionMessage, Reply, ReplyOf}

import scala.language.unsafeNulls

class BatchAskStack[A <: Ask[? <: Reply]] extends Stack {

    private var msgs: Seq[Ask[?]] = _
    private var reply: Reply      = _

    override def recycle(): Unit = BatchAskStack.pool.recycle(this)

    private[core] def setAsks(asks: Seq[Ask[?]]): Unit = this.msgs = asks

    def asks: Seq[A] = msgs.asInstanceOf[Seq[A]]

    override protected def cleanInstance(): Unit = {
        super.cleanInstance()
        msgs = null
        reply = null
    }

    def `return`(ret: ReplyOf[A]): None.type = {
        ret.setMessageContext(runtimeActor)
        ret.setReplyId(asks.map(ask => (ask.senderId, ask.messageId)))
        reply = ret
        for (sender <- asks.map(_.sender).distinct) {
            sender.reply(reply, runtimeActor)
        }
        None
    }

    def `throw`(cause: ExceptionMessage): None.type = {
        cause.setReplyId(asks.map(ask => (ask.senderId, ask.messageId)))
        cause.setMessageContext(runtimeActor)
        reply = cause
        this.setFailed()
        for (sender <- asks.map(_.sender).distinct) {
            sender.reply(reply, runtimeActor)
        }
        None
    }

    def isDone: Boolean = reply != null

}

object BatchAskStack {

    private val pool = new StackObjectPool[BatchAskStack[?]] {
        override protected def newObject(): BatchAskStack[?] = new BatchAskStack[Nothing]
    }

    def apply[A <: Ask[? <: Reply]](): BatchAskStack[A] = pool.get().asInstanceOf[BatchAskStack[A]]

}
