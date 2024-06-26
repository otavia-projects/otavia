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
import cc.otavia.core.cache.ActorThreadIsolatedObjectPool
import cc.otavia.core.message.{Call, Notice}

import scala.language.unsafeNulls

final class NoticeStack[N <: Notice] private () extends Stack {

    private var done: Boolean = false
    private var msg: Call     = _

    private[core] def setNotice(c: Call): Unit = msg = c

    def notice: N = msg.asInstanceOf[N]

    override def recycle(): Unit = NoticeStack.stackPool.recycle(this)

    override protected def cleanInstance(): Unit = {
        msg = null
        done = false
        super.cleanInstance()
    }

    /** Finish this [[NoticeStack]] */
    def `return`(): StackYield = {
        done = true
        StackYield.RETURN
    }

    def isDone: Boolean = done

}

object NoticeStack {

    private val stackPool = new ActorThreadIsolatedObjectPool[NoticeStack[? <: Notice]] {
        override protected def newObject(): NoticeStack[? <: Notice] = new NoticeStack[Nothing]()
    }

    private[core] def apply[N <: Notice](actor: AbstractActor[?]): NoticeStack[N] = {
        val stack = stackPool.get().asInstanceOf[NoticeStack[N]]
        stack.setRuntimeActor(actor)
        stack
    }

}
