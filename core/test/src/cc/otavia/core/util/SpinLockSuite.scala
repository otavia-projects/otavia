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

package cc.otavia.core.util

import org.scalatest.funsuite.AnyFunSuiteLike

class SpinLockSuite extends AnyFunSuiteLike {

    test("system call time spend") {
        val start = System.nanoTime()
        var i     = 0
        while (i < 100_000_000) {
            System.nanoTime()
            i += 1
        }

        assert(true)
    }

    test("basic operation time spend") {

        var acc = 0L
        var i   = 0
        while (i < 10_000) {
            acc += System.nanoTime()
            i += 1
        }

        val start = System.nanoTime()
//        println("system call")
//        val st = 0 // System.nanoTime()

        val duration = System.nanoTime() - start
        println(s"system call ${duration} ns ")
//        println(st)
//        println(acc)
        assert(true)
    }

}
