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

import io.otavia.core.system.HouseQueueHolder.*
import io.otavia.core.util.SystemPropertyUtil

class HouseQueueHolder(val thread: ActorThread) {

    private val serverActorQueue   = new HouseQueue(this)
    private val channelsActorQueue = new HouseQueue(this)
    private val actorQueue         = new HouseQueue(this)

    private var serverRuns: Long  = 0
    private var serverTimes: Long = 0

    private var channelsRuns: Long  = 0
    private var channelsTimes: Long = 0

    private var actorRuns: Long  = 0
    private var actorTimes: Long = 0

    @volatile private var runningStart: Long = Long.MaxValue

    private def get(): ActorHouse = {
        if (serverActorQueue.available) serverActorQueue.poll()
        else if (channelsActorQueue.available) channelsActorQueue.poll()
        else if (actorQueue.available) actorQueue.poll()

        ???
    }

    private def get(nanos: Long): ActorHouse = {

        ???
    }

    /** Run by [[thread]] */
    def run(): Boolean = {
//        val house =
        ???
    }

    def stealable: Boolean = (actorQueue.readies > STEAL_REMAINING_THRESHOLD) ||
        ((System.nanoTime() - runningStart) > STEAL_NANO_THRESHOLD)

    /** Steal running by other [[ActorThread]] */
    def stealRun(): Boolean = {
        thread.system.pool.workers
        ???
    }

}

object HouseQueueHolder {

    private val STEAL_REMAINING_THRESHOLD = SystemPropertyUtil.getInt("io.otavia.core.steal.threshold", 4)
    private val STEAL_NANO_THRESHOLD =
        SystemPropertyUtil.getInt("io.otavia.core.steal.threshold.microsecond", 1000) * 1000

}
