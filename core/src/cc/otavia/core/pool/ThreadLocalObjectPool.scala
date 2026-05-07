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

package cc.otavia.core.pool

import cc.otavia.core.pool.ThreadLocalObjectPool.*
import cc.otavia.core.timer.TimeoutTrigger

import java.util.concurrent.TimeUnit

abstract class ThreadLocalObjectPool[T <: Poolable] extends ObjectPool[T] {

    private val threadLocal = new PoolHolderThreadLocal[T](this)

    protected def holder(): PerThreadPool[T] = threadLocal.get()

    override def get(): T = {
        val h   = holder()
        val pop = h.pop()
        if (pop != null) pop.asInstanceOf[T] else create()
    }

    override def recycle(poolable: T): Unit = {
        poolable.clean()
        if (poolable.creatorThread == Thread.currentThread()) holder().push(poolable)
    }

    protected val timeoutTrigger: Option[TimeoutTrigger] =
        Some(
          TimeoutTrigger.DelayPeriod(
            TimeoutInitialDelaySec,
            TimeoutCheckIntervalSec,
            TimeUnit.SECONDS,
            TimeUnit.SECONDS
          )
        )

    protected def handleTimeout(registerId: Long, threadLocalTimer: ThreadLocalTimer): Unit = {
        val duration = System.currentTimeMillis() - threadLocalTimer.lastGetTime
        if (duration / 1000 > MaxIdleSec) {
            val holder = this.holder()
            if (holder.size > MinRetainCount) holder.clean(MinRetainCount)
        }
    }

}

object ThreadLocalObjectPool {

    private val TimeoutCheckIntervalSec = 60
    private val TimeoutInitialDelaySec  = 60
    private val MaxIdleSec              = 30
    private val MinRetainCount          = 10

    private[pool] class PoolHolderThreadLocal[T <: Poolable](parent: ThreadLocalObjectPool[T])
        extends ActorThreadLocal[PerThreadPool[T]] {

        override protected def initialValue(): PerThreadPool[T] =
            new PerThreadPool[T]()

        override protected final def initialTimeoutTrigger: Option[TimeoutTrigger] = parent.timeoutTrigger

        override def handleTimeout(registerId: Long, resourceTimer: ResourceTimer): Unit = {
            val threadLocalTimer: ThreadLocalTimer = resourceTimer.asInstanceOf[ThreadLocalTimer]
            parent.handleTimeout(registerId, threadLocalTimer)
        }

    }

}
