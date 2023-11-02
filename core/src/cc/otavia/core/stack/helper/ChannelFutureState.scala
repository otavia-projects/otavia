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

import cc.otavia.core.cache.Poolable
import cc.otavia.core.stack.{ChannelFuture, MessageFuture, StackState, StackStatePool}

import scala.language.unsafeNulls

final class ChannelFutureState private () extends StackState with Poolable {

    private var stateId: Int      = 0
    private var fu: ChannelFuture = _

    def future: ChannelFuture = fu

    override def id: Int = stateId

    override def recycle(): Unit = ChannelFutureState.pool.recycle(this)

    override protected def cleanInstance(): Unit = {
        stateId = 0
        fu = null
    }

}

object ChannelFutureState {

    def apply(): ChannelFutureState = {
        val state = pool.get()
        state.fu = ChannelFuture()
        state
    }

    def apply(stateId: Int): ChannelFutureState = {
        val state = pool.get()
        state.stateId = stateId
        state.fu = ChannelFuture()
        state
    }

    private val pool = new StackStatePool[ChannelFutureState] {
        override protected def newObject(): ChannelFutureState = new ChannelFutureState()
    }

}
