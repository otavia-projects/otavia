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

package cc.otavia.buffer.utils

import cc.otavia.buffer.Buffer

import java.util.UUID
import scala.language.unsafeNulls

trait BufferStringUtils extends BufferBaseUtils {

    final def readEscapedChar(buffer: Buffer): Char = {
        val b1 = buffer.readByte
        if (b1 >= 0) {
            if (b1 != '\\') b1.toChar
            else {
                val b2 = buffer.readByte
                if (b2 != 'u') byteToChar(b2)
                else {
                    val ns = BufferConstants.nibbles
                    val x = ns(buffer.readByte & 0xff) << 12 |
                        ns(buffer.readByte & 0xff) << 8 |
                        ns(buffer.readByte & 0xff) << 4 |
                        ns(buffer.readByte & 0xff)
                    x.toChar
                }
            }
        } else if ((b1 >> 5) == -2) { // 110bbbbb 10aaaaaa (UTF-8 bytes) -> 00000bbbbbaaaaaa (UTF-16 char)
            val b2 = buffer.readByte
            (b1 << 6 ^ b2 ^ 0xf80).toChar // 0xF80 == 0xC0.toByte << 6 ^ 0x80.toByte
        } else if ((b1 >> 4) == -2) {     // 1110cccc 10bbbbbb 10aaaaaa (UTF-8 bytes) -> ccccbbbbbbaaaaaa (UTF-16 char)
            val b2 = buffer.readByte
            val b3 = buffer.readByte
            val ch =
                (b1 << 12 ^ b2 << 6 ^ b3 ^ 0xfffe1f80).toChar // 0xFFFE1F80 == 0xE0.toByte << 12 ^ 0x80.toByte << 6 ^ 0x80.toByte
            ch
        } else throw new IllegalStateException("illegal surrogate character")
    }

    final def getEscapedChar(index: Int, buffer: Buffer): Char = {
        val b1 = buffer.getByte(index)
        if (b1 >= 0) {
            if (b1 != '\\') {
                b1.toChar
            } else {
                val b2 = buffer.getByte(index + 1)
                if (b2 != 'u') byteToChar(b2)
                else {
                    val ns = BufferConstants.nibbles
                    val x = ns(buffer.getByte(index + 2) & 0xff) << 12 |
                        ns(buffer.getByte(index + 3) & 0xff) << 8 |
                        ns(buffer.getByte(index + 4) & 0xff) << 4 |
                        ns(buffer.getByte(index + 5) & 0xff)
                    x.toChar
                }
            }
        } else if ((b1 >> 5) == -2) { // 110bbbbb 10aaaaaa (UTF-8 bytes) -> 00000bbbbbaaaaaa (UTF-16 char)
            val b2 = buffer.getByte(index + 1)
            (b1 << 6 ^ b2 ^ 0xf80).toChar // 0xF80 == 0xC0.toByte << 6 ^ 0x80.toByte
        } else if ((b1 >> 4) == -2) {     // 1110cccc 10bbbbbb 10aaaaaa (UTF-8 bytes) -> ccccbbbbbbaaaaaa (UTF-16 char)
            val b2 = buffer.getByte(index + 1)
            val b3 = buffer.getByte(index + 2)
            val ch =
                (b1 << 12 ^ b2 << 6 ^ b3 ^ 0xfffe1f80).toChar // 0xFFFE1F80 == 0xE0.toByte << 12 ^ 0x80.toByte << 6 ^ 0x80.toByte
            ch
        } else throw new IllegalStateException("illegal surrogate character")
    }

    final def writeEscapedChar(buffer: Buffer, ch: Char): Unit = {
        if (ch < 0x80) {
            val ecs = BufferConstants.escapedChars(ch)
            if (ecs == 0) { // 00000000 0aaaaaaa (UTF-16 char) -> 0aaaaaaa (UTF-8 byte)
                buffer.writeByte(ch.toByte)
            } else if (ecs > 0) {
                buffer.writeShortLE((ecs << 8 | 0x5c).toShort)
            } else {
                val ds = BufferConstants.lowerCaseHexDigits
                buffer.writeLongLE(ds(ch).toLong << 32 | 0x00003030755cL)
                buffer.writerOffset(buffer.writerOffset - 2)
            }
        } else if (ch < 0x800) { // 00000bbb bbaaaaaa (UTF-16 char) -> 110bbbbb 10aaaaaa (UTF-8 bytes)
            buffer.writeShortLE((ch >> 6 | (ch << 8 & 0x3f00) | 0x80c0).toShort)
        } else if (ch < 0xd800 || ch > 0xdfff) { // ccccbbbbbbaaaaaa (UTF-16 char) -> 1110cccc 10bbbbbb 10aaaaaa (UTF-8 bytes)
            buffer.writeMediumLE(ch >> 12 | (ch << 2 & 0x3f00) | (ch << 16 & 0x3f0000) | 0x8080e0)
        }
    }

