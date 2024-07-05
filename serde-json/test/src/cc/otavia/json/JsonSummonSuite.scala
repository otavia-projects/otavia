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

package cc.otavia.json

import cc.otavia.buffer.BufferAllocator
import cc.otavia.buffer.pool.{AdaptiveBuffer, HeapPooledPageAllocator}
import cc.otavia.serde.annotation.stringfield
import org.scalatest.funsuite.AnyFunSuite

import java.time.YearMonth
import scala.language.unsafeNulls

class JsonSummonSuite extends AnyFunSuite {

    test("primary type") {}

    test("Seq[T]") {}

    test("tuple") {
        val buffer = AdaptiveBuffer(new HeapPooledPageAllocator())
        val tps    = JsonSerde.derived[(String, Int, Long, Option[Long])]

        tps.serialize(("hello", 91, 56789, Some(90)), buffer)
        tps.deserialize(buffer)

        assert(true)
    }

    test("array") {
        val buffer = AdaptiveBuffer(new HeapPooledPageAllocator())
        val tps    = JsonSerde.derived[Array[Int]]

        tps.serialize(Array(1, 2, 3, 4, 5), buffer)
        assert(true)
    }

    test("map") {
        val buffer = AdaptiveBuffer(new HeapPooledPageAllocator())
        val tps    = JsonSerde.derived[Map[Int, Long]]

        tps.serialize(Map(1 -> 2, 2 -> 3), buffer)

        assert(true)
    }

    test("case class") {
        val buffer = AdaptiveBuffer(new HeapPooledPageAllocator())
        val tps    = summon[JsonSerde[Hello]]

        tps.serialize(Hello(1, "world", YearMonth.now()), buffer)

        assert(true)
    }

    test("derived case class") {
        val buffer = AdaptiveBuffer(new HeapPooledPageAllocator())
        val tps    = summon[JsonSerde[Hello]]

        // tps.serialize(World(1, "world"), buffer)

        assert(true)
    }

}
