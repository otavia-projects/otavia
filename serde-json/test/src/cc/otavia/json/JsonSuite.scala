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

import cc.otavia.buffer.Buffer
import org.scalatest.funsuite.AnyFunSuite

import java.nio.charset.StandardCharsets
import scala.language.unsafeNulls

class JsonSuite extends AnyFunSuite {

    import JsonSuite.*

    test("byte json") {
        val b: Byte = 'c'
        val serde   = JsonSerde.derived[Byte]
        val buffer  = Buffer.wrap(new Array[Byte](1024)).clean()
        serde.serialize(b, buffer)
        val bytes = b.toInt.toString.getBytes(StandardCharsets.US_ASCII)
        assert(buffer.skipIfNextAre(bytes))
        buffer.writeBytes(bytes)
        assert(serde.deserialize(buffer) == b)
    }

    test("derives") {
        val user = User(1, "Tom")

        val serde = summon[JsonSerde[User]]
    }

}

object JsonSuite {

    case class User(id: Int, name: String) // derives JsonSerde

    object User {

        // TODO: auto derives this
        given userJsonSerde: JsonSerde[User] with {

            override def deserialize(in: Buffer): User = {
                ???
            }

            override def serialize(value: User, out: Buffer): Unit = {
                serializeObjectStart(out)
                serializeKey("id", out)
                out.writeByte(JsonConstants.TOKEN_COLON)
                serializeInt(value.id, out)

                out.writeByte(JsonConstants.TOKEN_COMMA)
                serializeKey("name", out)
                out.writeByte(JsonConstants.TOKEN_COLON)
                serializeString(value.name, out)

                serializeObjectEnd(out)
            }

        }
    }

}
