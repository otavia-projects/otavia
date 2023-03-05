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

import io.otavia.core.message.{Ask, Notice}

class BatchNoticeStack[N <: Notice] private () extends Stack {

    private var msgs: Seq[Notice] = _
    private var done: Boolean     = false

    override def recycle(): Unit = BatchNoticeStack.pool.recycle(this)

    private[core] def setNotices(notices: Seq[Notice]): Unit = msgs = notices

    def notices: Seq[N] = msgs.asInstanceOf[Seq[N]]

    def `return`(): None.type = {
        done = true
        None
    }

    def isDone: Boolean = done

}

object BatchNoticeStack {

    private val pool: StackObjectPool[BatchNoticeStack[_]] = new StackObjectPool[BatchNoticeStack[?]] {
        override protected def newObject(): BatchNoticeStack[_] = new BatchNoticeStack[Nothing]()
    }

    def apply[N <: Notice](): BatchNoticeStack[N] = pool.get().asInstanceOf[BatchNoticeStack[N]]

}
