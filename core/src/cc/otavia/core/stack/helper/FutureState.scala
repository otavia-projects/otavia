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

import cc.otavia.core.cache.*
import cc.otavia.core.message.Reply
import cc.otavia.core.stack.{MessageFuture, StackState}

import scala.language.unsafeNulls

final class FutureState[R <: Reply] private () extends StackState with Poolable {

    private var stateId: Int         = 0
    private var fu: MessageFuture[R] = _

    def future: MessageFuture[R] = fu

    override def id: Int = stateId

    override def recycle(): Unit = FutureState.pool.recycle(this)

    override protected def cleanInstance(): Unit = {
        fu = null
        stateId = 0
    }

}

object FutureState {

    def apply[R <: Reply](stateId: Int): FutureState[R] = {
        val state = pool.get().asInstanceOf[FutureState[R]]
        state.stateId = stateId
        state.fu = MessageFuture()
        state
    }

    def apply[R <: Reply](): FutureState[R] = {
        val state = pool.get().asInstanceOf[FutureState[R]]
        state.fu = MessageFuture()
        state
    }

    private val pool = new ActorThreadIsolatedObjectPool[FutureState[?]] {

        override protected def newObject(): FutureState[?] = new FutureState()

    }

}
