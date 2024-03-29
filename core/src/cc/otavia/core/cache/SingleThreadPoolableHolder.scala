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

import cc.otavia.core.system.ActorSystem
import cc.otavia.core.util.Nextable

import scala.language.unsafeNulls

final class SingleThreadPoolableHolder[T <: Poolable](val maxSize: Int = ActorSystem.DEFAULT_POOL_HOLDER_MAX_SIZE)
    extends PoolableHolder[T] {

    private var count: Int      = 0
    private var head: Nextable = _
    private var tail: Nextable = _

    override def size: Int = count

    override def pop(): T | Null = if (count > 1) {
        val poolable = head
        head = poolable.next
        count -= 1
        poolable.deChain()
        poolable.asInstanceOf[T]
    } else if (count == 1) {
        val poolable = head
        tail = null
        head = null
        count -= 1
        poolable.asInstanceOf[T]
    } else null

    override def push(poolable: T): Unit = if (count == 0) {
        head = poolable
        tail = poolable
        count = 1
    } else {
        if (count != maxSize) {
            val oldHead = head
            poolable.next = oldHead
            head = poolable
            count += 1
        }
    }

    /** Clear this [[PoolableHolder]] until there are remain keep's objects.
     *
     *  @param keep
     *    the number of object to remain.
     */
    def clean(keep: Int = 0): Unit = if (count > keep) {
        if (keep == 0) {
            head = null
            tail = null
            count = 0
        } else {
            tail = head
            var c = keep - 1
            while (c > 0) {
                tail = tail.next
                c -= 1
            }
            tail.cleanNext()
            count = keep
        }
    }

}
