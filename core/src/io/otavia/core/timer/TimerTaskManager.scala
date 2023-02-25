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

package io.otavia.core.timer

import io.netty5.util.{Timeout, Timer as NettyTimer}
import io.otavia.core.address.EventableAddress
import io.otavia.core.timer.Timer.TimeoutTrigger

import java.util.concurrent.{ConcurrentHashMap, TimeUnit}

class TimerTaskManager(val timer: Timer) {

    private val registeredTasks = new ConcurrentHashMap[Long, OtaviaTimerTask]()

    // TODO: support OtaviaTimerTask object pool

    def newTask(
        address: EventableAddress,
        period: Long,
        attach: AnyRef | Null = null,
        periodUnit: TimeUnit = TimeUnit.MILLISECONDS
    ): OtaviaTimerTask = {
        val timerTask = new OtaviaTimerTask(this)
        timerTask.setAttach(attach)
        timerTask.setPeriod(period)
        timerTask.setAddress(address)
        timerTask.setPeriodUnit(periodUnit)
        registeredTasks.put(timerTask.registerId, timerTask)
        timerTask
    }

    def remove(id: Long): Unit = registeredTasks.remove(id) match
        case null =>
        case timerTask: OtaviaTimerTask =>
            timerTask.timeout.cancel()

    def update(trigger: TimeoutTrigger, registerId: Long): Unit = {
        registeredTasks.remove(registerId) match
            case task: OtaviaTimerTask =>
                val nettyTimer: NettyTimer = task.timeout.timer().nn
                task.timeout.cancel() // cancel old timer task
                registeredTasks.put(registerId, task)
                trigger match
                    case TimeoutTrigger.FixTime(date) =>
                        updateTimeoutTrigger(nettyTimer, task, date.getTime - System.currentTimeMillis())
                    case TimeoutTrigger.DelayTime(delay, unit) =>
                        updateTimeoutTrigger(nettyTimer, task, delay, delayUnit = unit)
                    case TimeoutTrigger.DelayPeriod(delay, period, delayUnit, periodUnit) =>
                        updateTimeoutTrigger(nettyTimer, task, delay, period, delayUnit, periodUnit)
                    case TimeoutTrigger.FirstTimePeriod(first, period, unit) =>
                        val delay = first.getTime - System.currentTimeMillis()
                        updateTimeoutTrigger(nettyTimer, task, delay, period, periodUnit = unit)
            case _ =>
                println(s"Timer task register id $registerId is not registered in system timer.")
    }

    private def updateTimeoutTrigger(
        nettyTimer: NettyTimer,
        task: OtaviaTimerTask,
        delay: Long,
        period: Long = -1,
        delayUnit: TimeUnit = TimeUnit.MILLISECONDS,
        periodUnit: TimeUnit = TimeUnit.MILLISECONDS
    ): Unit = {
        task.update(period, periodUnit)
        val handle: Timeout = nettyTimer.newTimeout(task, if (delay < 0) 0 else delay, delayUnit).nn
        task.setHandle(handle)
    }

}
