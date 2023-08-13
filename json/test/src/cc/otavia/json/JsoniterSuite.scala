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

import cc.otavia.json.JsoniterSuite.*
import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*
import org.scalatest.funsuite.AnyFunSuite

class JsoniterSuite extends AnyFunSuite {

    test("encode") {
        given CodecMakerConfig.PrintCodec with {}

        given codec: JsonValueCodec[User] = JsonCodecMaker.make

        val user = readFromString[User]("""{"name":"John","devices":[{"id":1,"model":"HTC One X"}]}""")
        val json = writeToString(User("John", Seq(Device(2, "iPhone X"))))


        val user2 = readFromString[User]("""{"name": "Yan Kun", "devices":  []}""")
        val user3 = readFromString[User]("""{"name": "Yan Kun"}""")

        assert(true)
    }

}

object JsoniterSuite {

    case class Device(id: Int, model: String)

    case class User(name: String, devices: Seq[Device])

}
