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

import io.netty5.util.{Timeout, TimerTask}
import io.otavia.core.address.Address
import io.otavia.core.reactor.{Reactor, TimeoutEvent}
import io.otavia.core.timer.Timer.*

import java.util.Date
import java.util.concurrent.{ConcurrentHashMap, TimeUnit}

/** [[Timer]] can generate timeout event. */
trait Timer {

    /** API for [[io.otavia.core.actor.Actor]] to register timeout event trigger.
     *
     *  @param trigger
     *    Timeout event trigger.
     *  @param address
     *    [[Address]] of this [[TimeoutTrigger]] belong to. The timeout event of the [[TimeoutTrigger]] is send to this
     *    address.
     *  @return
     *    Register id of [[ReactorTimerTask]], [[io.otavia.core.actor.ChannelsActor]] can use this id to cancel this
     *    trigger by [[cancelTimerTask]]
     */
    def registerTimerTask(trigger: TimeoutTrigger, address: Address[?]): Long

    /** API for [[io.otavia.core.actor.ChannelsActor]] to register timeout event to [[Reactor]].
     *
     *  @param trigger
     *    Timeout event trigger.
     *  @param address
     *    [[Address]] of this [[TimeoutTrigger]] belong to. The timeout event of the [[TimeoutTrigger]] is send to this
     *    address.
     *  @param attach
     *    attachment object
     *  @return
     *    Register id of [[ReactorTimerTask]], [[io.otavia.core.actor.Actor]] can use this id to cancel this trigger by
     *    [[cancelTimerTask]]
     */
    def registerTimerTask(trigger: TimeoutTrigger, address: Address[?], attach: AnyRef): Long

    /** API for [[io.otavia.core.actor.Actor]] to cancel timeout event
     *
     *  @param registerId
     *    Register id of [[ReactorTimerTask]] to cancel.
     */
    def cancelTimerTask(registerId: Long): Unit

}

object Timer {

    final class TimerTriggerTask(
        val address: Address[?],
        val registerId: Long,
        val period: Long,
        val parent: ConcurrentHashMap[Long, TimerTriggerTask],
        val attach: AnyRef | Null = null
    ) extends TimerTask {

        @volatile private var handle: Timeout = _

        def timeout: Timeout = handle

        def setHandle(timeout: Timeout): Unit = this.handle = timeout

        override def run(timeout: Timeout): Unit = {
            address.inform(TimeoutEvent(registerId, attach))
            if (period > 0) setHandle(timeout.timer().newTimeout(this, period, TimeUnit.MILLISECONDS))
            else {
                val task = parent.remove(registerId)
                if (task != null) task.timeout.cancel()
            }
        }

    }

    enum TimeoutTrigger {

        case FixTime(date: Date)                        extends TimeoutTrigger
        case DelayTime(delay: Long)                     extends TimeoutTrigger
        case DelayPeriod(delay: Long, period: Long)     extends TimeoutTrigger
        case FirstTimePeriod(first: Date, period: Long) extends TimeoutTrigger

    }

}
