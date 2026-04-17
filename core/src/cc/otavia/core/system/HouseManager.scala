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

import cc.otavia.common.SystemPropertyUtil
import cc.otavia.core.actor.Actor
import cc.otavia.core.slf4a.Logger
import cc.otavia.core.system.HouseManager.*
import cc.otavia.core.system.monitor.HouseManagerMonitor

import scala.language.unsafeNulls

/** Per-[[ActorThread]] scheduler that manages three priority queues for actor house scheduling.
 *
 *  Queue assignment is based on actor type:
 *    - '''mountingQueue''' (FIFO): houses awaiting initial mount (all types)
 *    - '''channelsActorQueue''' (Priority): IO-capable actors, fully drained in Phase 2 with no time budget
 *    - '''actorQueue''' (Priority): business logic actors, time-budgeted in Phase 3
 *
 *  Each [[PriorityHouseQueue]] has two sub-queues: normal priority and high priority. Houses are promoted to high
 *  priority when they have excessive pending replies, events, or a low stack-end-rate (backpressure signal).
 *
 *  @param thread
 *    the owning ActorThread
 */
final class HouseManager(val thread: ActorThread) {

    private val logger = Logger.getLogger(getClass, thread.system)

    private val mountingQueue      = new FIFOHouseQueue(this)
    private val channelsActorQueue = new PriorityHouseQueue(this)
    private val actorQueue         = new PriorityHouseQueue(this)

    private var currentRunning: Actor[?] = _

    def system: ActorSystem = thread.system

    private[core] def currentRunningActor: Actor[?] = currentRunning

    def laterTasks: scala.collection.mutable.ArrayDeque[Runnable] = thread.laterTasks

    /** Whether any queue has work available. Used by the IO handler to determine if it can block on select. */
    def runnable: Boolean =
        actorQueue.nonEmpty || channelsActorQueue.nonEmpty || mountingQueue.nonEmpty

    // =========================================================================
    // Scheduling operations
    // =========================================================================

    /** Schedule a newly created house for mounting. */
    def mount(house: ActorHouse): Unit = {
        mountingQueue.enqueue(house)
        thread.notifyThread()
    }

    /** Enqueue an [[ActorHouse]] that has transitioned from WAITING to READY. The house is placed into the appropriate
     *  queue based on its actor type:
     *    - [[cc.otavia.core.actor.StateActor]] -> actorQueue (time-budgeted in Phase 3)
     *    - [[cc.otavia.core.actor.ChannelsActor]] / [[cc.otavia.core.actor.AcceptorActor]] -> channelsActorQueue
     *      (fully drained in Phase 2)
     */
    def ready(house: ActorHouse): Unit = {
        if (house.actorType == ActorHouse.STATE_ACTOR) actorQueue.enqueue(house)
        else if (house.actorType >= ActorHouse.CHANNELS_ACTOR) channelsActorQueue.enqueue(house)

        thread.notifyThread()
    }

    /** Promote an [[ActorHouse]] to high priority when new messages arrive while it is already in READY or RUNNING
     *  state. Only houses with pending high-priority indicators (excessive replies/events or low stack-end-rate)
     *  are promoted.
     */
    def change(house: ActorHouse): Unit = {
        if (house.highPriority && !house.inHighPriorityQueue) {
            if (house.actorType == ActorHouse.CHANNELS_ACTOR) channelsActorQueue.adjustPriority(house)
            else if (house.actorType == ActorHouse.STATE_ACTOR) actorQueue.adjustPriority(house)
        }
    }

    // =========================================================================
    // Execution
    // =========================================================================

    /** Run channels actor queue (IO pipeline work) and mounting queue. These are always drained fully as they are
     *  part of the IO pipeline and must not be starved.
     */
    def runChannelsActors(): Unit = {
        if (channelsActorQueue.available) run0(channelsActorQueue, Long.MaxValue)
        if (mountingQueue.available) mount0()
    }

    /** Run state actor queue (business logic) within the given time budget. Actors that are not processed within
     *  the deadline remain in the queue for the next iteration.
     *
     *  @param deadlineNanos
     *    the absolute time (in nanos) after which no more actors should be dequeued. [[Long.MaxValue]] means no limit.
     */
    def runStateActors(deadlineNanos: Long): Unit = {
        if (!actorQueue.available) return
        run0(actorQueue, deadlineNanos)
    }

    private def run0(houseQueue: HouseQueue, deadlineNanos: Long): Unit = {
        var house = houseQueue.dequeue()
        var count = 0
        while (house != null) {
            currentRunning = house.actor
            house.run()
            currentRunning = null
            count += 1
            if (deadlineNanos != Long.MaxValue && (count & 0xF) == 0 && System.nanoTime() >= deadlineNanos) return
            house = houseQueue.dequeue()
        }
    }

    private def mount0(): Unit = {
        var house = mountingQueue.dequeue()
        while (house != null) {
            currentRunning = house.actor
            house.doMount()
            currentRunning = null
            house = mountingQueue.dequeue()
        }
    }

    // =========================================================================
    // Work stealing
    // =========================================================================

    private def stealable: Boolean = actorQueue.readies > STEAL_REMAINING_THRESHOLD

    /** Attempt to steal a StateActor from another [[ActorThread]]'s queue. Used for cross-thread load balancing.
     *  Only steals from the normal-priority queue.
     *
     *  @return
     *    true if a house was stolen and executed
     */
    def trySteal(): Boolean = {
        if (actorQueue.nonEmpty || channelsActorQueue.nonEmpty) return false
        val threads                         = thread.parent.workers
        var i                               = 1
        var continue: Boolean               = true
        var stealThread: ActorThread | Null = null
        while (i < threads.length && continue) {
            val candidate = threads((i + this.thread.index) % threads.length)
            i += 1
            if (candidate != null && candidate.houseManager.stealable) {
                continue = false
                stealThread = candidate
            }
        }
        if (stealThread != null) {
            stealThread.houseManager.runSteal()
        } else false
    }

    /** Execute one stolen house. Called by the stealing thread. */
    private def runSteal(): Boolean = {
        if (actorQueue.available) {
            val house = actorQueue.dequeue()
            if (house != null) {
                house.run()
                true
            } else false
        } else false
    }

    // =========================================================================
    // Monitoring
    // =========================================================================

    def monitor(): HouseManagerMonitor = HouseManagerMonitor(
      mountingQueue.readies,
      channelsActorQueue.readies,
      actorQueue.readies,
      actorQueue.readies
    )

    override def toString: String = s"mounting=${mountingQueue.readies}, channels=${channelsActorQueue.readies}, " +
        s"state=${actorQueue.readies}"

}

object HouseManager {

    private val STEAL_REMAINING_THRESHOLD = SystemPropertyUtil.getInt("cc.otavia.core.steal.threshold", 64)

}
