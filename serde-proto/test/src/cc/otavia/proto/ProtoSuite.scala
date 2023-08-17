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

package cc.otavia.proto

import org.scalatest.funsuite.AnyFunSuite
import proto.*

import scala.collection.immutable.TreeMap

class ProtoSuite extends AnyFunSuite {

    test("third library ") {
        final case class VectorClock(versions: TreeMap[String, Long])
        @RestrictedN(3, 4)
        final case class Equipment(@N(1) id: String, @N(2) tpe: String)
        final case class Car(id: String, color: Int, equipment: List[Equipment], vc: VectorClock)

        implicit val tuple2Codec = caseCodecIdx[Tuple2[String, Long]] // codec for TreeMap[String, Long]

        implicit val vectorClockCodec = caseCodecIdx[VectorClock]
        implicit val equipmentCodec   = caseCodecAuto[Equipment]
        implicit val carCodec         = caseCodecNums[Car](("id", 1), ("color", 4), ("equipment", 2), ("vc", 3))

        val vc        = VectorClock(versions = TreeMap.empty)
        val equipment = List(Equipment(id = "1", tpe = "123"), Equipment(id = "2", tpe = "456"))
        val car       = Car(id = "1", color = 16416882, equipment = equipment, vc = vc)
        // encode
        val bytes: Array[Byte] = encode(car)
        // decode
        val car2: Car = decode[Car](bytes)

        assert(car2 == car2)
    }

}
