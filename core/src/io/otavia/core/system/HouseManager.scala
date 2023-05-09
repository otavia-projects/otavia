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

package io.otavia.core.system

import io.otavia.core.message.Event
import io.otavia.core.slf4a.Logger
import io.otavia.core.system.HouseManager.*
import io.otavia.core.system.monitor.HouseManagerMonitor
import io.otavia.core.util.SystemPropertyUtil

import scala.language.unsafeNulls

class HouseManager(val thread: ActorThread) {

    private val logger = Logger.getLogger(getClass, thread.system)

    private val mountingQueue = new FIFOHouseQueue(this)

    private val serverActorQueue   = new FIFOHouseQueue(this)
    private val channelsActorQueue = new PriorityHouseQueue(this)
    private val actorQueue         = new PriorityHouseQueue(this)

    private var serverRuns: Long  = 0
    private var serverTimes: Long = 0

    private var channelsRuns: Long  = 0
    private var channelsTimes: Long = 0

    private var actorRuns: Long  = 0
    private var actorTimes: Long = 0

    @volatile private var runningStart: Long = Long.MaxValue

    def mount(house: ActorHouse): Unit = {
        mountingQueue.enqueue(house)
        logger.trace(s"Schedule mount ${house.actor}")
        thread.notifyThread()
    }

    /** [[ActorHouse]] status: <b> WAITING -> READY
     *  @param house
     *    the status changed [[ActorHouse]]
     */
    def ready(house: ActorHouse): Unit = {
        if (house.actorType == ActorHouse.STATE_ACTOR) actorQueue.enqueue(house)
        else if (house.actorType == ActorHouse.CHANNELS_ACTOR) channelsActorQueue.enqueue(house)
        else if (house.actorType == ActorHouse.SERVER_CHANNELS_ACTOR) serverActorQueue.enqueue(house)

        logger.trace(s"Change actor ${house.actor} to ready")

        thread.notifyThread()
    }

    /** Received [[io.otavia.core.message.Message]] or [[Event]] when [[ActorHouse]] status is <b> READY | RUNNING
     *
     *  @param house
     *    The [[ActorHouse]] which is received [[io.otavia.core.message.Message]] or [[Event]]
     */
    def change(house: ActorHouse): Unit = {
        if (house.highPriority && !house.inHighPriorityQueue) {
            // try adjust priority
            if (house.actorType == ActorHouse.CHANNELS_ACTOR) channelsActorQueue.adjustPriority(house)
            else if (house.actorType == ActorHouse.STATE_ACTOR) actorQueue.adjustPriority(house)
        }
    }

    final private def adjustPriority(queue: PriorityHouseQueue, house: ActorHouse): Unit = {
        if (queue.adjust(house)) {
            queue.enqueue(house)
        }
    }

    /** Run by [[thread]], if no house is available, spin timeout nanosecond to wait some house become ready.
     *
     *  @return
     *    true if run some [[ActorHouse]], otherwise false.
     */
    def run(): Boolean = {
        runningStart = System.nanoTime()

        var success = false

        if (this.run0(serverActorQueue)) success = true

        if (this.run0(channelsActorQueue)) success = true

        if (this.run0(actorQueue)) success = true

        if (mountingQueue.available) {
            logger.trace(s"${thread.getName} mounting size ${mountingQueue.readies}")
            val house = mountingQueue.dequeue()
            if (house != null) {
                house.doMount()
                success = true
            }
        }

        runningStart = Long.MaxValue

        success
    }

    final private def run0(houseQueue: HouseQueue): Boolean = {
        val house = houseQueue.dequeue()
        if (house != null) {
            house.run()
            true
        } else false
    }

    private def stealable: Boolean = (actorQueue.readies > STEAL_REMAINING_THRESHOLD) ||
        (((System.nanoTime() - runningStart) > STEAL_NANO_THRESHOLD) && actorQueue.nonEmpty)

    /** Steal from other [[ActorThread]] to run, this method is called by [[HouseManager.thread]] */
    def steal(): Boolean = {
        // find the next stealable thread
        val threads                         = thread.parent.workers
        var i                               = 1
        var continue: Boolean               = true
        var stealThread: ActorThread | Null = null
        while (i < threads.length && continue) {
            val thread = threads((i + threads.length) % threads.length)
            i += 1
            if (thread != null && thread.houseManager.stealable) {
                continue = false
                stealThread = thread
            }
        }
        if (stealThread != null) {
//            runningStart = System.nanoTime()
            // running other thread's HouseManager
            val success = stealThread.houseManager.runSteal()
//            runningStart = Long.MaxValue
            success
        } else false
    }

    /** Steal running by other [[ActorThread]] */
    final private def runSteal(): Boolean = {
        if (actorQueue.available) {
            val house = actorQueue.dequeue()
            if (house != null) {
                house.run()
                true
            } else false
        } else false
    }

    def monitor(): HouseManagerMonitor = HouseManagerMonitor(
      mountingQueue.readies,
      serverActorQueue.readies,
      channelsActorQueue.readies,
      actorQueue.readies
    )

    override def toString: String = s"mounting=${mountingQueue.readies}, server=${serverActorQueue.readies}, " +
        s"channels=${channelsActorQueue.readies}, state=${actorQueue.readies}"

}

object HouseManager {

    private val STEAL_REMAINING_THRESHOLD = SystemPropertyUtil.getInt("io.otavia.core.steal.threshold", 16)
    private val STEAL_NANO_THRESHOLD =
        SystemPropertyUtil.getInt("io.otavia.core.steal.threshold.microsecond", 1 * 1000) * 1000

}
