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

package io.otavia.core.util

import org.scalatest.funsuite.AnyFunSuite

import scala.util.Random

class CompressionBooleanLongTest extends AnyFunSuite {

    test("CompressionBooleanLong") {
        val compressionBooleanLong = new CompressionBooleanLong {}

        for (idx <- 0 until 64) {
            assert(!compressionBooleanLong.getAt(idx))
        }

        for (idx <- 0 until 64) {
            compressionBooleanLong.setAt(idx, true)
            assert(compressionBooleanLong.getAt(idx))

            compressionBooleanLong.setAt(idx, false)
            assert(!compressionBooleanLong.getAt(idx))
        }

        0 to 100 foreach { _ =>
            val random  = new Random(System.currentTimeMillis())
            val shuffle = (0 until 64).map(i => (i, Random.nextBoolean()))

            for (elem <- shuffle) {
                compressionBooleanLong.setAt(elem._1, elem._2)
            }

            for (elem <- shuffle) {
                assert(compressionBooleanLong.getAt(elem._1) == elem._2)
            }
        }

    }

}
