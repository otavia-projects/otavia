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

import io.otavia.core.system.ActorThread

abstract class PerActorThreadObjectPool[T <: Poolable] extends ObjectPool[T] {

    private val threadLocal = new ActorThreadLocal[Poolable.SingleThreadPoolableHolder] {
        override protected def initialValue(): Poolable.SingleThreadPoolableHolder =
            new Poolable.SingleThreadPoolableHolder[T]()()
    }

    private def holder(): Poolable.SingleThreadPoolableHolder = threadLocal.get()

    override def get(): T = {
        holder().pop() match
            case null: Null => newObject()
            case obj: T     => obj
    }

    override def recycle(poolable: T): Unit = holder().push(poolable)

}
