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
import cc.otavia.core.message.{AskTimeoutEvent, ChannelTimeoutEvent, ResourceTimeoutEvent, TimeoutEvent}
import cc.otavia.core.pool.ResourceTimer
import cc.otavia.core.slf4a.Logger
import cc.otavia.core.system.ActorSystem
import cc.otavia.core.system.monitor.TimerMonitor
import cc.otavia.core.timer.Timer.*

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import scala.language.unsafeNulls

/** Default implementation of [[Timer]] */
final class TimerImpl(private[timer] val system: ActorSystem) extends Timer {

    private val hashedWheelTimer = new HashedWheelTimer(
      system,
      new TimerThreadFactory(),
      system.config.timer.tickDurationMs,
      TimeUnit.MILLISECONDS,
      system.config.timer.ticksPerWheel
    )

    private val taskManager = new TimerTaskManager(this)

    private val nextId = new AtomicLong(Timer.INVALID_TIMEOUT_REGISTER_ID + 1)

    protected val logger: Logger = Logger.getLogger(getClass, system)

    override private[timer] def nextRegisterId() = nextId.getAndIncrement()

    override def cancelTimerTask(registerId: Long): Unit = taskManager.remove(registerId)

    override private[core] def internalTimer = hashedWheelTimer

    override private[core] def monitor() = TimerMonitor(taskManager.count)

    override def registerActorTimeout(
        trigger: TimeoutTrigger,
        address: EventableAddress,
        attach: Option[AnyRef]
    ): Long = {
        val (delay, period, delayUnit, periodUnit) = extract(trigger)
        logger.trace(s"register timeout trigger with delay: $delay $delayUnit period: $period $periodUnit")
        if (delay <= 0 && period <= 0) {
            val registerId = nextRegisterId()
            address.inform(TimeoutEvent(registerId, attach))
            registerId
        } else {
            val timerTask = taskManager.newActorTimeoutTask(address, period, attach, periodUnit)
            handle(timerTask, delay, period, delayUnit, periodUnit)
        }
    }

    override def registerChannelTimeout(trigger: TimeoutTrigger, channel: Channel): Long = {
        val (delay, period, delayUnit, periodUnit) = extract(trigger)
        if (delay <= 0 && period <= 0) {
            val registerId = nextRegisterId()
            channel.executorAddress.inform(ChannelTimeoutEvent(registerId, channel))
            registerId
        } else {
            val timerTask = taskManager.newChannelTimeoutTask(channel.executorAddress, period, channel, periodUnit)
            handle(timerTask, delay, period, delayUnit, periodUnit)
        }
    }

    override private[core] def registerAskTimeout(
        trigger: TimeoutTrigger,
        sender: EventableAddress,
        askId: Long
    ): Long = {
        val (delay, period, delayUnit, periodUnit) = extract(trigger)
        if (delay <= 0 && period <= 0) {
            val registerId = nextRegisterId()
            sender.inform(AskTimeoutEvent(registerId, askId))
            registerId
        } else {
            val timerTask = taskManager.newAskTimeoutTask(sender, period, askId, periodUnit)
            handle(timerTask, delay, period, delayUnit, periodUnit)
        }
    }

    override private[core] def registerResourceTimeout(
        trigger: TimeoutTrigger,
        address: EventableAddress,
        resource: ResourceTimer
    ): Long = {
        val (delay, period, delayUnit, periodUnit) = extract(trigger)
        if (delay <= 0 && period <= 0) {
            val registerId = nextRegisterId()
            address.inform(ResourceTimeoutEvent(registerId, resource))
            registerId
        } else {
            val timerTask = taskManager.newResourceTimeoutTask(address, period, resource, periodUnit)
            handle(timerTask, delay, period, delayUnit, periodUnit)
        }
    }

    // extract (delay, period, delayUnit, periodUnit)
    inline private def extract(trigger: TimeoutTrigger): (Long, Long, TimeUnit, TimeUnit) = trigger match
        case TimeoutTrigger.FixTime(nanos) =>
            (nanos - System.nanoTime(), -1, TimeUnit.NANOSECONDS, TimeUnit.MILLISECONDS)
        case TimeoutTrigger.DelayTime(delay, unit) =>
            (delay, -1, unit, TimeUnit.MILLISECONDS)
        case TimeoutTrigger.DelayPeriod(delay, period, delayUnit, periodUnit) =>
            (delay, period, delayUnit, periodUnit)
        case TimeoutTrigger.FirstTimePeriod(first, period, periodUnit) =>
            (first - System.nanoTime(), period, TimeUnit.NANOSECONDS, periodUnit)

    private def handle(
        timerTask: TimeoutTask,
        delay: Long,
        period: Long,
        delayUnit: TimeUnit,
        periodUnit: TimeUnit
    ): Long = {
        val d = if (delay < 0) 0 else delay
        val handle =
            if (period > 0) hashedWheelTimer.newTimeout(timerTask, d, delayUnit, period, periodUnit)
            else hashedWheelTimer.newTimeout(timerTask, d, delayUnit)
        timerTask.setHandle(handle)
        timerTask.registerId
    }

    override def updateTimerTask(trigger: TimeoutTrigger, registerId: Long): Unit = {
        taskManager.update(trigger, registerId)
    }

}
