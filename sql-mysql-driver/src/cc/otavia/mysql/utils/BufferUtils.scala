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

package cc.otavia.mysql.utils

import cc.otavia.buffer.Buffer

import java.nio.charset.Charset
import scala.language.unsafeNulls

object BufferUtils {

    private val TERMINAL: Byte = 0x00

    def readNullTerminatedString(buffer: Buffer, charset: Charset): String = {
        val s = buffer.readCharSequence(buffer.bytesBefore(TERMINAL), charset).toString
        buffer.readByte
        s
    }

    def readFixedLengthString(buffer: Buffer, length: Int, charset: Charset): String = {
        buffer.readCharSequence(length, charset).toString
    }

    def writeNullTerminatedString(buffer: Buffer, charSequence: CharSequence, charset: Charset): Unit = {
        buffer.writeCharSequence(charSequence, charset)
        buffer.writeByte(TERMINAL)
    }

    def writeLengthEncodedInteger(buffer: Buffer, value: Long): Unit =
        if (value < 251) {
            //  1-byte integer
            buffer.writeByte(value.toByte)
        } else if (value <= 0xffff) {
            //  0xFC + 2-byte integer
            buffer.writeByte(0xfc.toByte)
            buffer.writeShortLE(value.toShort)
        } else if (value <= 0xffffff) {
            // 0xFD + 3-byte integer
            buffer.writeByte(0xfd.toByte)
            buffer.writeMediumLE(value.toInt)
        } else {
            // 0xFE + 8-byte integer
            buffer.writeByte(0xfe.toByte)
            buffer.writeLongLE(value)
        }

    def readLengthEncodedInteger(buffer: Buffer): Long = buffer.readUnsignedByte match
        case 0xfb      => -1
        case 0xfc      => buffer.readUnsignedShortLE
        case 0xfd      => buffer.readUnsignedMediumLE
        case 0xfe      => buffer.readLongLE
        case firstByte => firstByte

    def writeLengthEncodedString(buffer: Buffer, value: String, charset: Charset): Unit = {
        val bytes = value.getBytes(charset)
        writeLengthEncodedInteger(buffer, bytes.length)
        buffer.writeBytes(bytes)
    }

    def readLengthEncodedString(buffer: Buffer, charset: Charset): String = {
        val length = readLengthEncodedInteger(buffer)
        readFixedLengthString(buffer, length.toInt, charset)
    }

    def readDecStrAsLong(len: Int, buffer: Buffer): Long = {
        var index       = buffer.readerOffset
        var value: Long = 0
        if (len > 0) {
            val to = index + len
            val neg = if (buffer.getByte(index) == '-') {
                index += 1
                true
            } else false
            while (index < to) {
                value = value * 10 + (buffer.getByte(index) - '0')
                index += 1
            }
            if (neg) value = -value
            buffer.skipReadableBytes(len)
        }
        value
    }

}
