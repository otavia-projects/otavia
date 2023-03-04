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

package io.otavia.core.cache

import io.netty5.util.concurrent.FastThreadLocal
import io.otavia.core.system.ActorThread

abstract class PerThreadObjectPool[T <: Poolable](override val dropIfRecycleNotByCreated: Boolean = false)
    extends ThreadIsolationObjectPool[T] {

    // use by ActorThread
    private val threadLocal = new ActorThreadLocal[Poolable.SingleThreadPoolableHolder[T]] {
        override protected def initialValue(): Poolable.SingleThreadPoolableHolder[T] =
            new Poolable.SingleThreadPoolableHolder[T]()
    }

    // use by other Thread
    private lazy val fastThreadLocal = new FastThreadLocal[Poolable.SingleThreadPoolableHolder[T]] {
        override def initialValue(): Poolable.SingleThreadPoolableHolder[T] =
            new Poolable.SingleThreadPoolableHolder[T]()
    }

    override protected def holder(): Poolable.SingleThreadPoolableHolder[T] =
        if (ActorThread.currentThreadIsActorThread) threadLocal.get() else fastThreadLocal.get().nn

}
