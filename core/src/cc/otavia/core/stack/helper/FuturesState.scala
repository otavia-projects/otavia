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

package cc.otavia.core.stack.helper

import cc.otavia.core.cache.{ActorThreadIsolatedObjectPool, Poolable}
import cc.otavia.core.message.Reply
import cc.otavia.core.stack.{MessageFuture, StackState}

import scala.language.unsafeNulls

class FuturesState[R <: Reply] private () extends StackState with Poolable {

    private var stateId: Int               = 0
    private var fus: Seq[MessageFuture[R]] = _

    def futures: Seq[MessageFuture[R]] = fus

    override def id: Int = stateId

    override def recycle(): Unit = FuturesState.pool.recycle(this)

    override protected def cleanInstance(): Unit = {
        fus = null
        stateId = 0
    }

}

object FuturesState {

    def apply[R <: Reply](length: Int, stateId: Int = 0): FuturesState[R] = {
        val state = pool.get().asInstanceOf[FuturesState[R]]
        state.stateId = stateId

        state.fus = (0 until length).map { i => MessageFuture() }

        state
    }

    private val pool = new ActorThreadIsolatedObjectPool[FuturesState[?]] {

        override protected def newObject(): FuturesState[?] = new FuturesState()

    }

}
