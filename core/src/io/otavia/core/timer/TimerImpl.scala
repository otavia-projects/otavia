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

import io.netty5.util.HashedWheelTimer
import io.otavia.core.address.{Address, ChannelsActorAddress, EventableAddress}
import io.otavia.core.reactor.TimeoutEvent
import io.otavia.core.system.ActorSystem
import io.otavia.core.timer.Timer
import io.otavia.core.timer.Timer.*
import org.log4s.{Logger, getLogger}

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue, ThreadFactory, TimeUnit}

/** Default implementation of [[Timer]] */
final class TimerImpl(private[core] val system: ActorSystem) extends Timer {

    private val hashedWheelTimer = new HashedWheelTimer(new TimerThreadFactory())
    private val taskManager      = new TimerTaskManager(this)

    private val nextId = new AtomicLong(Timer.INVALID_TIMEOUT_REGISTER_ID + 1)

    protected val logger: Logger = getLogger

    override private[timer] def nextRegisterId() = nextId.getAndIncrement()

    override def cancelTimerTask(registerId: Long): Unit = taskManager.remove(registerId)

    override def registerTimerTask(trigger: TimeoutTrigger, address: EventableAddress): Long =
        registerTimerTask0(trigger, address, null)

    override def registerTimerTask(trigger: TimeoutTrigger, address: EventableAddress, attach: AnyRef): Long =
        registerTimerTask0(trigger, address, attach)

    private def registerTimerTask0(trigger: TimeoutTrigger, address: EventableAddress, attach: AnyRef | Null): Long = {
        trigger match
            case TimeoutTrigger.FixTime(date) =>
                handleTimeoutTrigger(address, date.getTime - System.currentTimeMillis(), attach = attach)
            case TimeoutTrigger.DelayTime(delay, unit) =>
                handleTimeoutTrigger(address, delay, attach = attach)
            case TimeoutTrigger.DelayPeriod(delay, period, delayUnit, periodUnit) =>
                handleTimeoutTrigger(address, delay, period, delayUnit, periodUnit, attach)
            case TimeoutTrigger.FirstTimePeriod(first, period, unit) =>
                val delay = first.getTime - System.currentTimeMillis()
                handleTimeoutTrigger(address, delay, period, attach = attach, periodUnit = unit)
    }

    private def handleTimeoutTrigger(
        address: EventableAddress,
        delay: Long,
        period: Long = -1, // not period
        delayUnit: TimeUnit = TimeUnit.MILLISECONDS,
        periodUnit: TimeUnit = TimeUnit.MILLISECONDS,
        attach: AnyRef | Null = null
    ): Long = if (delay <= 0 && period < 0) {
        val registerId = nextRegisterId()
        address.inform(TimeoutEvent(registerId))
        registerId
    } else {
        val timerTask = taskManager.newTask(address, period, attach, periodUnit)
        if (delay <= 0 && period > 0)
            timerTask.setHandle(hashedWheelTimer.newTimeout(timerTask, 0, periodUnit).nn)
        else // delay > 0, period
            timerTask.setHandle(hashedWheelTimer.newTimeout(timerTask, delay, delayUnit).nn)

        timerTask.registerId
    }

    override def updateTimerTask(trigger: TimeoutTrigger, registerId: Long): Unit = {
        taskManager.update(trigger, registerId)
    }

}
