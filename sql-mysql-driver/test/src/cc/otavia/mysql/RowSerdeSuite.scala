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

package cc.otavia.mysql

import cc.otavia.buffer.Buffer
import cc.otavia.core.message.Reply
import cc.otavia.sql.RowParser
import cc.otavia.sql.serde.RowSerde
import org.scalatest.funsuite.AnyFunSuite

class RowSerdeSuite extends AnyFunSuite {

    test("generate row serde by hand") {
        assert(true)
    }

}

object RowSerdeSuite {

    private case class User(id: Int, name: String, address: String) extends Reply

    private val userSerde: RowSerde[User] = new RowSerde[User] {

        override def deserialize(in: Buffer): User = {
            User(parser.parseInt(0), parser.parseString(1), parser.parseString(2))
        }

        override def serialize(value: User, out: Buffer): Unit = ???

    }

}
