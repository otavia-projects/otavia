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

package cc.otavia.http.deriving

import cc.otavia.buffer.Buffer
import cc.otavia.http.server.RouterMatcher
import cc.otavia.http.{AbstractParameterSerde, ParameterSerde}
import magnolia1.{CaseClass, ProductDerivation}

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import scala.collection.mutable
import scala.language.unsafeNulls

object ParamsSerdeGenerator extends ProductDerivation[ParameterSerde] {
    override def join[T](caseClass: CaseClass[ParameterSerde, T]): ParameterSerde[T] = new AbstractParameterSerde[T] {

        override def deserialize(in: Buffer): T =
            caseClass.construct(p => params.getOrElse(p.label, pathVars(p.label)))

        override def serialize(value: T, out: Buffer): Unit = if (params.nonEmpty) {
            for (param <- caseClass.params) {
                val key = URLEncoder.encode(param.label, RouterMatcher.URL_CHARSET).getBytes(StandardCharsets.US_ASCII)
                out.writeBytes(key)
                out.writeByte('=')
                param.typeclass.serialize(param.deref(value), out)
                out.writeByte('&')
            }
        }

    }

}
