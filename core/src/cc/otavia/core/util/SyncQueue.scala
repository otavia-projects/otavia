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

final class SyncQueue[T <: Nextable] {

    private var size = 0

    private var head: T | Null = _
    private var tail: T | Null = _

    def isEmpty: Boolean = this.synchronized(size == 0)

    def nonEmpty: Boolean = this.synchronized(size > 0)

    def length: Int = this.synchronized(size)

    def enqueue(instance: T): Unit = this.synchronized {
        if (size > 0) {
            tail.next = instance
            tail = instance
        } else {
            head = instance
            tail = instance
        }
        size += 1
    }

    def dequeue(): T | Null = this.synchronized {
        var value: T | Null = null
        if (size == 1) {
            value = head
            head = null
            tail = null
            size = 0
        } else if (size > 1) {
            value = head
            head = value.next.asInstanceOf[T]
            value.dechain()
            size -= 1
        }
        value
    }

}
