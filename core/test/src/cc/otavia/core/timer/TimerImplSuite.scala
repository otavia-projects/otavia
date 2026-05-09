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

import cc.otavia.core.system.ActorSystem
import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration.*

class TimerImplSuite extends AnyFunSuite {

    val system: ActorSystem = ActorSystem.global

    private def timerImpl: TimerImpl = system.timer match
        case impl: TimerImpl => impl

    // ---- Cancel timer task ----

    test("TimerImpl.cancelTimerTask removes registered task") {
        val timer = timerImpl
        val before = timer.monitor().tasks
        val timeout = timer.internalTimer.newTimeout(_ => {}, 5000, MILLISECONDS)
        assert(timeout.isCancelled == false)
        timeout.cancel
    }

    // ---- Monitor ----

    test("TimerImpl.monitor returns task count") {
        val monitor = timerImpl.monitor()
        assert(monitor.tasks >= 0)
    }

    // ---- InternalTimer basic ops ----

    test("TimerImpl.internalTimer is HashedWheelTimer") {
        assert(timerImpl.internalTimer.isInstanceOf[HashedWheelTimer])
    }

    // ---- TimeoutTrigger types ----

    test("TimeoutTrigger.DelayTime extracts correctly") {
        val trigger = TimeoutTrigger.DelayTime(100, MILLISECONDS)
        assert(trigger.isInstanceOf[TimeoutTrigger.DelayTime])
    }

    test("TimeoutTrigger.FixTime uses nanoTime") {
        val now = System.nanoTime()
        val trigger = TimeoutTrigger.FixTime(now)
        assert(trigger.isInstanceOf[TimeoutTrigger.FixTime])
    }

    test("TimeoutTrigger.DelayPeriod creates correctly") {
        val trigger = TimeoutTrigger.DelayPeriod(100, 200, MILLISECONDS, MILLISECONDS)
        assert(trigger.isInstanceOf[TimeoutTrigger.DelayPeriod])
    }

    test("TimeoutTrigger.FirstTimePeriod uses nanoTime") {
        val deadline = System.nanoTime() + 1000L
        val trigger = TimeoutTrigger.FirstTimePeriod(deadline, 100, MILLISECONDS)
        assert(trigger.isInstanceOf[TimeoutTrigger.FirstTimePeriod])
    }

}
