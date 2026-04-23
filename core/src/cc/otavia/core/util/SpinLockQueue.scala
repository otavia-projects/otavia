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

import cc.otavia.core.config.SpinLockConfig
import java.util.concurrent.atomic.AtomicInteger
import scala.language.unsafeNulls

class SpinLockQueue[T <: Nextable](config: SpinLockConfig = SpinLockConfig()) {

    private val lock = new SpinLock(config)
    private val size = new AtomicInteger(0)

    @volatile private var head: T | Null = _
    @volatile private var tail: T | Null = _

    def isEmpty: Boolean = size.get() == 0

    def nonEmpty: Boolean = size.get() > 0

    def length: Int = size.get()

    def enqueue(instance: T): Unit = {
        lock.lock()
        if (size.get() == 0) {
            head = instance
            tail = instance
        } else {
            tail.next = instance
            tail = instance
        }
        size.incrementAndGet()
        lock.unlock()
    }

    final def dequeue(): T | Null = {
        if (size.get() == 0) null
        else {
            lock.lock()
            if (size.get() == 0) {
                lock.unlock()
                null
            } else {
                val value = head
                head = value.next.asInstanceOf[T]
                if (size.get() == 1) tail = null
                size.decrementAndGet()
                lock.unlock()
                value.unlink()
                value.asInstanceOf[T]
            }
        }
    }

}
