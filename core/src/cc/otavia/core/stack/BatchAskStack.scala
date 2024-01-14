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

    private var envelopes: Seq[Envelope[Ask[?]]] = _
    private var done: Boolean                    = false

    private[core] def setAsks(envelopes: Seq[Envelope[Ask[?]]]): Unit = this.envelopes = envelopes

    def asks: Seq[Envelope[A]] = envelopes.asInstanceOf[Seq[Envelope[A]]]

    def `return`(ret: ReplyOf[A]): None.type = {
        val none = end(ret)
        done = true
        none
    }

    def `return`(rets: Seq[(Envelope[A], ReplyOf[A])]): None.type = {
        done = true
        for ((envelope, ret) <- rets) envelope.sender.reply(ret, envelope.messageId, runtimeActor)
        None
    }

    def `throw`(cause: ExceptionMessage): None.type = {
        val none = end(cause, true)
        done = true
        none
    }

    private def end(ret: Reply, exception: Boolean = false): None.type = {
        for ((sender, envs) <- envelopes.groupBy(_.sender)) {
            if (envs.length > 1) {
                val replyIds = envs.map(_.messageId).toArray
                if (exception) sender.`throw`(ret.asInstanceOf[ExceptionMessage], replyIds, runtimeActor)
                else sender.reply(ret, replyIds, runtimeActor)
            } else {
                val replyId = envs.head.messageId
                if (exception) sender.`throw`(ret.asInstanceOf[ExceptionMessage], replyId, runtimeActor)
                else sender.reply(ret, replyId, runtimeActor)
            }
        }
        None
    }

    def isDone: Boolean = done

    override def recycle(): Unit = BatchAskStack.pool.recycle(this)

    override protected def cleanInstance(): Unit = {
        envelopes = null
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
