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

import java.util.concurrent.atomic.AtomicInteger
import scala.language.unsafeNulls

// MPSC
class FIFOHouseQueue(manager: HouseManager) extends HouseQueue(manager) {

    private val readLock                   = new SpinLock()
    private val writeLock                  = new SpinLock()
    private val size                       = new AtomicInteger(0)
    @volatile private var head: ActorHouse = _
    @volatile private var tail: ActorHouse = _

    override def available: Boolean = size.get() > 0

    override def readies: Int = size.get()

    override def isEmpty: Boolean = size.get() == 0

    override def nonEmpty: Boolean = size.get() > 0

    override def enqueue(house: ActorHouse): Unit = {
        writeLock.lock()
        if (size.get() == 0) {
            readLock.lock()
            head = house
            tail = house
            size.incrementAndGet()
            writeLock.unlock()
            readLock.unlock()
        } else {
            tail.next = house
            tail = house
            size.incrementAndGet()
            writeLock.unlock()
        }
    }

    final override def dequeue(): ActorHouse | Null = {
        if (size.get() == 0) null
        else dequeue0()
    }

    final private def dequeue0(): ActorHouse | Null = {
        if (size.get() == 0) {
            null
        } else if (size.get() == 1) {
            writeLock.lock()
            readLock.lock()
            if (size.get() == 1) {
                val house = head
                head = null
                tail = null
                size.decrementAndGet()
                house.schedule()
                writeLock.unlock()
                readLock.unlock()
                house
            } else { // size.get() > 1
                writeLock.unlock()
                dequeue00()
            }
        } else {
            readLock.lock()
            dequeue00()
        }
    }

    final private inline def dequeue00(): ActorHouse = {
        val house = head
        head = house.next.asInstanceOf[ActorHouse]
        size.decrementAndGet()
        house.schedule()
        readLock.unlock()
        house.deChain()
        house
    }

}
