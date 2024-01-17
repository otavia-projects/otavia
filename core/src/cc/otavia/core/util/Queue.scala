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

package cc.otavia.core.util

import scala.language.unsafeNulls

class Queue[T <: Nextable] {

    private var size = 0

    @volatile private var head: T | Null = _
    @volatile private var tail: T | Null = _

    def isEmpty: Boolean = size == 0

    def nonEmpty: Boolean = size > 0

    def length: Int = size

    final def enqueue(value: T): Unit = if (size == 0) {
        head = value
        tail = value
        size += 1
    } else {
        tail.next = value
        size += 1
    }

    final def dequeue(): T | Null = if (size == 0) null
    else if (size == 1) {
        val value = head
        head = null
        tail = null
        size -= 1
        value
    } else {
        val value = head
        head = value.next.asInstanceOf[T]
        value.deChain()
        size -= 1
        value
    }

}
