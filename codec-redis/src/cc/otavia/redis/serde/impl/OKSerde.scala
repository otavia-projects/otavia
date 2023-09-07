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
import cc.otavia.redis.RedisProtocolException
import cc.otavia.redis.cmd.*
import cc.otavia.redis.serde.{AbstractResponseSerde, RedisSerde}

object OKSerde extends AbstractResponseSerde[OK] {

    private val SIMPLE_STARTS: Array[Byte] = Array('-', '+')

    private val OK_BYTES: Array[Byte] = Array('+', 'O', 'K', '\r', '\n')

    override def checkDeserializable(in: Buffer): Boolean = {
        if (in.nextIn(SIMPLE_STARTS)) {
            -1 != in.bytesBefore('\r'.toByte, '\n')
        } else false
    }

    final override def deserialize(in: Buffer): OK = {
        if (in.skipIfNexts(OK_BYTES)) {
            OK()
        } else if (in.skipIfNext('-')) {
            val errorMsg = deserializeSimpleError(in)
            throw new CommandException(errorMsg)
        } else throw new RedisProtocolException(s"except byte '-' or '+', but get '${in.getByte(in.readerOffset)}'")
    }

    final override def serialize(value: OK, out: Buffer): Unit = {
        out.writeByte('+')
        out.writeByte('O')
        out.writeByte('K')
        out.writeByte('\r')
        out.writeByte('\n')
    }

}
