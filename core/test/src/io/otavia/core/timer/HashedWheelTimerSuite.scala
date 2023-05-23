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

import io.otavia.core.system.ActorSystem
import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration.*

class HashedWheelTimerSuite extends AnyFunSuite {

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

        Thread.sleep(3 * 1000)
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
              println(System.currentTimeMillis())
              if (count == 10) timeout.cancel
          },
          1,
          SECONDS,
          1,
          SECONDS
        )

        Thread.sleep(15 * 1000)
        assert(count == 10)

    }

}
