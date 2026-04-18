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

package cc.otavia.core.system

import cc.otavia.core.util.{Nextable, SpinLock}

import scala.language.unsafeNulls

class Mailbox(val house: ActorHouse) {

    private val lock = new SpinLock

    private var head: Nextable = _
    private var tail: Nextable = _

    @volatile private var count: Int = 0

    def put(obj: Nextable): Unit = {
        lock.lock()
        try {
            val oldTail = tail
            if (oldTail == null) {
                head = obj
                tail = obj
            } else {
                tail = obj
                oldTail.next = tail
            }
            count += 1
        } finally lock.unlock()
    }

    def putHead(obj: Nextable): Unit = {
        lock.lock()
        try {
            val old = head
            head = obj
            obj.next = old
            count += 1
        } finally lock.unlock()
    }

    def get[T <: Nextable](): T = {
        lock.lock()
        try {
            var obj: Nextable = null
            if (count == 1) {
                obj = head
                head = null
                tail = null
            } else {
                obj = head
                head = obj.next
                obj.deChain()
            }
            count -= 1

            obj.asInstanceOf[T]
        } finally lock.unlock()
    }

    def getChain(max: Int): Nextable = {
        lock.lock()
        try {
            var obj: Nextable = null
            if (count <= max) {
                obj = head
                head = null
                tail = null
                count = 0
            } else {
                obj = head
                var i      = 0
                var cursor = head
                while (i < max - 1) {
                    cursor = cursor.next
                    i += 1
                }
                val chainTail = cursor
                head = cursor.next
                chainTail.next = null
                count -= max
            }
            obj
        } finally lock.unlock()
    }

    def getAll: Nextable = {
        lock.lock()
        try {
            val obj = head
            head = null
            tail = null
            count = 0
            obj
        } finally lock.unlock()
    }

    def size(): Int = count

    def isEmpty: Boolean = count == 0

    def nonEmpty: Boolean = count > 0

    override def toString: String = s"size = $count"

}
