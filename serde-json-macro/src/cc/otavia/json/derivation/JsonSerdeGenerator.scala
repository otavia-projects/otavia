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

import cc.otavia.buffer.Buffer
import cc.otavia.json.{JsonConstants, JsonSerde}
import magnolia1.{AutoDerivation, CaseClass, SealedTrait}
import shapeless3.deriving.*

import scala.collection.AbstractIterable
import scala.compiletime.*
import scala.deriving.Mirror

object JsonSerdeGenerator extends AutoDerivation[JsonSerde] {

    override def split[T](sealedTrait: SealedTrait[JsonSerdeGenerator.Typeclass, T]): JsonSerde[T] = ???

    override def join[T](caseClass: CaseClass[JsonSerdeGenerator.Typeclass, T]): JsonSerde[T] = new JsonSerde[T] {

        override def deserialize(in: Buffer): T = ???

        override def serialize(value: T, out: Buffer): Unit = {
            serializeObjectStart(out)
            caseClass.params.foreach { param =>
                serializeKey(param.label, out)
                param.typeclass.serialize(param.deref(value), out)
                out.writeByte(JsonConstants.TOKEN_COMMA)
            }
            out.writerOffset(out.writerOffset - 1)
            serializeObjectEnd(out)
        }

    }

}
