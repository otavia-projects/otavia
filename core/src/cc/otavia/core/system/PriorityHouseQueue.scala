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

class PriorityHouseQueue(manager: HouseManager) extends HouseQueue(manager) {

    // for normal priority actor house
    private val readLock                   = new SpinLock()
    private val writeLock                  = new SpinLock()
    private val size                       = new AtomicInteger(0)
    @volatile private var head: ActorHouse = _
    @volatile private var tail: ActorHouse = _

    // for high priority actor house
    private val highReadLock                   = new SpinLock()
    private val highWriteLock                  = new SpinLock()
    private val highSize                       = new AtomicInteger(0)
    @volatile private var highHead: ActorHouse = _
    @volatile private var highTail: ActorHouse = _

    def available: Boolean = (size.get() > 0) || (highSize.get() > 0)

    def readies: Int = highSize.get() + size.get()

    override def isEmpty: Boolean = (highSize.get() == 0) && (size.get() == 0)

    override def nonEmpty: Boolean = (size.get() > 0) || (highSize.get() > 0)

    override def enqueue(house: ActorHouse): Unit = {
        if (!house.highPriority) {
            writeLock.lock()
            if (size.get() == 0) {
                head = house
                tail = house
                size.incrementAndGet()
            } else {
                val oldTail = tail
                tail = house
                oldTail.next = tail
                tail.pre = oldTail
                size.incrementAndGet()
            }
            house.inHighPriorityQueue = false
            writeLock.unlock()
        } else {
            highWriteLock.lock()
            if (highSize.get() == 0) {
                highHead = house
                highTail = house
                highSize.incrementAndGet()
            } else {
                val oldTail = highTail
                highTail = house
                oldTail.next = highTail
                highSize.incrementAndGet()
            }
            house.inHighPriorityQueue = true
            highWriteLock.unlock()
        }
    }

    override def dequeue(): ActorHouse | Null = {
        if (highSize.get() > 0) dequeuePriority()
        else if (size.get() > 0) dequeueNormal()
        else null
    }

    private def dequeueNormal(): ActorHouse | Null = {
        readLock.lock()
        val sizeShadow = size.get()
        if (sizeShadow == 0) {
            readLock.unlock()
            null
        } else if (sizeShadow == 1) {
            writeLock.lock()
            if (size.get() == 1) {
                val house = head
                head = null
                tail = null
                size.decrementAndGet()
                house.deChain()
                house.schedule()
                writeLock.unlock()
                readLock.unlock()
                house
            } else {
                val house = head
                head = house.next
                head.pre = null
                size.decrementAndGet()
                house.deChain()
                house.schedule()
                writeLock.unlock()
                readLock.unlock()
                house
            }
        } else {
            val house = head
            head = house.next
            head.pre = null
            size.decrementAndGet()
            house.deChain()
            house.schedule()
            readLock.unlock()
            house
        }
    }

    private def dequeuePriority(): ActorHouse | Null = {
        highReadLock.lock()
        val sizeShadow = highSize.get()
        if (sizeShadow == 0) {
            highReadLock.unlock()
            null
        } else if (sizeShadow == 1) {
            highWriteLock.lock()
            if (highSize.get() == 1) {
                val house = highHead
                highHead = null
                highTail = null
                highSize.decrementAndGet()
                house.deChain()
                house.schedule()
                highWriteLock.unlock()
                highReadLock.unlock()
                house
            } else {
                val house = highHead
                highHead = house.next
                highSize.decrementAndGet()
                house.deChain()
                house.schedule()
                highWriteLock.unlock()
                highReadLock.unlock()
                house
            }
        } else {
            val house = highHead
            highHead = house.next
            highSize.decrementAndGet()
            house.deChain()
            house.schedule()
            highReadLock.unlock()
            house
        }
    }

    def adjustPriority(house: ActorHouse): Unit = {
        readLock.lock()
        writeLock.lock()
        if (house.isReady && !house.inHighPriorityQueue) {
            val pre  = house.pre
            val next = house.next
            if (pre != null) {
                pre.next = next
                if (next != null) next.pre = pre else tail = pre
            } else {
                if (next != null) {
                    next.pre = null
                    head = next
                } else {
                    head = null
                    tail = null
                }
            }
            size.decrementAndGet()
            if (size.get() == -1) {
                println("-1")
            }
            house.inHighPriorityQueue = true
        }
        writeLock.unlock()
        readLock.unlock()
        house.deChain()
        this.enqueue(house)

    }

    def adjust(house: ActorHouse): Boolean = {
        ???
    }

}
