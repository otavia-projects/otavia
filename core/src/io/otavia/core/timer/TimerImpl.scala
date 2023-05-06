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

import io.netty5.util.{HashedWheelTimer, TimerTask}
import io.otavia.core.address.{Address, EventableAddress}
import io.otavia.core.cache.ResourceTimer
import io.otavia.core.channel.Channel
import io.otavia.core.reactor.{AskTimeoutEvent, ChannelTimeoutEvent, ResourceTimeoutEvent, TimeoutEvent}
import io.otavia.core.slf4a.Logger
import io.otavia.core.system.ActorSystem
import io.otavia.core.timer.Timer
import io.otavia.core.timer.Timer.*

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue, ThreadFactory, TimeUnit}
import scala.language.unsafeNulls

/** Default implementation of [[Timer]] */
final class TimerImpl(private[timer] val system: ActorSystem) extends Timer {

    private val hashedWheelTimer = new HashedWheelTimer(new TimerThreadFactory())
    private val taskManager      = new TimerTaskManager(this)

    private val nextId = new AtomicLong(Timer.INVALID_TIMEOUT_REGISTER_ID + 1)

    protected val logger: Logger = Logger.getLogger(getClass, system)

    override private[timer] def nextRegisterId() = nextId.getAndIncrement()

    override def cancelTimerTask(registerId: Long): Unit = taskManager.remove(registerId)

    override def registerActorTimeout(
        trigger: TimeoutTrigger,
        address: EventableAddress,
        attach: Option[AnyRef]
    ): Long = {
        val (delay, period, delayUnit, periodUnit) = extract(trigger)
        logger.trace(s"register timeout trigger with delay: ${delay} ${delayUnit} period: ${period} ${periodUnit}")
        if (delay <= 0 && period < 0) {
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
        if (delay <= 0 && period < 0) {
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
        if (delay <= 0 && period < 0) {
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
        if (delay <= 0 && period < 0) {
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
        case TimeoutTrigger.FixTime(date) =>
            (date.getTime - System.currentTimeMillis(), -1, TimeUnit.MILLISECONDS, TimeUnit.MILLISECONDS)
        case TimeoutTrigger.DelayTime(delay, unit) =>
            (delay, -1, unit, TimeUnit.MILLISECONDS)
        case TimeoutTrigger.DelayPeriod(delay, period, delayUnit, periodUnit) =>
            (delay, period, delayUnit, periodUnit)
        case TimeoutTrigger.FirstTimePeriod(first, period, periodUnit) =>
            val delay = first.getTime - System.currentTimeMillis()
            (delay, period, TimeUnit.MILLISECONDS, periodUnit)

    private def handle(
        timerTask: TimeoutTask,
        delay: Long,
        period: Long,
        delayUnit: TimeUnit,
        periodUnit: TimeUnit
    ): Long = {
        if (delay <= 0 && period > 0)
            timerTask.setHandle(hashedWheelTimer.newTimeout(timerTask, 0, periodUnit))
        else // delay > 0, period
            timerTask.setHandle(hashedWheelTimer.newTimeout(timerTask, delay, delayUnit))
        timerTask.registerId
    }

    override def updateTimerTask(trigger: TimeoutTrigger, registerId: Long): Unit = {
        taskManager.update(trigger, registerId)
    }

}
