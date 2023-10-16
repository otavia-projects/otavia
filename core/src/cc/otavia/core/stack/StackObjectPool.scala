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
import cc.otavia.core.system.ActorThread
import cc.otavia.core.timer.TimeoutTrigger

import java.util.concurrent.TimeUnit

/** A object pool for [[Stack]], This object pool is not visible to the user.
 *  @tparam S
 *    type of [[Stack]]
 */
private[core] abstract class StackObjectPool[S <: Stack] extends ThreadIsolationObjectPool[S] {

    private val threadLocal = new ActorThreadLocal[Poolable.SingleThreadPoolableHolder[S]] {

        override protected def initialValue(): Poolable.SingleThreadPoolableHolder[S] =
            new Poolable.SingleThreadPoolableHolder[S]()

        override protected def initialTimeoutTrigger: Option[TimeoutTrigger] =
            Some(TimeoutTrigger.DelayPeriod(60, 60, TimeUnit.SECONDS, TimeUnit.SECONDS))

        override def handleTimeout(registerId: Long, resourceTimer: ResourceTimer): Unit = {
            val threadLocalTimer: ThreadLocalTimer = resourceTimer.asInstanceOf[ThreadLocalTimer]
            val duration                           = System.currentTimeMillis() - threadLocalTimer.recentlyGetTime
            if (duration / 1000 > 30) {
                val holder = this.get()
                if (holder.size > 10) holder.clean(10)
            }
        }

    }

    override protected def holder(): Poolable.SingleThreadPoolableHolder[S] =
        if (ActorThread.currentThreadIsActorThread) threadLocal.get()
        else
            throw new IllegalStateException(
              "PerActorThreadObjectPool can not be used in thread which is not ActorThread, " +
                  "maby you can use PerThreadObjectPool"
            )

    override def dropIfRecycleNotByCreated: Boolean = false

}
