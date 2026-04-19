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

/** A priority-aware MPSC house queue with two sub-queues: high-priority and normal-priority.
 *
 *  '''Dequeue order:''' high-priority sub-queue is always drained before the normal sub-queue (strict priority). This
 *  guarantees that actors that can immediately push the system forward (reply/event backlogs, or no downstream
 *  blocking — see [[ActorHouse._highPriority]]) are always served before normal actors.
 *
 *  '''Priority is determined at enqueue time.''' The [[ActorHouse._highPriority]] cached flag is read once when the
 *  house enters the queue (via [[ActorHouse.waitingToReady]] → [[HouseManager.ready]] → [[enqueue]]). A house that
 *  becomes high-priority while already queued will be correctly prioritized on its next enqueue (after
 *  [[ActorHouse.completeRunning]] re-evaluates the flag).
 *
 *  '''Concurrency model:''' SpinLock-based MPSC. Multiple producer threads can enqueue concurrently; the owning
 *  ActorThread dequeues via [[dequeue]]. Other threads may attempt opportunistic steals via [[stealDequeue]], which
 *  uses [[SpinLock.tryLock]] to avoid spinning on the owner's lock. Each sub-queue has independent read/write lock
 *  pairs to avoid cross-priority contention.
 *
 *  '''Schedule() outside lock:''' the READY → SCHEDULED CAS in [[ActorHouse.schedule]] is performed after releasing
 *  the queue lock. This is safe because no other thread can change the house state between unlock and schedule: the
 *  house is in READY state, removed from the queue, and no lifecycle transition is possible until schedule() fires.
 */
class PriorityHouseQueue(manager: HouseManager) extends HouseQueue(manager) {

    // Normal-priority sub-queue (SpinLock-based MPSC singly-linked list)
    private val readLock                   = new SpinLock()
    private val writeLock                  = new SpinLock()
    private val size                       = new AtomicInteger(0)
    @volatile private var head: ActorHouse = _
    @volatile private var tail: ActorHouse = _

    // High-priority sub-queue (SpinLock-based MPSC singly-linked list)
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
                size.incrementAndGet()
            }
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
            highWriteLock.unlock()
        }
    }

    override def dequeue(): ActorHouse | Null = {
        if (highSize.get() > 0) dequeuePriority()
        else if (size.get() > 0) dequeueNormal()
        else null
    }

    /** Opportunistic dequeue for cross-thread stealing. Uses [[SpinLock.tryLock()]] instead of [[SpinLock.lock()]] to
     *  avoid the stealing thread spinning on the owning thread's lock. Returns null immediately if the lock is contended.
     *
     *  Only handles the size > 1 case. When size == 1, the dequeue path requires acquiring both readLock and writeLock
     *  (to null out the tail pointer). Skipping this case is acceptable because the steal threshold (64+) guarantees the
     *  queue is deep when stealing is attempted.
     */
    def stealDequeue(): ActorHouse | Null = {
        // Try high-priority sub-queue first (consistent with dequeue contract)
        if (highSize.get() > 1 && highReadLock.tryLock()) {
            if (highSize.get() > 1) {
                val house = highHead
                highHead = house.next
                highSize.decrementAndGet()
                highReadLock.unlock()
                house.schedule()
                return house
            }
            highReadLock.unlock()
        }
        // Try normal-priority sub-queue
        if (size.get() > 1 && readLock.tryLock()) {
            if (size.get() > 1) {
                val house = head
                head = house.next
                size.decrementAndGet()
                readLock.unlock()
                house.schedule()
                return house
            }
            readLock.unlock()
        }
        null
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
                writeLock.unlock()
                readLock.unlock()
                house.schedule()
                house
            } else {
                val house = head
                head = house.next
                size.decrementAndGet()
                writeLock.unlock()
                readLock.unlock()
                house.schedule()
                house
            }
        } else {
            val house = head
            head = house.next
            size.decrementAndGet()
            readLock.unlock()
            house.schedule()
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
                highWriteLock.unlock()
                highReadLock.unlock()
                house.schedule()
                house
            } else {
                val house = highHead
                highHead = house.next
                highSize.decrementAndGet()
                highWriteLock.unlock()
                highReadLock.unlock()
                house.schedule()
                house
            }
        } else {
            val house = highHead
            highHead = house.next
            highSize.decrementAndGet()
            highReadLock.unlock()
            house.schedule()
            house
        }
    }

}
