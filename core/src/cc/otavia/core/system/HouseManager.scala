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

import cc.otavia.core.actor.Actor
import cc.otavia.core.slf4a.Logger
import cc.otavia.core.system.monitor.HouseManagerMonitor

import scala.language.unsafeNulls

/** Per-[[ActorThread]] scheduler that manages three priority queues for actor house scheduling.
 *
 *  Queue assignment is based on actor type:
 *    - '''mountingQueue''' (FIFO): houses awaiting initial mount (all types)
 *    - '''channelsActorQueue''' (Priority): IO-capable actor, fully drained in Phase 2 with no time budget
 *    - '''actorQueue''' (Priority): business logic actor, time-budgeted in Phase 3
 *
 *  Each [[PriorityHouseQueue]] has two sub-queues: normal priority and high priority. Houses are classified as high
 *  priority based on three signals (see [[ActorHouse._highPriority]]): reply backlog, event backlog, and no downstream
 *  blocking (no pending promises). Priority is determined at enqueue time; there is no mid-queue promotion.
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

    /** Whether the scheduling queue for the given actor type has other houses waiting. Used by the dispatch-loop
     *  optimization to decide whether the current actor can re-enter dispatch without re-queueing.
     */
    def hasOtherReady(house: ActorHouse): Boolean =
        if house.actorType == ActorHouse.STATE_ACTOR then actorQueue.nonEmpty
        else channelsActorQueue.nonEmpty

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

    // =========================================================================
    // Execution
    // =========================================================================

    /** Run channels actor queue (IO pipeline work) and mounting queue. These are always drained fully as they are
     *  part of the IO pipeline and must not be starved.
     */
    def runChannelsActors(): Unit = {
        if (channelsActorQueue.available) drainHouses(channelsActorQueue, Long.MaxValue)
        if (mountingQueue.available) drainMountingQueue()
    }

    /** Run state actor queue (business logic) within the given time budget. Actors that are not processed within
     *  the deadline remain in the queue for the next iteration.
     *
     *  @param deadlineNanos
     *    the absolute time (in nanos) after which no more actor should be dequeued. [[Long.MaxValue]] means no limit.
     */
    def runStateActors(deadlineNanos: Long): Unit = {
        if (!actorQueue.available) return
        drainHouses(actorQueue, deadlineNanos)
    }

    private def drainHouses(houseQueue: HouseQueue, deadlineNanos: Long): Unit = {
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

    private def drainMountingQueue(): Unit = {
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

    /** Consecutive event-loop iterations where all three queues were empty. Reset to 0 when the thread has work.
     *  Used as the thief-side input to the adaptive steal condition (see [[stealableBy]]).
     */
    private var idleCount: Int = 0

    /** Whether this victim's queue is backlogged enough for a thief with the given [[idleCount]] to attempt stealing.
     *
     *  Adaptive steal condition: `readies > STEAL_FLOOR && idleCount × readies >= STEAL_AGGRESSION`
     *
     *  This ties the thief's idleness to the victim's backlog severity:
     *    - Severe backlog (high readies) → few idle iterations needed (fast response to crisis)
     *    - Moderate backlog → more idle iterations required (conservative, avoids stealing from a thread
     *      that will self-drain shortly)
     *    - Below STEAL_FLOOR → never steal (CPU cache cost of cross-thread execution outweighs benefit)
     */
    private def stealableBy(thiefIdleCount: Int): Boolean = {
        val r = actorQueue.readies
        r > thread.system.config.scheduler.stealFloor &&
        thiefIdleCount * r >= thread.system.config.scheduler.stealAggression
    }

    /** Attempt to steal a StateActor from another [[ActorThread]]'s queue. Used as a safety net for extreme load
     *  imbalance — the primary scheduling model keeps actors on their owning thread for CPU cache locality.
     *
     *  '''Idle tracking:''' [[idleCount]] is incremented each call (each idle event-loop iteration). It is reset when
     *  the owning thread has work. This ensures only genuinely idle threads attempt stealing.
     *
     *  '''Victim selection:''' random starting index distributes multiple idle thieves across different victims. The
     *  actual dequeue uses [[PriorityHouseQueue.stealDequeue]] (tryLock-based, no spinning) so that a contended victim
     *  is skipped instantly without delaying either the thief or the victim.
     *
     *  '''Steal condition:''' see [[stealableBy]] — combines [[idleCount]] with victim queue depth.
     *
     *  @return
     *    true if a house was stolen and executed
     */
    def trySteal(): Boolean = {
        if (runnable) { idleCount = 0; return false }
        idleCount += 1

        val threads = thread.parent.workers
        val n       = threads.length
        if (n <= 1) return false

        val start = thread.random.nextInt(n)
        var i     = 0
        while (i < n) {
            val idx = (start + i) % n
            if (idx != thread.index) {
                val candidate = threads(idx)
                if (candidate != null && candidate.houseManager.stealableBy(idleCount)) {
                    val house = candidate.houseManager.stealDequeue()
                    if (house != null) {
                        house.run()
                        return true
                    }
                }
            }
            i += 1
        }
        false
    }

    private def stealDequeue(): ActorHouse | Null = actorQueue.stealDequeue()

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
