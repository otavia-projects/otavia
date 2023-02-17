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

import io.otavia.core.system.{ActorSystem, ActorThreadPool}
import io.otavia.core.util.Chainable

/** An object which can be pooled */
trait Poolable extends Chainable {

    private var index: Int = ActorThreadPool.INVALID_THREAD_ID

    private[core] def creator: Int           = index
    private[core] def creator(id: Int): Unit = index = id

    final def recycle(pool: ObjectPool[this.type]): Unit = {
        cleanInstance()
        dechain()
        pool.recycle(this)
    }

    def recycle(): Unit

    protected def cleanInstance(): Unit

}

object Poolable {

    trait PoolableHolder[T <: Poolable] {

        def size: Int
        def maxSize: Int

        def pop(): T | Null

        def push(poolable: T): Unit

    }

    class SingleThreadPoolableHolder[T <: Poolable](val maxSize: Int = ActorSystem.DEFAULT_POOL_HOLDER_MAX_SIZE)
        extends PoolableHolder[T] {

        import scala.language.unsafeNulls

        private var count: Int             = 0
        private var head: Chainable | Null = null
        private var tail: Chainable | Null = null

        inline private def headnn: Chainable = head.asInstanceOf[Chainable]
        inline private def tailnn: Chainable = tail.asInstanceOf[Chainable]

        override def size: Int = count

        override def pop(): T | Null = if (count > 1) {
            val poolable = headnn
            head = poolable.next
            count -= 1
            poolable.dechain()
            poolable.asInstanceOf[T]
        } else if (count == 1) {
            val poolable = headnn
            head = null
            tail = null
            count -= 1
            poolable.asInstanceOf[T]
        } else null

        override def push(poolable: T): Unit = if (count == 0) {
            head = poolable
            tail = poolable
            count = 1
        } else {
            val oldHead = headnn
            poolable.next = oldHead
            head = poolable
            count += 1
            if (count == maxSize + 1) {
                val oldTail = tailnn
                tail = oldTail.pre
                oldTail.dechain()
                tailnn.cleanNext()
                count -= 1
            }
        }

    }

}
