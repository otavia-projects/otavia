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

class HashedWheelTimerSuite extends AnyFunSuite {

    val system: ActorSystem = ActorSystem.global

    // ---- Basic one-shot timeout ----

    test("Should not expire before deadline") {
        val timer = new HashedWheelTimer(system)
        val timeout = timer.newTimeout(_ => {}, 10, SECONDS)
        Thread.sleep(50)
        assert(!timeout.isCancelled)
        assert(!timeout.isExpired)
    }

    test("Should expire after delay") {
        val timer = new HashedWheelTimer(system)
        val timeout = timer.newTimeout(_ => {}, 1, MILLISECONDS)
        Thread.sleep(200)
        assert(timeout.isExpired)
    }

    test("Cancel timeout before expiry") {
        val timer = new HashedWheelTimer(system)
        val timeout = timer.newTimeout(_ => {}, 10, SECONDS)
        Thread.sleep(50)
        timeout.cancel
        assert(!timeout.isExpired)
        assert(timeout.isCancelled)
    }

    test("Cancel already expired timeout returns false") {
        val timer = new HashedWheelTimer(system)
        val timeout = timer.newTimeout(_ => {}, 1, MILLISECONDS)
        Thread.sleep(200)
        assert(timeout.isExpired)
        val result = timeout.cancel
        assert(!result)
    }

    // ---- delay = 0 ----

    test("Delay zero fires on next tick") {
        val timer = new HashedWheelTimer(system)
        @volatile var fired = false
        timer.newTimeout(_ => { fired = true }, 0, MILLISECONDS)
        Thread.sleep(300)
        assert(fired)
    }

    // ---- Periodic timeouts ----

    test("Periodic timeout fires repeatedly") {
        val timer = new HashedWheelTimer(system)
        @volatile var count = 0
        timer.newTimeout(
          _ => { count += 1 },
          200,
          MILLISECONDS,
          200,
          MILLISECONDS
        )
        Thread.sleep(1100)
        timer.stop
        // Should fire roughly 5-6 times (first at ~200ms, then every ~200ms)
        assert(count >= 4, s"Expected at least 4 fires, got $count")
    }

    test("Periodic timeout can be cancelled") {
        val timer = new HashedWheelTimer(system)
        @volatile var count = 0
        val timeout = timer.newTimeout(
          _ => { count += 1 },
          100,
          MILLISECONDS,
          100,
          MILLISECONDS
        )
        Thread.sleep(550)
        timeout.cancel
        val countAfterCancel = count
        Thread.sleep(500)
        // Should not fire after cancel
        assert(count == countAfterCancel, s"Fires continued after cancel: $countAfterCancel -> $count")
        timer.stop
    }

    test("Period less than tick duration is clamped") {
        val timer = new HashedWheelTimer(system)
        @volatile var count       = 0
        val start                 = System.currentTimeMillis()
        @volatile var spend: Long = 0
        val timeout = timer.newTimeout(
          t => {
              spend = System.currentTimeMillis() - start
              count += 1
              if (count == 11) t.cancel
          },
          1,
          SECONDS,
          10,
          MILLISECONDS
        )
        Thread.sleep(3 * 1000)
        assert(spend > 1 * 1000 + 10 * 10)
        assert(spend / 1000 == 2)
    }

    // ---- Stop ----

    test("Stop returns unprocessed timeouts") {
        val timer = new HashedWheelTimer(system)
        val timeout1 = timer.newTimeout(_ => {}, 10, SECONDS)
        val timeout2 = timer.newTimeout(_ => {}, 11, SECONDS)
        val timeout3 = timer.newTimeout(_ => {}, 60, MILLISECONDS)
        Thread.sleep(200)
        val unprocessed = timer.stop
        assert(unprocessed.size == 2)
        assert(unprocessed.contains(timeout1))
        assert(unprocessed.contains(timeout2))
        assert(!unprocessed.contains(timeout3))
    }

    // ---- FixTime trigger ----

    test("FixTime with nanoTime deadline") {
        val timer = new HashedWheelTimer(system)
        @volatile var count = 0
        val deadline = System.nanoTime() + 500 * 1000 * 1000L // 500ms from now
        timer.newTimeout(_ => { count += 1 }, deadline - System.nanoTime(), NANOSECONDS)
        Thread.sleep(1000)
        assert(count == 1)
        timer.stop
    }

    // ---- Multiple timeouts ordering ----

    test("Multiple timeouts with distinct tick-aligned delays fire in order") {
        // Use delays that are multiples of tick duration (100ms) to avoid same-tick coalescing
        val timer = new HashedWheelTimer(system, tickDuration = 50, unit = MILLISECONDS, ticksPerWheel = 64)
        @volatile var order = List.empty[Int]
        timer.newTimeout(_ => { order = order :+ 3 }, 300, MILLISECONDS)
        timer.newTimeout(_ => { order = order :+ 1 }, 100, MILLISECONDS)
        timer.newTimeout(_ => { order = order :+ 2 }, 200, MILLISECONDS)
        Thread.sleep(600)
        assert(order == List(1, 2, 3), s"Expected List(1, 2, 3), got $order")
        timer.stop
    }

    // ---- Custom tick duration and wheel size ----

    test("Custom tick duration and wheel size") {
        val timer = new HashedWheelTimer(system, tickDuration = 50, unit = MILLISECONDS, ticksPerWheel = 64)
        @volatile var fired = false
        timer.newTimeout(_ => { fired = true }, 100, MILLISECONDS)
        Thread.sleep(300)
        assert(fired)
        timer.stop
    }

    // ---- Task exception handling ----

    test("Task exception does not crash timer") {
        val timer = new HashedWheelTimer(system)
        @volatile var secondFired = false
        timer.newTimeout(_ => { throw new RuntimeException("test exception") }, 50, MILLISECONDS)
        timer.newTimeout(_ => { secondFired = true }, 150, MILLISECONDS)
        Thread.sleep(400)
        assert(secondFired, "Second task should still fire after first task threw exception")
        timer.stop
    }

    // ---- Delay fixed time (one-shot) ----

    test("One-shot delay fires exactly once") {
        val timer = new HashedWheelTimer(system)
        @volatile var count: Int = 0
        timer.newTimeout(_ => { count += 1 }, 1, SECONDS)
        Thread.sleep(2 * 1000)
        assert(count == 1)
        timer.stop
    }

    // ---- Period delay with cancel after N fires ----

    test("Period delay cancels after N fires") {
        val timer = new HashedWheelTimer(system)
        @volatile var count: Int = 0
        timer.newTimeout(
          timeout => {
              count += 1
              if (count == 4) timeout.cancel
          },
          1,
          SECONDS,
          1,
          SECONDS
        )
        Thread.sleep(6 * 1000)
        timer.stop
        assert(count == 4)
    }

}
