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

package cc.otavia.json.derivation

import cc.otavia.buffer.{Buffer, BufferAllocator}
import org.scalatest.funsuite.AnyFunSuite

import java.nio.charset.{Charset, StandardCharsets}
import scala.language.unsafeNulls

class GeneratorSuite extends AnyFunSuite {

    import GeneratorSuite.*

    test("serde") {
        given charset: Charset = StandardCharsets.UTF_8
        val device             = Device(1, "iPhone 14")
        val serde              = JsonSerdeGenerator.derived[Device]

        val allocator = BufferAllocator.onHeapAllocator()
        val buffer    = allocator.allocate(4096)

        serde.serialize(device, buffer)

        println(buffer.readCharSequence(buffer.readableBytes))
        assert(true)

    }

}

object GeneratorSuite {
    case class Device(id: Int, name: String)
}
