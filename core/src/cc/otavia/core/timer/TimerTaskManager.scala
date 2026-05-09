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

package cc.otavia.core.timer

import cc.otavia.core.address.EventableAddress
import cc.otavia.core.channel.Channel
import cc.otavia.core.pool.ResourceTimer
import cc.otavia.core.slf4a.Logger
import cc.otavia.core.system.ActorSystem
import cc.otavia.core.timer.{InternalTimer, Timeout, TimeoutTrigger}

import java.util.concurrent.{ConcurrentHashMap, TimeUnit}

class TimerTaskManager(val timer: Timer) {

    private val registeredTasks = new ConcurrentHashMap[Long, TimeoutTask]()

    private val logger: Logger = Logger.getLogger(getClass, timer.system)

    final def system: ActorSystem = timer.system

    def count: Int = registeredTasks.size()

    def newActorTimeoutTask(
        address: EventableAddress,
        period: Long,
        attach: Option[AnyRef],
        periodUnit: TimeUnit = TimeUnit.MILLISECONDS
    ): ActorTimeoutTask = {
        val timerTask = new ActorTimeoutTask(this)
        timerTask.setAttach(attach)
        timerTask.set(address, period, periodUnit)
        registeredTasks.put(timerTask.registerId, timerTask)
        timerTask
    }

    def newAskTimeoutTask(
        address: EventableAddress,
        period: Long,
        askId: Long,
        periodUnit: TimeUnit = TimeUnit.MILLISECONDS
    ): AskTimeoutTask = {
        val timerTask = new AskTimeoutTask(this)
        timerTask.setAskId(askId)
        timerTask.set(address, period, periodUnit)
        registeredTasks.put(timerTask.registerId, timerTask)
        timerTask
    }

    def newChannelTimeoutTask(
        address: EventableAddress,
        period: Long,
        channel: Channel,
        periodUnit: TimeUnit = TimeUnit.MILLISECONDS
    ): ChannelTimeoutTask = {
        val timerTask = new ChannelTimeoutTask(this)
        timerTask.setChannel(channel)
        timerTask.set(address, period, periodUnit)
        registeredTasks.put(timerTask.registerId, timerTask)
        timerTask
    }

    def newResourceTimeoutTask(
        address: EventableAddress,
        period: Long,
        resourceTimer: ResourceTimer,
        periodUnit: TimeUnit = TimeUnit.MILLISECONDS
    ): ResourceTimeoutTask = {
        val timerTask = new ResourceTimeoutTask(this)
        timerTask.setResourceTimer(resourceTimer)
        timerTask.set(address, period, periodUnit)
        registeredTasks.put(timerTask.registerId, timerTask)
        timerTask
    }

    def remove(id: Long): Unit = registeredTasks.remove(id) match
        case null =>
        case timerTask: TimeoutTask =>
            timerTask.timeout.cancel

    def update(trigger: TimeoutTrigger, registerId: Long): Unit = {
        registeredTasks.get(registerId) match
            case task: TimeoutTask =>
                val oldHandle = task.timeout
                oldHandle.cancel
                val internalTimer = oldHandle.timer
                trigger match
                    case TimeoutTrigger.FixTime(nanos) =>
                        updateTimeoutTrigger(internalTimer, task, nanos - System.nanoTime())
                    case TimeoutTrigger.DelayTime(delay, unit) =>
                        updateTimeoutTrigger(internalTimer, task, delay, delayUnit = unit)
                    case TimeoutTrigger.DelayPeriod(delay, period, delayUnit, periodUnit) =>
                        updateTimeoutTrigger(internalTimer, task, delay, period, delayUnit, periodUnit)
                    case TimeoutTrigger.FirstTimePeriod(first, period, unit) =>
                        val delay = first - System.nanoTime()
                        updateTimeoutTrigger(internalTimer, task, delay, period, periodUnit = unit)
            case _ =>
                if (logger.isWarnEnabled)
                    logger.warn(s"Timer task register id $registerId is not registered in system timer.")
    }

    private def updateTimeoutTrigger(
        internalTimer: InternalTimer,
        task: TimeoutTask,
        delay: Long,
        period: Long = -1,
        delayUnit: TimeUnit = TimeUnit.MILLISECONDS,
        periodUnit: TimeUnit = TimeUnit.MILLISECONDS
    ): Unit = {
        task.update(period, periodUnit)
        val d = if (delay < 0) 0 else delay
        val handle: Timeout =
            if (period > 0) internalTimer.newTimeout(task, d, delayUnit, period, periodUnit).nn
            else internalTimer.newTimeout(task, d, delayUnit).nn
        task.setHandle(handle)
    }

}
