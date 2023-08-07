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

import cc.otavia.core.message.Notice
import cc.otavia.core.message.{Call, Notice}

import scala.language.unsafeNulls

class NoticeStack[N <: Notice] private () extends ActorStack {

    private var done: Boolean = false

    def notice: N = call.asInstanceOf[N]

    override def recycle(): Unit = NoticeStack.stackPool.recycle(this)

    override protected def cleanInstance(): Unit = {
        done = false
        super.cleanInstance()
    }

    /** Finsh this [[NoticeStack]] */
    def `return`(): None.type = {
        done = true
        None
    }

    override def isDone: Boolean = done

}

object NoticeStack {

    private val stackPool = new StackObjectPool[NoticeStack[? <: Notice]] {
        override protected def newObject(): NoticeStack[? <: Notice] = new NoticeStack[Nothing]()
    }

    def apply[N <: Notice](): NoticeStack[N] = stackPool.get().asInstanceOf[NoticeStack[N]]

}
