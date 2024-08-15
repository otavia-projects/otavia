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

package cc.otavia.sql

import org.scalatest.funsuite.AnyFunSuiteLike

class MacroSuite extends AnyFunSuiteLike {

    import MacroSuite.*

    test("derived") {
        val decoder = summon[RowCodec[World]]
    }

    test("derived RowCodec Tuple") {
        val codec = RowCodec.derived[(Int, String, Option[Int])]
    }

    test("derived RowCodec Case Class") {
        val codec = RowCodec.derived[Person]
    }

}

object MacroSuite {
    case class Person(id: Int, name: String, sonAge: Option[Int]) derives RowCodec
}
