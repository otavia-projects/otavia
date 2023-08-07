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

package cc.otavia.core.stack

import org.scalatest.funsuite.AnyFunSuite

import java.util

class FutureDispatcherSuite extends AnyFunSuite {

    test("tableSizeFor") {
        for (i <- 0 to 8) {
            assert(FutureDispatcher.tableSizeFor(i) == 8)
        }
        for (i <- 9 to 16) {
            assert(FutureDispatcher.tableSizeFor(i) == 16)
        }
        for (i <- 17 to 32) {
            assert(FutureDispatcher.tableSizeFor(i) == 32)
        }
        for (i <- 33 to 64) {
            assert(FutureDispatcher.tableSizeFor(i) == 64)
        }
        for (i <- 65 to 128) {
            assert(FutureDispatcher.tableSizeFor(i) == 128)
        }
        for (i <- 129 to 256) {
            assert(FutureDispatcher.tableSizeFor(i) == 256)
        }
    }

    test("mod hash 8") {
        val mask    = 8 - 1
        val counter = new Array[Int](8)
        util.Arrays.fill(counter, 0)
        for (i <- -1000 to 1000) {
            counter(i & mask) += 1
        }

        assert(true)
    }

}
