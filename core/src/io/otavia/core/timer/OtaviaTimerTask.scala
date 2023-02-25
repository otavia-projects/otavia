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

import io.netty5.util.{Timeout, TimerTask, Timer as NTimer}
import io.otavia.core.address.EventableAddress
import io.otavia.core.reactor.TimeoutEvent

import java.util.concurrent.{ConcurrentHashMap, TimeUnit}
import scala.beans.BeanProperty

final class OtaviaTimerTask(val manager: TimerTaskManager) extends TimerTask {

    val id: Long = manager.timer.nextRegisterId()

    var address: EventableAddress = _

    var period: Long = -1

    var attach: AnyRef | Null = null

    var periodUnit: TimeUnit = TimeUnit.MILLISECONDS

    @volatile private var handle: Timeout = _

    def setAddress(address: EventableAddress): Unit = this.address = address
    def setPeriod(period: Long): Unit               = this.period = period
    def setAttach(attach: AnyRef | Null): Unit      = this.attach = attach
    def setPeriodUnit(periodUnit: TimeUnit): Unit   = this.periodUnit = periodUnit

    def parent: TimerTaskManager = manager

    def timeout: Timeout = handle

    def registerId: Long = id

    def setHandle(timeout: Timeout): Unit = this.synchronized {
        this.handle = timeout
    }

    def update(period: Long, periodUnit: TimeUnit = TimeUnit.MILLISECONDS): Unit = this.synchronized {
        this.period = period
        this.periodUnit = periodUnit
    }

    override def run(timeout: Timeout): Unit = this.synchronized {
        address.inform(TimeoutEvent(id, attach))
        if (period > 0) {
            val timer: NTimer = timeout.timer().nn
            val newTimeout    = timer.newTimeout(this, period, periodUnit).nn
            this.setHandle(newTimeout)
        } else {
            parent.remove(id)
        }
    }

}
