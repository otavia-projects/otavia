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

package cc.otavia.redis.serde.impl

import cc.otavia.buffer.Buffer
import cc.otavia.redis.cmd.{Auth, Info}
import cc.otavia.redis.serde.AbstractCommandSerde

object AuthSerde extends AbstractCommandSerde[Auth] {

    override def deserialize(in: Buffer): Auth = ???

    override def serialize(value: Auth, out: Buffer): Unit = {
        serializeArrayHeader(2, out)
        serializeBulkString("auth", out)
        serializeBulkString(value.password, out)
    }

}