    final def writeEscapedCharWithQuote(buffer: Buffer, ch: Char): Unit = {
        if (ch < 0x80) { // 00000000 0aaaaaaa (UTF-16 char) -> 0aaaaaaa (UTF-8 byte)
            val ecs = BufferConstants.escapedChars(ch)
            if (ecs == 0) {
                buffer.writeMediumLE(ch << 8 | 0x220022) // 0x220022 == " "
            } else if (ecs > 0) {
                buffer.writeIntLE(ecs << 16 | 0x22005c22) // 0x22005C22 == "\ "
            } else {
                val ds = BufferConstants.lowerCaseHexDigits
                buffer.writeLongLE(ds(ch).toLong << 40 | 0x2200003030755c22L)
            }
        } else if (ch < 0x800) { // 00000bbb bbaaaaaa (UTF-16 char) -> 110bbbbb 10aaaaaa (UTF-8 bytes)
            buffer.writeIntLE((ch & 0x3f) << 16 | (ch & 0xfc0) << 2 | 0x2280c022)
        } else if (ch < 0xd800 || ch > 0xdfff) { // ccccbbbbbbaaaaaa (UTF-16 char) -> 1110cccc 10bbbbbb 10aaaaaa (UTF-8 bytes)
            buffer.writeLongLE(((ch & 0x3f) << 24 | (ch & 0xfc0) << 10 | (ch & 0xf000) >> 4) | 0x228080e022L)
            buffer.writerOffset(buffer.writerOffset - 3)
        }
    }

    final def readEscapedString(buffer: Buffer, len: Int): String = {
        val cs = buffer.readCharSequence(len).toString
        cs.translateEscapes()
    }

    final def writeEscapedString(buffer: Buffer, str: String): Unit = {
        var i = 0
        while (i < str.length()) {
            val ch = str.charAt(i)
            if (ch < 0x80) {
                val ecs = BufferConstants.escapedChars(ch)
                if (ecs == 0) { // 00000000 0aaaaaaa (UTF-16 char) -> 0aaaaaaa (UTF-8 byte)
                    buffer.writeByte(ch.toByte)
                } else if (ecs > 0) {
                    buffer.writeShortLE((ecs << 8 | 0x5c).toShort)
                }
            } else if (ch < 0x800) { // 00000bbb bbaaaaaa (UTF-16 char) -> 110bbbbb 10aaaaaa (UTF-8 bytes)
                buffer.writeShortLE((ch >> 6 | (ch << 8 & 0x3f00) | 0x80c0).toShort)
            } else if (ch < 0xd800 || ch > 0xdfff) { // ccccbbbbbbaaaaaa (UTF-16 char) -> 1110cccc 10bbbbbb 10aaaaaa (UTF-8 bytes)
                buffer.writeMediumLE(ch >> 12 | (ch << 2 & 0x3f00) | (ch << 16 & 0x3f0000) | 0x8080e0)
            } else {
                // 110110uuuuccccbb 110111bbbbaaaaaa (UTF-16 chars) -> 11110ddd 10ddcccc 10bbbbbb 10aaaaaa (UTF-8 bytes), where ddddd = uuuu + 1
                val ch2 = str.charAt(i + 1)
                i += 1
                val cp = (ch << 10) + (ch2 - 56613888) // -56613888 == 0x10000 - (0xD800 << 10) - 0xDC00
                buffer.writeIntLE(
                  cp >> 18 | (cp >> 4 & 0x3f00) | (cp << 10 & 0x3f0000) | (cp << 24 & 0x3f000000) | 0x808080f0
                )
            }
            i += 1
        }
    }

    /** Parses the string content stored in the buffer as a [[UUID]].
     *
     *  @param buffer
     *    the buffer to read.
     *  @param index
     *    The read offset, an absolute offset into this buffer, to read from.
     *  @return
     *    The UUID represented by the string content.
     */
    final def getStringAsUUID(buffer: Buffer, index: Int): UUID = {
        val ns = BufferConstants.nibbles
        val msb1 = ns(buffer.getByte(index) & 0xff).toLong << 28 | (
          ns(buffer.getByte(index + 1) & 0xff) << 24 |
              ns(buffer.getByte(index + 2) & 0xff) << 20 |
              ns(buffer.getByte(index + 3) & 0xff) << 16 |
              ns(buffer.getByte(index + 4) & 0xff) << 12 |
              ns(buffer.getByte(index + 5) & 0xff) << 8 |
              ns(buffer.getByte(index + 6) & 0xff) << 4 |
              ns(buffer.getByte(index + 7) & 0xff)
        )
        if (msb1 < 0) throw new RuntimeException()
        if (buffer.getByte(index + 8) != '-') throw new RuntimeException()

        val msb2 = ns(buffer.getByte(index + 9) & 0xff) << 12 |
            ns(buffer.getByte(index + 10) & 0xff) << 8 |
            ns(buffer.getByte(index + 11) & 0xff) << 4 |
            ns(buffer.getByte(index + 12) & 0xff)
        if (msb2 < 0) throw new RuntimeException()
        if (buffer.getByte(index + 13) != '-') throw new RuntimeException()

        val msb3 = ns(buffer.getByte(index + 14) & 0xff) << 12 |
            ns(buffer.getByte(index + 15) & 0xff) << 8 |
            ns(buffer.getByte(index + 16) & 0xff) << 4 |
            ns(buffer.getByte(index + 17) & 0xff)
        if (msb3 < 0) throw new RuntimeException()
        if (buffer.getByte(index + 18) != '-') throw new RuntimeException()

        val lsb1 = ns(buffer.getByte(index + 19) & 0xff) << 12 |
            ns(buffer.getByte(index + 20) & 0xff) << 8 |
            ns(buffer.getByte(index + 21) & 0xff) << 4 |
            ns(buffer.getByte(index + 22) & 0xff)
        if (lsb1 < 0) throw new RuntimeException()
        if (buffer.getByte(index + 23) != '-') throw new RuntimeException()

        val lsb2 = (ns(buffer.getByte(index + 24) & 0xff) << 16 |
            ns(buffer.getByte(index + 25) & 0xff) << 12 |
            ns(buffer.getByte(index + 26) & 0xff) << 8 |
            ns(buffer.getByte(index + 27) & 0xff) << 4 |
            ns(buffer.getByte(index + 28) & 0xff)).toLong << 28 |
            (ns(buffer.getByte(index + 29) & 0xff) << 24 |
                ns(buffer.getByte(index + 30) & 0xff) << 20 |
                ns(buffer.getByte(index + 31) & 0xff) << 16 |
                ns(buffer.getByte(index + 32) & 0xff) << 12 |
                ns(buffer.getByte(index + 33) & 0xff) << 8 |
                ns(buffer.getByte(index + 34) & 0xff) << 4 |
                ns(buffer.getByte(index + 35) & 0xff))
        if (lsb2 < 0) throw new RuntimeException()

        new UUID(msb1 << 32 | msb2.toLong << 16 | msb3, lsb1.toLong << 48 | lsb2)
    }

