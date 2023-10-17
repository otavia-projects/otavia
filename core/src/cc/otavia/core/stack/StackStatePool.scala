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

import cc.otavia.core.cache.*
import cc.otavia.core.system.ActorSystem
import cc.otavia.core.timer.TimeoutTrigger

import java.util.concurrent.TimeUnit

/** A object pool for [[StackState]].
 *  @tparam S
 *    type of [[StackState]]
 */
abstract class StackStatePool[S <: StackState & Poolable] extends ActorThreadIsolatedObjectPool[S] {

    override def dropIfRecycleNotByCreated: Boolean = true

    override protected val timeoutTrigger: Option[TimeoutTrigger] =
        Some(TimeoutTrigger.DelayPeriod(60, 60, TimeUnit.SECONDS, TimeUnit.SECONDS))

    override protected def handleTimeout(registerId: Long, threadLocalTimer: ThreadLocalTimer): Unit = {
        val duration = System.currentTimeMillis() - threadLocalTimer.recentlyGetTime
        if (duration / 1000 > 60) {
            val holder = this.holder()
            if (holder.size > 10) holder.clean(10)
        }
    }

}
