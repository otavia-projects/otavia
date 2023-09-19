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

package cc.otavia.sql.serde

import cc.otavia.buffer.Buffer
import cc.otavia.serde.Serde
import cc.otavia.sql.ResultSetParser

trait RowSerde[R] extends Serde[R] {

    private var resultSetParser: ResultSetParser = _

    private[sql] def setParser(parser: ResultSetParser): Unit = this.resultSetParser = parser

    protected def parser: ResultSetParser = resultSetParser

    def parse(p: ResultSetParser, index: Int): R

    def parse(p: ResultSetParser, name: String): R

}

object RowSerde {

    private sealed abstract class Column[C] extends RowSerde[C] {

        override def deserialize(in: Buffer): C = throw new UnsupportedOperationException()

        override def serialize(value: C, out: Buffer): Unit = throw new UnsupportedOperationException()

    }

    given intCol: RowSerde[Int] = new Column[Int] {

        override def parse(p: ResultSetParser, index: Int): Int = p.parseInt(index)

        override def parse(p: ResultSetParser, name: String): Int = ???

    }
    given charCol: RowSerde[Char] = new Column[Char] {

        override def parse(p: ResultSetParser, index: Int): Char = ???

        override def parse(p: ResultSetParser, name: String): Char = ???

    }

}
