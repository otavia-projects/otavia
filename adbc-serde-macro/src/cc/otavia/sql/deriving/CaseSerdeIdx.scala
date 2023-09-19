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

package cc.otavia.sql.deriving

import cc.otavia.buffer.Buffer
import cc.otavia.sql.ResultSetParser
import cc.otavia.sql.serde.RowSerde
import magnolia1.{AutoDerivation, CaseClass, ProductDerivation, SealedTrait}

object CaseSerdeIdx extends ProductDerivation[RowSerde] {

    override def join[T](caseClass: CaseClass[RowSerde, T]): RowSerde[T] = new RowSerde[T] {

        override def deserialize(in: Buffer): T = {
            caseClass.construct(param => param.typeclass.parse(parser, param.index))
        }

        override def serialize(value: T, out: Buffer): Unit = throw new UnsupportedOperationException()
        
        override def parse(p: ResultSetParser, index: Int): T = throw new UnsupportedOperationException()

        override def parse(p: ResultSetParser, name: String): T = throw new UnsupportedOperationException()

    }

}
