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

import io.otavia.core.util.Chainable

/** An object which can be pooled */
trait Poolable extends Chainable

object Poolable {

    trait PoolableHolder[T <: Poolable] {

        def size: Int
        def maxSize: Int

        def pop(): T | Null

        def push(poolable: T): Unit

    }

    class SingleThreadPoolableHolder[T <: Poolable](val maxSize: Int = 1024) extends PoolableHolder[T] {

        private var count: Int        = 0
        private var head: T | Null    = null
        private var tail: T | Null    = null
        private var maxTail: T | Null = null

        override def size: Int = count

        override def pop(): T | Null = if (count > 1) {
            val poolable = head
            head = head.nn.next
            count -= 1
            poolable.nn
        } else if (count == 1) {
            val poolable = head
            head = null
            tail = null
            count -= 1
            poolable.nn
        } else null

        override def push(poolable: T): Unit = if (count == 0) {
            head = poolable
            tail = poolable
            count = 1
        } else {
            val oldHead = head
            head = poolable
            head.nn.next = oldHead
            count += 1
            if (count == maxSize) maxTail = tail
            if (count == maxSize + 1) {
                tail = tail.nn.pre
                maxTail.nn.pre = null
                maxTail = tail
                tail.nn.next = null
                count -= 1
            }
        }

    }

}
