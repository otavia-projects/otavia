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

    test("Should not expire") {
        val system = ActorSystem()
        val timer  = new HashedWheelTimer(system)

        val timeout = timer.newTimeout(_ => {}, 10, SECONDS)

        Thread.sleep(50)

        assert(!timeout.isCancelled)
        assert(!timeout.isExpired)
    }

    test("Should expire") {
        val system = ActorSystem()
        val timer  = new HashedWheelTimer(system)

        val timeout = timer.newTimeout(_ => {}, 1, MILLISECONDS)

        Thread.sleep(200)

        assert(timeout.isExpired)
    }

    test("Cancel timeout") {
        val system = ActorSystem()
        val timer  = new HashedWheelTimer(system)

        val timeout = timer.newTimeout(_ => {}, 10, SECONDS)

        Thread.sleep(50)

        timeout.cancel

        assert(!timeout.isExpired)
        assert(timeout.isCancelled)
    }

    test("Period less than duration") {
        val system = ActorSystem()
        val timer  = new HashedWheelTimer(system)

        @volatile var count       = 0
        val start                 = System.currentTimeMillis()
        @volatile var spend: Long = 0
        // period duration is less than tick duration
        val timeout = timer.newTimeout(
          t => {
              spend = System.currentTimeMillis() - start
              count += 1
              if (count == 11) t.cancel
          },
          1,
          SECONDS,
          10, // change to timer duration which is 100ms
          MILLISECONDS
        )

        Thread.sleep(3 * 1000)

        assert(spend > 1 * 1000 + 10 * 10)
        assert((spend / 1000) == 2)

    }

    test("Stop before complete") {
        val system = ActorSystem()
        val timer  = new HashedWheelTimer(system)

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

    test("Delay fixed time") {
        val system = ActorSystem()
        val timer  = new HashedWheelTimer(system)

        @volatile var count: Int = 0

        timer.newTimeout(
          _ => {
              count += 1
          },
          1,
          SECONDS
        )

        Thread.sleep(2 * 1000)
        assert(count == 1)
        timer.stop
    }

    test("Period delay") {
        val system = ActorSystem()
        val timer  = new HashedWheelTimer(system)

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
