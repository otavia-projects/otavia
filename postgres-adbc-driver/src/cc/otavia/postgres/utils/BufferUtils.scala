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

package cc.otavia.postgres.utils

import cc.otavia.buffer.Buffer

import java.nio.charset.{Charset, StandardCharsets}
import scala.language.unsafeNulls

object BufferUtils {

    private val ZERO: Byte                = 0
    private val FIRST_HALF_BYTE_MASK: Int = 0x0f

    def readCString(buffer: Buffer, charset: Charset = StandardCharsets.UTF_8): String = {
        val s = buffer.readCharSequence(buffer.bytesBefore(ZERO), charset).toString
        buffer.readByte
        s
    }

    def writeCString(buffer: Buffer, s: String, charset: Charset = StandardCharsets.UTF_8): Unit = {
        buffer.writeCharSequence(s, charset)
        buffer.writeByte(ZERO)
    }

    def writeCSting(buffer: Buffer, bytes: Array[Byte]): Unit = {
        buffer.writeBytes(bytes)
        buffer.writeByte(ZERO)
    }

}
