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

package cc.otavia.core.cache

import cc.otavia.core.system.ActorThread
import cc.otavia.core.timer.TimeoutTrigger

abstract class TimeoutThreadIsolationObjectPool[T <: Poolable] extends ThreadIsolationObjectPool[T] {

    private val threadLocal = new ActorThreadLocal[SingleThreadPoolableHolder[T]] {

        override protected def initialValue(): SingleThreadPoolableHolder[T] =
            new SingleThreadPoolableHolder[T]()

        override protected def initialTimeoutTrigger: Option[TimeoutTrigger] =
            TimeoutThreadIsolationObjectPool.this.initialTimeoutTrigger

        override def handleTimeout(registerId: Long, resourceTimer: ResourceTimer): Unit = {
            val threadLocalTimer: ThreadLocalTimer = resourceTimer.asInstanceOf[ThreadLocalTimer]
            TimeoutThreadIsolationObjectPool.this.handleTimeout(registerId, threadLocalTimer)
        }

    }

    override protected def holder(): SingleThreadPoolableHolder[T] =
        if (ActorThread.currentThreadIsActorThread) threadLocal.get()
        else
            throw new IllegalStateException(
              "PerActorThreadObjectPool can not be used in thread which is not ActorThread, " +
                  "maby you can use PerThreadObjectPool"
            )

    protected def initialTimeoutTrigger: Option[TimeoutTrigger]

    protected def handleTimeout(registerId: Long, threadLocalTimer: ThreadLocalTimer): Unit

}
