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

package cc.otavia.redis.serde

import cc.otavia.buffer.Buffer
import cc.otavia.redis.RedisProtocolException
import cc.otavia.redis.cmd.{Command, CommandResponse}

import java.nio.charset.StandardCharsets
import scala.language.unsafeNulls

abstract class AbstractCommandSerde[C <: Command[?] | CommandResponse] extends RedisSerde[C] {

    protected def serializeString(bulk: String, out: Buffer): this.type = {
        out.writeByte('$')
        val bytes = bulk.getBytes(StandardCharsets.UTF_8)
        out.writeCharSequence(bytes.length.toString)
        serializeCRLF(out)
        out.writeBytes(bytes)
        serializeCRLF(out)
        this
    }

    protected def deserializeString(in: Buffer): String = ???

    protected def serializeInteger(value: Long, out: Buffer): this.type = {
        out.writeByte(':')
        out.writeCharSequence(value.toString)
        serializeCRLF(out)
        this
    }

    protected def serializeArrayHeader(len: Int, out: Buffer): this.type = {
        out.writeByte('*')
        out.writeCharSequence(len.toString)
        serializeCRLF(out)
        this
    }

    protected def deserializeArrayHeader(in: Buffer): Int = if (in.skipIfNext('*')) {

        ???
    } else throw new RedisProtocolException(s"except byte '*' but get '${in.getByte(in.readerOffset)}'")

}
