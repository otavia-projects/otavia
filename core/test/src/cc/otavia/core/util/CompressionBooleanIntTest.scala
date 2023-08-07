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

import org.scalatest.funsuite.AnyFunSuite

import scala.util.Random

class CompressionBooleanIntTest extends AnyFunSuite {

    test("CompressionBooleanInt") {
        val compressionBooleanInt = new CompressionBooleanInt {}

        for (idx <- 0 until 32) {
            assert(!compressionBooleanInt.getAt(idx))
        }

        for (idx <- 0 until 32) {
            compressionBooleanInt.setAt(idx, true)
            assert(compressionBooleanInt.getAt(idx))

            compressionBooleanInt.setAt(idx, false)
            assert(!compressionBooleanInt.getAt(idx))
        }

        0 to 500 foreach { _ =>
            val random  = new Random(System.currentTimeMillis())
            val shuffle = (0 until 32).map(i => (i, Random.nextBoolean()))

            for (elem <- shuffle) {
                compressionBooleanInt.setAt(elem._1, elem._2)
            }

            for (elem <- shuffle) {
                assert(compressionBooleanInt.getAt(elem._1) == elem._2)
            }
        }
    }
}
