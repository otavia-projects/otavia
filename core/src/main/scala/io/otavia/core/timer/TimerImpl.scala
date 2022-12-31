/*
 * Copyright 2022 Yan Kun
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
import io.otavia.core.address.{Address, ChannelsActorAddress}
import io.otavia.core.reactor.TimeoutEvent
import io.otavia.core.system.ActorSystem
import io.otavia.core.timer.Timer
import io.otavia.core.timer.Timer.*

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue, ThreadFactory, TimeUnit}

/** Default implementation of [[Timer]] */
final class TimerImpl(private[core] val system: ActorSystem) extends Timer {

    private val hashedWheelTimer = new HashedWheelTimer(new TimerThreadFactory())
    private val timerTasks       = new ConcurrentHashMap[Long, TimerTriggerTask]()

    private val nextId = new AtomicLong(0)

    override def cancelTimerTask(registerId: Long): Unit = {
        val task = timerTasks.remove(registerId)
        if (task != null) task.timeout.cancel()
    }

    override def registerTimerTask(trigger: TimeoutTrigger, address: Address[_]): Long =
        registerTimerTask(trigger, address, null)

    override def registerTimerTask(trigger: TimeoutTrigger, address: Address[_], attach: AnyRef): Long = {
        val registerId = nextId.getAndIncrement()
        trigger match
            case TimeoutTrigger.FixTime(date) =>
                handleTimeoutTrigger(address, registerId, date.getTime - System.currentTimeMillis(), attach = attach)
            case TimeoutTrigger.DelayTime(delay) => handleTimeoutTrigger(address, registerId, delay, attach = attach)
            case TimeoutTrigger.DelayPeriod(delay, period) =>
                handleTimeoutTrigger(address, registerId, delay, period, attach)
            case TimeoutTrigger.FirstTimePeriod(first, period) =>
                val delay = first.getTime - System.currentTimeMillis()
                handleTimeoutTrigger(address, registerId, delay, period, attach)

        registerId
    }

    private def handleTimeoutTrigger(
        address: Address[?],
        registerId: Long,
        delay: Long,
        period: Long = -1, // not period
        attach: AnyRef | Null = null
    ): Unit = {
        if (delay <= 0 && period < 0) address.inform(TimeoutEvent(registerId))
        else {
            val timerTask = new TimerTriggerTask(address, registerId, period, timerTasks, attach)
            timerTasks.put(timerTask.registerId, timerTask)
            if (delay <= 0 && period > 0)
                timerTask.setHandle(hashedWheelTimer.newTimeout(timerTask, 0, TimeUnit.MILLISECONDS))
            else // delay > 0, period
                timerTask.setHandle(hashedWheelTimer.newTimeout(timerTask, delay, TimeUnit.MILLISECONDS))
        }
    }

}
