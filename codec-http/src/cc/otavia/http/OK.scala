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

package cc.otavia.http

import cc.otavia.buffer.Buffer
import cc.otavia.core.message.Reply
import cc.otavia.http.server.HttpResponseSerde
import cc.otavia.serde.Serde

/** A http response with no body */
case class OK() extends Reply

object OK {

    val serde: Serde[OK] = new Serde[OK] {

        override def serialize(value: OK, out: Buffer): Unit = {}

        override def deserialize(in: Buffer): OK = OK()

    }

    val responseSerde = new HttpResponseSerde[OK](serde)

}