    /** Parses the string content stored in the buffer as a [[UUID]].
     *
     *  @param buffer
     *    the buffer to read.
     *  @return
     *    The UUID represented by the string content.
     */
    final def readStringAsUUID(buffer: Buffer): UUID = {
        val uuid = getStringAsUUID(buffer, buffer.readerOffset)
        buffer.skipReadableBytes(36)
        uuid
    }

    /** Writes into this buffer, all the bytes from the given [[uuid]] string. This updates the [[writerOffset]] of this
     *  buffer.
     *
     *  @param buffer
     *    the buffer to write.
     *  @param uuid
     *    uuid value.
     */
    final def writeUUIDAsString(buffer: Buffer, uuid: UUID): Unit = {
        val index = buffer.writerOffset
        buffer.writerOffset(index + 36)

        setUUIDAsString(buffer, index, uuid)
    }

    /** Writes into this buffer, all the bytes from the given [[uuid]] string. This not updates the [[writerOffset]] of
     *  this buffer.
     *
     *  @param buffer
     *    the buffer to write.
     *  @param index
     *    The write offset, an absolute offset into this buffer to write to.
     *  @param uuid
     *    uuid value.
     */
    final def setUUIDAsString(buffer: Buffer, index: Int, uuid: UUID): Unit = {
        val mostSigBits  = uuid.getMostSignificantBits
        val leastSigBits = uuid.getLeastSignificantBits
        val ds           = BufferConstants.lowerCaseHexDigits

        val mostSigBits1 = (mostSigBits >> 32).toInt
        val d1           = ds(mostSigBits1 >>> 24)
        val d2           = ds(mostSigBits1 >> 16 & 0xff).toLong << 16
        val d3           = ds(mostSigBits1 >> 8 & 0xff).toLong << 32
        val d4           = ds(mostSigBits1 & 0xff)
        buffer.setLongLE(index, d1 | d2 | d3 | d4.toLong << 48)

        val mostSigBits2 = mostSigBits.toInt
        val d5           = ds(mostSigBits2 >>> 24) << 8
        val d6           = ds(mostSigBits2 >> 16 & 0xff).toLong << 24
        val d7           = ds(mostSigBits2 >> 8 & 0xff)
        buffer.setLongLE(index + 8, d5 | d6 | d7.toLong << 48 | 0x2d000000002dL)

        val d8            = ds(mostSigBits2 & 0xff)
        val leastSigBits1 = (leastSigBits >> 32).toInt
        val d9            = ds(leastSigBits1 >>> 24).toLong << 24
        val d10           = ds(leastSigBits1 >> 16 & 0xff).toLong << 40
        buffer.setLongLE(index + 16, d8 | d9 | d10 | 0x2d000000002d0000L)

        val d11           = ds(leastSigBits1 >> 8 & 0xff)
        val d12           = ds(leastSigBits1 & 0xff).toLong << 16
        val leastSigBits2 = leastSigBits.toInt
        val d13           = ds(leastSigBits2 >>> 24).toLong << 32
        val d14           = ds(leastSigBits2 >> 16 & 0xff)
        buffer.setLongLE(index + 24, d11 | d12 | d13 | d14.toLong << 48)

        val d15 = ds(leastSigBits2 >> 8 & 0xff)
        val d16 = ds(leastSigBits2 & 0xff).toInt << 16
        buffer.setIntLE(index + 32, d15 | d16)
    }

}
