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

import cc.otavia.core.cache.AbstractThreadIsolatedObjectPool.ObjectPoolThreadLocal
import cc.otavia.core.system.ActorThread

import java.lang.ThreadLocal as JThreadLocal
import scala.language.unsafeNulls

abstract class ThreadIsolatedObjectPool[T <: Poolable]() extends AbstractThreadIsolatedObjectPool[T] {

    // use by ActorThread
    private val threadLocal = new ObjectPoolThreadLocal[T](this)

    // use by other Thread
    private val jdkThreadLocal = new JThreadLocal[SingleThreadPoolableHolder[T]] {
        override def initialValue(): SingleThreadPoolableHolder[T] =
            new SingleThreadPoolableHolder[T]()
    }

    override protected def holder(): SingleThreadPoolableHolder[T] =
        if (ActorThread.currentThreadIsActorThread) threadLocal.get() else jdkThreadLocal.get()

    override def dropIfRecycleNotByCreated: Boolean = false

}
