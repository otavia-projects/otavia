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
import cc.otavia.core.cache.Poolable.SingleThreadPoolableHolder
import cc.otavia.core.system.ActorThread
import cc.otavia.core.timer.TimeoutTrigger

import java.util.concurrent.TimeUnit

abstract class PromiseObjectPool[P <: AbstractPromise[?]] extends ThreadIsolationObjectPool[P] {

    private val threadLocal = new ActorThreadLocal[SingleThreadPoolableHolder[P]] {

        override protected def initialValue(): Poolable.SingleThreadPoolableHolder[P] =
            new Poolable.SingleThreadPoolableHolder[P]()

        override protected def initialTimeoutTrigger: Option[TimeoutTrigger] =
            Some(TimeoutTrigger.DelayPeriod(60, 60, TimeUnit.SECONDS, TimeUnit.SECONDS))

        override def handleTimeout(registerId: Long, resourceTimer: ResourceTimer): Unit = {
            val threadLocalTimer: ThreadLocalTimer = resourceTimer.asInstanceOf[ThreadLocalTimer]
            if ((System.currentTimeMillis() - threadLocalTimer.recentlyGetTime) / 1000 > 30) {
                val holder = this.get()
                if (holder.size > 100) holder.clean(100)
            }
        }

    }

    override protected def holder(): Poolable.SingleThreadPoolableHolder[P] =
        if (ActorThread.currentThreadIsActorThread) threadLocal.get()
        else
            throw new IllegalStateException(
              "PerActorThreadObjectPool can not be used in thread which is not ActorThread, " +
                  "maby you can use PerThreadObjectPool"
            )

    override def dropIfRecycleNotByCreated: Boolean = false

}
