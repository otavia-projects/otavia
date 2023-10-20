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

abstract class AbstractThreadIsolatedObjectPool[T <: Poolable] extends ObjectPool[T] {

    protected def holder(): SingleThreadPoolableHolder[T]

    def dropIfRecycleNotByCreated: Boolean

    override def get(): T = {
        val h   = holder()
        val pop = h.pop()
        if (pop != null) pop.asInstanceOf[T] else newInstance()
    }

    override def recycle(poolable: T): Unit = {
        poolable.clean()
        if (dropIfRecycleNotByCreated) {
            if (poolable.creator == Thread.currentThread()) holder().push(poolable) else {}
        } else holder().push(poolable)
    }

    protected val timeoutTrigger: Option[TimeoutTrigger]

    protected def handleTimeout(registerId: Long, threadLocalTimer: ThreadLocalTimer): Unit

}

object AbstractThreadIsolatedObjectPool {

    class ObjectPoolThreadLocal[T <: Poolable](val parent: AbstractThreadIsolatedObjectPool[T])
        extends ActorThreadLocal[SingleThreadPoolableHolder[T]] {

        override protected def initialValue(): SingleThreadPoolableHolder[T] =
            new SingleThreadPoolableHolder[T]()

        override protected final def initialTimeoutTrigger: Option[TimeoutTrigger] = parent.timeoutTrigger

        override def handleTimeout(registerId: Long, resourceTimer: ResourceTimer): Unit = {
            val threadLocalTimer: ThreadLocalTimer = resourceTimer.asInstanceOf[ThreadLocalTimer]
            parent.handleTimeout(registerId, threadLocalTimer)
        }

    }

}
