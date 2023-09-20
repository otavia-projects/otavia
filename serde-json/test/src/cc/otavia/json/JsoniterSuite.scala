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

import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*
import org.scalatest.funsuite.AnyFunSuite

class JsoniterSuite extends AnyFunSuite {

    import JsoniterSuite.*

    test("jsoniter serialize") {
        given CodecMakerConfig.PrintCodec with {}
        given codec: JsonValueCodec[Device] = JsonCodecMaker.make

        val json = writeToString(Device(-1, 'l', 'l', "iPhone 13"))

        println(json)
    }

    test("jsoniter serialize primaries") {
        given codec: JsonValueCodec[Int] = JsonCodecMaker.make
        val int                          = writeToString(1)

        println(int)
    }

}

object JsoniterSuite {

    case class Device(id: Int, mark: Byte, ch: Char, model: String)

    case class User(name: String, devices: Seq[Device])

}
