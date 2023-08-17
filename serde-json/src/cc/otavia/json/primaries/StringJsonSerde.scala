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

package cc.otavia.json.primaries

import cc.otavia.buffer.Buffer
import cc.otavia.json.JsonSerde

import java.nio.charset.{Charset, StandardCharsets}
import scala.language.unsafeNulls

class StringJsonSerde(override val charsets: Charset = StandardCharsets.UTF_8) extends JsonSerde[String] {

    override def deserialize(in: Buffer): String = deserializeString(in)

    override def serialize(value: String, out: Buffer): Unit = serializeString(value, out)

}

object StringJsonSerde {
    val UTF8StringJsonSerde = new StringJsonSerde()
}
