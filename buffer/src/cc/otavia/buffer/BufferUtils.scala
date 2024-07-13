/*
 * Copyright 2022 Yan Kun <yan_kun_1992@foxmail.com>
 *
 * Some code ported from jsoniter-scala
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

package cc.otavia.buffer

import java.math.{BigInteger, MathContext}
import java.nio.charset.StandardCharsets
import java.time.{Duration as JDuration, *}
import java.util.UUID
import scala.annotation.switch
import scala.concurrent.duration.Duration
import scala.language.unsafeNulls

object BufferUtils {

    final def readEscapedChar(buffer: Buffer): Char = {
        val b1 = buffer.readByte
        if (b1 >= 0) {
            if (b1 != '\\') {
                b1.toChar
            } else {
                val b2 = buffer.readByte
                if (b2 != 'u') byteToChar(b2)
                else {
                    val ns = nibbles
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
                    val ns = nibbles
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

    private def byteToChar(b2: Byte): Char = (b2: @switch) match
        case 'b'  => '\b'
        case 'f'  => '\f'
        case 'n'  => '\n'
        case 'r'  => '\r'
        case 't'  => '\t'
        case '"'  => '"'
        case '/'  => '/'
        case '\\' => '\\'

    final def writeEscapedChar(buffer: Buffer, ch: Char): Unit = {
        if (ch < 0x80) {
            val ecs = escapedChars(ch)
            if (ecs == 0) { // 00000000 0aaaaaaa (UTF-16 char) -> 0aaaaaaa (UTF-8 byte)
                buffer.writeByte(ch.toByte)
            } else if (ecs > 0) {
                buffer.writeShortLE((ecs << 8 | 0x5c).toShort)
            } else {
                val ds = lowerCaseHexDigits
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
            val ecs = escapedChars(ch)
            if (ecs == 0) {
                buffer.writeMediumLE(ch << 8 | 0x220022) // 0x220022 == " "
            } else if (ecs > 0) {
                buffer.writeIntLE(ecs << 16 | 0x22005c22) // 0x22005C22 == "\ "
            } else {
                val ds = lowerCaseHexDigits
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
                val ecs = escapedChars(ch)
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
        val ns = nibbles
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
        val ds           = lowerCaseHexDigits

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

    /** Checks if a character does not require JSON escaping or encoding.
     *
     *  @param ch
     *    the character to check
     *  @return
     *    `true` if the character is a basic ASCII character (code point less than `0x80`) that does not need JSON
     *    escaping
     */
    final def isNonEscapedAscii(ch: Char): Boolean = ch < 0x80 && escapedChars(ch) == 0

    final def readStringAsByte(buffer: Buffer): Byte = {
        val isNeg = buffer.skipIfNextIs('-')
        if (isNeg && !buffer.nextInRange('0', '9'))
            throw new NumberFormatException(s"except number at '-', but got ${buffer.getByte(buffer.readerOffset)}")
        var x: Int = 0
        while (buffer.readableBytes > 0 && buffer.nextInRange('0', '9')) {
            x = x * 10 + (buffer.readByte - '0')
            if (x >= 128) throw new NumberFormatException(s"short value overflow error")
        }
        if (isNeg) x = -x
        x.toByte
    }

    final def writeByteAsString(buffer: Buffer, byte: Byte): Unit = {
        val q0: Int =
            if (byte >= 0) byte
            else {
                buffer.writeByte('-')
                -byte
            }
        if (q0 < 10) buffer.writeByte((q0 + '0').toByte)
        else if (q0 < 100) buffer.writeShortLE(digits(q0))
        else buffer.writeMediumLE(digits(q0 - 100) << 8 | 0x31)
    }

    final def readStringAsBoolean(buffer: Buffer): Boolean = {
        val bs = buffer.readIntLE
        if (bs == 0x65757274) true                                   // e u r t
        else if (bs == 0x736c6166 && buffer.skipIfNextIs('e')) false // e s l a f
        else throw new Exception("except 'ture' or 'false'")
    }

    final def getStringAsBoolean(buffer: Buffer, index: Int): Boolean = {
        val bs = buffer.getIntLE(index)
        if (bs == 0x65757274) true                                                // e u r t
        else if (bs == 0x736c6166 && buffer.indexIs('e'.toByte, index + 4)) false // e s l a f
        else throw new Exception("except 'ture' or 'false'")
    }

    final def writeBooleanAsString(buffer: Buffer, boolean: Boolean): Unit =
        if (boolean) buffer.writeIntLE(0x65757274) // e u r t
        else {
            buffer.writeIntLE(0x736c6166) // s l a f
            buffer.writeByte('e')         // e
        }

    final def setBooleanAsString(buffer: Buffer, index: Int, boolean: Boolean): Unit =
        if (boolean) buffer.setIntLE(index, 0x65757274) // e u r t
        else {
            buffer.setIntLE(index, 0x736c6166) // s l a f
            buffer.setByte(index + 4, 'e')     // e
        }

    final def readStringAsShort(buffer: Buffer): Short = {
        val isNeg = buffer.skipIfNextIs('-')
        if (isNeg && !buffer.nextInRange('0', '9'))
            throw new NumberFormatException(s"except number at '-', but got ${buffer.getByte(buffer.readerOffset)}")
        var x: Int = 0
        while (buffer.readableBytes > 0 && buffer.nextInRange('0', '9')) {
            x = x * 10 + (buffer.readByte - '0')
            if (x >= 32768) throw new NumberFormatException(s"short value overflow error")
        }
        if (isNeg) x = -x
        x.toShort
    }

    final def writeShortAsString(buffer: Buffer, short: Short): Unit = {
        val ds = digits
        val q0: Int =
            if (short >= 0) short
            else {
                buffer.writeByte('-')
                -short
            }
        if (q0 < 10) {
            buffer.writeByte((q0 + '0').toByte)
        } else if (q0 < 100) {
            buffer.writeShortLE(ds(q0))
        } else if (q0 < 10000) {
            val q1 = q0 * 5243 >> 19 // divide a small positive int by 100
            val d2 = ds(q0 - q1 * 100)
            if (q0 < 1000) buffer.writeMediumLE(q1 + '0' | d2 << 8) else buffer.writeIntLE(ds(q1) | d2 << 16)
        } else {
            // Based on James Anhalt's algorithm for 5 digits: https://jk-jeon.github.io/posts/2022/02/jeaiii-algorithm/
            val y1 = q0 * 429497L
            val y2 = (y1 & 0xffffffffL) * 100
            val y3 = (y2 & 0xffffffffL) * 100
            val d1 = (y1 >> 32).toInt + '0'
            val d2 = ds((y2 >> 32).toInt) << 8
            val d3 = ds((y3 >> 32).toInt).toLong << 24
            buffer.writeLongLE(d1 | d2 | d3)
            buffer.writerOffset(buffer.writerOffset - 3)
        }
    }

    final def readStringAsInt(buffer: Buffer): Int = {
        val isNeg   = buffer.skipIfNextIs('-')
        var x: Long = 0
        while (buffer.readableBytes > 0 && buffer.nextInRange('0', '9')) {
            x = x * 10 + (buffer.readByte - '0')
        }
        val ret = if (isNeg) -x else x
        if (ret > Int.MaxValue || ret < Int.MinValue)
            throw new NumberFormatException(s"int value overflow error, got $ret")
        ret.toInt
    }

    final def writeIntAsString(buffer: Buffer, int: Int): Unit = if (int != Int.MinValue) {
        val ds = digits
        val q0 =
            if (int >= 0) int
            else {
                buffer.writeByte('-')
                -int
            }
        buffer.writerOffset(buffer.writerOffset + digitCount(q0))
        writePositiveIntDigits(q0, buffer.writerOffset, buffer, ds)
    } else buffer.writeBytes(MIN_INT_BYTES)

    final def readStringAsLong(buffer: Buffer): Long = { // FIXME: long over flow
        buffer.skipIfNextIs('+')
        val minus     = buffer.skipIfNextIs('-')
        var ret: Long = 0
        while (buffer.readableBytes > 0 && buffer.nextInRange('0', '9')) {
            val b = buffer.readByte
            ret = ret * 10L + (b - '0')
        }
        if (minus) -ret else ret
    }

    final def writeLongAsString(buffer: Buffer, long: Long): Unit = {
        val ds = digits
        var q0 = long
        if (long < 0) {
            q0 = -q0
            if (q0 != long) buffer.writeByte('-')
            else {
                q0 = 3372036854775808L
                buffer.writeIntLE(0x3232392d)
            }
        }
        var q       = 0
        var lastPos = buffer.writerOffset
        if (q0 < 100000000) {
            q = q0.toInt
            lastPos += digitCount(q0)
            buffer.writerOffset(lastPos)
        } else {
            val q1 = Math.multiplyHigh(q0, 6189700196426901375L) >>> 25 // divide a positive long by 100000000
            if (q1 < 100000000) {
                q = q1.toInt
                lastPos += digitCount(q1)
                buffer.writerOffset(lastPos)
            } else {
                val q2 = (q1 >> 8) * 1441151881 >> 49 // divide a small positive long by 100000000
                q = q2.toInt
                lastPos += digitCount(q2)
                buffer.writerOffset(lastPos)
                write8Digits(buffer, q1 - q2 * 100000000, ds)
            }
            write8Digits(buffer, q0 - q1 * 100000000, ds)
        }
        writePositiveIntDigits(q, lastPos, buffer, ds)
    }

    final def readStringAsFloat(buffer: Buffer): Float = { // TODO: optimize
        buffer.skipIfNextIs('+')
        val minus               = buffer.skipIfNextIs('-')
        var intPart: Float      = 0
        var floatPart: Float    = 0f
        var startFloat: Boolean = false
        var floatIdx: Float     = 0
        while (buffer.readableBytes > 0 && (buffer.nextInRange('0', '9') || buffer.nextIs('.'))) {
            val b = buffer.readByte
            if (b == '.') startFloat = true
            else {
                if (!startFloat) intPart = intPart * 10f + (b - '0')
                else {
                    floatIdx += 1
                    floatPart = floatPart + ((b - '0').toFloat / Math.pow(10f, floatIdx).toFloat)
                }
            }
        }
        if (minus) -(intPart + floatPart) else intPart + floatPart
    }

    // Based on the amazing work of Raffaello Giulietti
    // "The Schubfach way to render doubles": https://drive.google.com/file/d/1luHhyQF9zKlM8yJ1nebU0OgVYhfC6CBN/view
    // Sources with the license are here: https://github.com/c4f7fcce9cb06515/Schubfach/blob/3c92d3c9b1fead540616c918cdfef432bca53dfa/todec/src/math/FloatToDecimal.java
    final def writeFloatAsString(buffer: Buffer, float: Float): Unit = {
        if (float < 0.0f) buffer.writeByte('-')
        if (float == 0.0f) buffer.writeMedium(0x302e30)
        else {
            val bits     = java.lang.Float.floatToRawIntBits(float)
            var e2       = (bits >> 23 & 0xff) - 150
            var m2       = bits & 0x7fffff | 0x800000
            var m10, e10 = 0
            if (e2 == 0) m10 = m2
            else if ((e2 >= -23 && e2 < 0) && m2 << e2 == 0) m10 = m2 >> -e2
            else {
                var e10Corr, e2Corr = 0
                var cblCorr         = 2
                if (e2 == -150) {
                    m2 &= 0x7fffff
                    e2 = -149
                    if (m2 < 8) {
                        m2 *= 10
                        e10Corr = 1
                    }
                } else if (m2 == 0x800000 && e2 > -149) {
                    e2Corr = 131007
                    cblCorr = 1
                }
                e10 = e2 * 315653 - e2Corr >> 20
                val g      = gs(e10 + 324 << 1) + 1
                val h      = (e10 * -108853 >> 15) + e2 + 1
                val cb     = m2 << 2
                val vbCorr = (m2 & 0x1) - 1
                val vb     = rop(g, cb << h)
                val vbl    = rop(g, cb - cblCorr << h) + vbCorr
                val vbr    = rop(g, cb + 2 << h) - vbCorr
                if (
                  vb < 400 || {
                      m10 = (vb * 107374183L >> 32).toInt // divide a positive int by 40
                      val vb40 = m10 * 40
                      val diff = vbl - vb40
                      (vb40 - vbr + 40 ^ diff) >= 0 || {
                          m10 += ~diff >>> 31
                          e10 += 1
                          false
                      }
                  }
                ) {
                    m10 = vb >> 2
                    val vb4  = m10 << 2
                    var diff = vbl - vb4
                    if ((vb4 - vbr + 4 ^ diff) >= 0) diff = (vb & 0x3) + (m10 & 0x1) - 3
                    m10 += ~diff >>> 31
                    e10 -= e10Corr
                }
            }
            val ds  = digits
            val len = digitCount(m10.toLong)
            e10 += len - 1
            if (e10 < -3 || e10 >= 7) {
                val pos = buffer.writerOffset
                writeSignificantFractionDigits(m10, buffer.writerOffset + len, buffer.writerOffset, buffer, ds)
                buffer.setShortLE(pos, (buffer.getByte(pos + 1) | 0x2e00).toShort)
                if (buffer.writerOffset - 3 < pos) buffer.writeByte('0') // write 0 after 0.
                buffer.writeShortLE(0x2d45)                              // E-
                if (e10 < 0) e10 = -e10
                if (e10 < 10) buffer.writeByte((e10 + '0').toByte) else write2Digits(buffer, e10, ds)
            } else if (e10 < 0) {
                var pos    = buffer.writerOffset
                val dotPos = pos + 1
                buffer.writeIntLE(0x30303030)
                pos -= e10
                writeSignificantFractionDigits(m10, pos + len, pos, buffer, ds)
                buffer.setByte(dotPos, '.')
            } else if (e10 < len - 1) {
                val pos = buffer.writerOffset
                writeSignificantFractionDigits(m10, buffer.writerOffset + len, pos, buffer, ds)
                val bs = buffer.getLongLE(pos)
                val s  = e10 << 3
                val m  = 0xffffffffffff0000L << s
                val d1 = (~m & bs) >> 8
                val d2 = 0x2e00L << s
                val d3 = m & bs
                buffer.setLongLE(pos, d1 | d2 | d3)
            } else {
                buffer.writerOffset(buffer.writerOffset + len)
                writePositiveIntDigits(m10, buffer.writerOffset, buffer, ds)
                buffer.writeShortLE(0x302e)
            }
        }
    }

    private def rop(g: Long, cp: Int): Int = {
        val x = Math.multiplyHigh(g, cp.toLong << 32)
        (x >>> 31).toInt | -x.toInt >>> 31
    }

    // TODO: optimize
    final def readStringAsDouble(buffer: Buffer): Double = {
        buffer.skipIfNextIs('+')
        val minus               = buffer.skipIfNextIs('-')
        var intPart: Double     = 0d
        var floatPart: Double   = 0d
        var startFloat: Boolean = false
        var floatIdx: Double    = 0d
        while (buffer.readableBytes > 0 && (buffer.nextInRange('0', '9') || buffer.nextIs('.'))) {
            val b = buffer.readByte
            if (b == '.') startFloat = true
            else {
                if (!startFloat) intPart = intPart * 10d + (b - '0')
                else {
                    floatIdx += 1
                    floatPart = floatPart + ((b - '0').toFloat / Math.pow(10d, floatIdx))
                }
            }
        }
        if (minus) -(intPart + floatPart) else intPart + floatPart
    }

    // Based on the amazing work of Raffaello Giulietti
    // "The Schubfach way to render doubles": https://drive.google.com/file/d/1luHhyQF9zKlM8yJ1nebU0OgVYhfC6CBN/view
    // Sources with the license are here: https://github.com/c4f7fcce9cb06515/Schubfach/blob/3c92d3c9b1fead540616c918cdfef432bca53dfa/todec/src/math/DoubleToDecimal.java
    final def writeDoubleAsString(buffer: Buffer, double: Double): Unit = {
        if (double < 0.0f) buffer.writeByte('-')
        if (double == 0.0f) buffer.writeMedium(0x302e30)
        else {
            val bits = java.lang.Double.doubleToRawLongBits(double)
            var e2   = ((bits >> 52).toInt & 0x7ff) - 1075
            var m2   = bits & 0xfffffffffffffL | 0x10000000000000L
            var m10  = 0L
            var e10  = 0
            if (e2 == 0) m10 = m2
            else if ((e2 >= -52 && e2 < 0) && m2 << e2 == 0) m10 = m2 >> -e2
            else {
                var e10Corr, e2Corr = 0
                var cblCorr         = 2
                if (e2 == -1075) {
                    m2 &= 0xfffffffffffffL
                    e2 = -1074
                    if (m2 < 3) {
                        m2 *= 10
                        e10Corr = 1
                    }
                } else if (m2 == 0x10000000000000L && e2 > -1074) {
                    e2Corr = 131007
                    cblCorr = 1
                }
                e10 = e2 * 315653 - e2Corr >> 20
                val i      = e10 + 324 << 1
                val g1     = gs(i)
                val g0     = gs(i + 1)
                val h      = (e10 * -108853 >> 15) + e2 + 2
                val cb     = m2 << 2
                val vbCorr = (m2.toInt & 0x1) - 1
                val vb     = rop(g1, g0, cb << h)
                val vbl    = rop(g1, g0, cb - cblCorr << h) + vbCorr
                val vbr    = rop(g1, g0, cb + 2 << h) - vbCorr
                if (
                  vb < 400 || {
                      m10 = Math.multiplyHigh(vb, 461168601842738792L) // divide a positive long by 40
                      val vb40 = m10 * 40
                      val diff = (vbl - vb40).toInt
                      ((vb40 - vbr).toInt + 40 ^ diff) >= 0 || {
                          m10 += ~diff >>> 31
                          e10 += 1
                          false
                      }
                  }
                ) {
                    m10 = vb >> 2
                    val vb4  = m10 << 2
                    var diff = (vbl - vb4).toInt
                    if (((vb4 - vbr).toInt + 4 ^ diff) >= 0) diff = (vb.toInt & 0x3) + (m10.toInt & 0x1) - 3
                    m10 += ~diff >>> 31
                    e10 -= e10Corr
                }
            }
            val ds  = digits
            val len = digitCount(m10)
            e10 += len - 1
            if (e10 < -3 || e10 >= 7) {
                val pos = buffer.writerOffset
                writeSignificantFractionDigits(m10, buffer.writerOffset + len, buffer.writerOffset, buffer, ds)
                buffer.setShortLE(pos, (buffer.getByte(pos + 1) | 0x2e00).toShort)
                if (buffer.writerOffset - 3 < pos) buffer.writeByte('0') // write 0 after 0.
                buffer.writeShortLE(0x2d45)                              // E-
                if (e10 < 0) e10 = -e10
                if (e10 < 10) buffer.writeByte((e10 + '0').toByte)
                else if (e10 < 100) write2Digits(buffer, e10, ds)
                else write3Digits(buffer, e10, ds)
            } else if (e10 < 0) {
                var pos    = buffer.writerOffset
                val dotPos = pos + 1
                buffer.writeIntLE(0x30303030)
                pos -= e10
                writeSignificantFractionDigits(m10, pos + len, pos, buffer, ds)
                buffer.setByte(dotPos, '.')
            } else if (e10 < len - 1) {
                val pos = buffer.writerOffset
                writeSignificantFractionDigits(m10, buffer.writerOffset + len, pos, buffer, ds)
                val bs = buffer.getLongLE(pos)
                val s  = e10 << 3
                val m  = 0xffffffffffff0000L << s
                val d1 = (~m & bs) >> 8
                val d2 = 0x2e00L << s
                val d3 = m & bs
                buffer.setLongLE(pos, d1 | d2 | d3)
            } else {
                buffer.writerOffset(buffer.writerOffset + len)
                writePositiveIntDigits(m10.toInt, buffer.writerOffset, buffer, ds)
                buffer.writeShortLE(0x302e)
            }
        }
    }

    private def rop(g1: Long, g0: Long, cp: Long): Long = {
        val x = Math.multiplyHigh(g0, cp) + (g1 * cp >>> 1)
        Math.multiplyHigh(g1, cp) + (x >>> 63) | (-x ^ x) >>> 63
    }

    final def readStringAsBigInt(buffer: Buffer): BigInt = {
        ???
    }

    final def writeBigIntAsString(buffer: Buffer, bigInt: BigInt): Unit = if (bigInt.isValidLong)
        writeLongAsString(buffer, bigInt.longValue)
    else writeBigInteger(buffer, bigInt.bigInteger, null)

    final def writeBigIntegerAsString(buffer: Buffer, num: BigInteger): Unit = writeBigInteger(buffer, num, null)

    private def writeBigInteger(buffer: Buffer, x: BigInteger, ss: Array[BigInteger]): Unit = {
        val bitLen = x.bitLength
        if (bitLen < 64) writeLongAsString(buffer, x.longValue)
        else {
            val n   = calculateTenPow18SquareNumber(bitLen)
            val ss1 = if (ss eq null) getTenPow18Squares(n) else ss
            val qr  = x.divideAndRemainder(ss1(n))
            writeBigInteger(buffer, qr(0), ss1)
            writeBigIntegerRemainder(buffer, qr(1), n - 1, ss1)
        }
    }

    private def writeBigIntegerRemainder(buffer: Buffer, x: BigInteger, n: Int, ss: Array[BigInteger]): Unit =
        if (n < 0) write18Digits(buffer, Math.abs(x.longValue), digits)
        else {
            val qr = x.divideAndRemainder(ss(n))
            writeBigIntegerRemainder(buffer, qr(0), n - 1, ss)
            writeBigIntegerRemainder(buffer, qr(1), n - 1, ss)
        }

    
    
    private def calculateTenPow18SquareNumber(bitLen: Int): Int = {
        // Math.max((x.bitLength * Math.log(2) / Math.log(1e18)).toInt - 1, 1)
        val m = Math.max((bitLen * 71828554L >> 32).toInt - 1, 1)
        31 - java.lang.Integer.numberOfLeadingZeros(m)
    }

    private def writeSignificantFractionDigits(x: Long, p: Int, pl: Int, buffer: Buffer, ds: Array[Short]): Unit = {
        var q0     = x.toInt
        var pos    = p
        var posLim = pl
        if (q0 != x) {
            val q1    = (Math.multiplyHigh(x, 6189700196426901375L) >>> 25).toInt // divide a positive long by 100000000
            val r1    = (x - q1 * 100000000L).toInt
            val posm8 = pos - 8
            if (r1 == 0) {
                q0 = q1
                pos = posm8
            } else {
                buffer.writerOffset(posm8)
                writeFractionDigits(q1, posm8, posLim, buffer, ds)
                q0 = r1
                posLim = posm8
            }
        }
        writeSignificantFractionDigits(q0, pos, posLim, buffer, ds)
    }

    private def writeSignificantFractionDigits(x: Int, p: Int, posLim: Int, buffer: Buffer, ds: Array[Short]): Unit = {
        var q0  = x
        var q1  = 0
        var pos = p
        while ({
            val qp = q0 * 1374389535L
            q1 = (qp >> 37).toInt     // divide a positive int by 100
            (qp & 0x1fc0000000L) == 0 // check if q is divisible by 100
        }) {
            q0 = q1
            pos -= 2
        }
        val d = ds(q0 - q1 * 100)
        buffer.writerOffset(pos + 1)
        buffer.setShortLE(pos - 1, d)
        writeFractionDigits(q1, pos - 2, posLim, buffer, ds)
        buffer.writerOffset(pos + ((0x3039 - d) >>> 31))
    }

    private def writeFractionDigits(x: Int, p: Int, posLim: Int, buffer: Buffer, ds: Array[Short]): Unit = {
        var q0  = x
        var pos = p
        while (pos > posLim) {
            val q1 = (q0 * 1374389535L >> 37).toInt // divide a positive int by 100
            buffer.setShortLE(pos - 1, ds(q0 - q1 * 100))
            q0 = q1
            pos -= 2
        }
    }

    private def writePositiveIntDigits(q: Int, p: Int, buffer: Buffer, ds: Array[Short]): Unit = {
        var q0  = q
        var pos = p
        while ({
            pos -= 2
            q0 >= 100
        }) {
            val q1 = (q0 * 1374389535L >> 37).toInt // divide a positive int by 100
            buffer.setShortLE(pos, ds(q0 - q1 * 100))
            q0 = q1
        }
        if (q0 < 10) buffer.setByte(pos + 1, (q0 + '0').toByte) else buffer.setShortLE(pos, ds(q0))
    }

    // Adoption of a nice trick from Daniel Lemire's blog that works for numbers up to 10^18:
    // https://lemire.me/blog/2021/06/03/computing-the-number-of-digits-of-an-integer-even-faster/
    private def digitCount(q0: Long): Int = (offsets(java.lang.Long.numberOfLeadingZeros(q0)) + q0 >> 58).toInt

    final def writeYearAsString(buffer: Buffer, year: Year): Unit = writeYearAsString(buffer, year.getValue)

    final def writeYearAsString(buffer: Buffer, year: Int): Unit = {
        val ds = digits
        if (year >= 0 && year < 10000) write4Digits(buffer, year, ds) else writeYearWithSign(buffer, year, ds)
    }

    private def writeYearWithSign(buffer: Buffer, year: Int, ds: Array[Short]): Unit = {
        var q0 = year

        var b: Byte = '+'
        if (q0 < 0) {
            q0 = -q0
            b = '-'
        }
        buffer.writeByte(b)
        if (q0 < 10000) write4Digits(buffer, q0, ds)
        else {
            buffer.writerOffset(buffer.writerOffset + digitCount(q0))
            writePositiveIntDigits(q0, buffer.writerOffset, buffer, ds)
        }
    }

    final def writeYearMonthAsString(buffer: Buffer, ym: YearMonth): Unit = {
        val ds = digits
        writeYearAsString(buffer, ym.getYear)
        buffer.writeMediumLE(ds(ym.getMonthValue) << 8 | 0x00002d)
    }

    final def writeLocalDateAsString(buffer: Buffer, localDate: LocalDate): Unit = {
        val ds = digits
        writeYearAsString(buffer, localDate.getYear)
        val d1 = ds(localDate.getMonthValue) << 8
        val d2 = ds(localDate.getDayOfMonth).toLong << 32
        buffer.writeLongLE(d1 | d2 | 0x00002d00002dL)
        buffer.writerOffset(buffer.writerOffset - 2)
    }

    final def writeLocalTimeAsString(buffer: Buffer, localTime: LocalTime): Unit = {
        val ds     = digits
        val second = localTime.getSecond
        val nano   = localTime.getNano
        val d1     = ds(localTime.getHour) | 0x3a00003a0000L
        val d2     = ds(localTime.getMinute).toLong << 24
        if ((second | nano) == 0) {
            buffer.writeLongLE(d1 | d2)
            buffer.writerOffset(buffer.writerOffset - 3)
        } else {
            val d3 = ds(second).toLong << 48
            buffer.writeLongLE(d1 | d2 | d3)
            if (nano != 0) writeNanos(buffer, nano, ds)
        }
    }

    private def writeNanos(buffer: Buffer, q0: Long, ds: Array[Short]): Unit = {
        val y1 =
            q0 * 1441151881 // Based on James Anhalt's algorithm for 9 digits: https://jk-jeon.github.io/posts/2022/02/jeaiii-algorithm/
        val y2 = (y1 & 0x1ffffffffffffffL) * 100
        var m  = y1 >>> 57 << 8 | ds((y2 >>> 57).toInt) << 16 | 0x302e
        if ((y2 & 0x1fffff800000000L) == 0) {
            buffer.writeIntLE(m.toInt)
        } else {
            val y3 = (y2 & 0x1ffffffffffffffL) * 100
            val y4 = (y3 & 0x1ffffffffffffffL) * 100
            m |= ds((y3 >>> 57).toInt).toLong << 32
            val d = ds((y4 >>> 57).toInt)
            buffer.writeLongLE(m | d.toLong << 48)
            if ((y4 & 0x1ff000000000000L) == 0 && d <= 0x3039) buffer.writerOffset(buffer.writerOffset - 1)
            else
                buffer.writeShortLE(ds(((y4 & 0x1ffffffffffffffL) * 100 >>> 57).toInt))
        }
    }

    final def writeLocalDateTimeAsString(buffer: Buffer, localDateTime: LocalDateTime): Unit = {
        writeLocalDateAsString(buffer, localDateTime.toLocalDate)
        buffer.writeByte('T')
        writeLocalTimeAsString(buffer, localDateTime.toLocalTime)
    }

    final def writeMonthDayAsString(buffer: Buffer, monthDay: MonthDay): Unit = { // "--01-01"
        val ds = digits
        val d1 = ds(monthDay.getMonthValue) << 16
        val d2 = ds(monthDay.getDayOfMonth).toLong << 40
        buffer.writeLongLE(d1 | d2 | 0x00002d00002d2dL)
        buffer.writerOffset(buffer.writerOffset - 1)
    }

    final def writeOffsetDateTimeAsString(buffer: Buffer, offsetDateTime: OffsetDateTime): Unit = {
        val ds = digits
        writeLocalDateTimeAsString(buffer, offsetDateTime.toLocalDateTime)
        writeZoneOffset(buffer, offsetDateTime.getOffset)
    }

    final def writeOffsetTimeAsString(buffer: Buffer, offsetTime: OffsetTime): Unit = {
        val ds = digits
        writeLocalTimeAsString(buffer, offsetTime.toLocalTime)
        writeZoneOffset(buffer, offsetTime.getOffset)
    }

    final def writeZoneOffset(buffer: Buffer, zoneOffset: ZoneOffset): Unit = {
        val ds = digits
        var y  = zoneOffset.getTotalSeconds
        if (y == 0) {
            buffer.writeByte(0x5a)
        } else {
            var m = 0x30303a00002bL
            if (y < 0) {
                y = -y
                m = 0x30303a00002dL
            }
            y *= 37283 // Based on James Anhalt's algorithm: https://jk-jeon.github.io/posts/2022/02/jeaiii-algorithm/
            m |= ds(y >>> 27) << 8
            if ((y & 0x7ff8000) == 0) {
                buffer.writeLongLE(m)
                buffer.writerOffset(buffer.writerOffset - 2)
            } else {
                y = (y & 0x7ffffff) * 15
                buffer.writeLongLE(ds(y >> 25).toLong << 32 | m)
                if ((y & 0x1f80000) == 0)
                    buffer.writerOffset(buffer.writerOffset - 1) // check if totalSeconds is divisible by 60
                else {
                    buffer.writerOffset(buffer.writerOffset - 2)
                    buffer.writeMediumLE(ds((y & 0x1ffffff) * 15 >> 23) << 8 | 0x00003a)
                }
            }
        }
    }

    final def writeZoneId(buffer: Buffer, zoneId: ZoneId): Unit = {
        val s = zoneId.getId
        buffer.writeCharSequence(s, StandardCharsets.US_ASCII)
    }

    final def writeZonedDateTime(buffer: Buffer, zonedDateTime: ZonedDateTime): Unit = {
        writeLocalDateTimeAsString(buffer, zonedDateTime.toLocalDateTime)
        writeZoneOffset(buffer, zonedDateTime.getOffset)
        val zone = zonedDateTime.getZone
        if (!zone.isInstanceOf[ZoneOffset]) {
            buffer.writeByte('[')
            writeZoneId(buffer, zone)
            buffer.writeByte(']')
        }
    }

    def writePeriodAsString(buffer: Buffer, period: Period): Unit = {
        val years  = period.getYears
        val months = period.getMonths
        val days   = period.getDays
        if ((years | months | days) == 0) buffer.writeMediumLE(0x443050)
        else {
            val ds = digits
            buffer.writeByte('P')
            if (years != 0) {
                writeIntAsString(buffer, years)
                buffer.writeByte('Y')
            }
            if (months != 0) {
                writeIntAsString(buffer, months)
                buffer.writeByte('M')
            }
            if (days != 0) {
                writeIntAsString(buffer, days)
                buffer.writeByte('D')
            }
        }
    }

    def writeJDurationAsString(buffer: Buffer, duration: JDuration): Unit = {
        val totalSecs = duration.getSeconds
        var nano      = duration.getNano
        if ((totalSecs | nano) != 0) {
            buffer.writeShortLE(0x5450) // PT
            val effectiveTotalSecs = if (totalSecs < 0) (-nano >> 31) - totalSecs else totalSecs
            val hours =
                Math.multiplyHigh(effectiveTotalSecs >> 4, 655884233731895169L) >> 3 // divide a positive long by 3600
            val secsOfHour = (effectiveTotalSecs - hours * 3600).toInt
            val minutes    = secsOfHour * 17477 >> 20 // divide a small positive int by 60
            val seconds    = secsOfHour - minutes * 60
            val ds         = digits
            if (hours != 0) {
                if (totalSecs < 0) buffer.writeByte('-')
                if (hours < 100000000) {
                    buffer.writerOffset(buffer.writerOffset + digitCount(hours))
                    writePositiveIntDigits(hours.toInt, buffer.writerOffset, buffer, ds)
                } else {
                    val q1 =
                        Math.multiplyHigh(hours, 6189700196426901375L) >>> 25 // divide a positive long by 100000000
                    buffer.writerOffset(buffer.writerOffset + digitCount(q1))
                    writePositiveIntDigits(q1.toInt, buffer.writerOffset, buffer, ds)
                    write8Digits(buffer, hours - q1 * 100000000, ds)
                }
                buffer.writeByte('H')
            }
            if (minutes != 0) {
                if (totalSecs < 0) buffer.writeByte('-')
                if (minutes < 10) buffer.writeByte((minutes + '0').toByte) else write2Digits(buffer, minutes, ds)
                buffer.writeByte('M')
            }
            if ((seconds | nano) != 0) {
                if (totalSecs < 0) buffer.writeByte('-')
                if (seconds < 10) buffer.writeByte((seconds + '0').toByte) else write2Digits(buffer, seconds, ds)
                if (nano != 0) {
                    if (totalSecs < 0) nano = 1000000000 - nano
                    val dotPos = buffer.writerOffset
                    writeSignificantFractionDigits(nano, dotPos + 9, dotPos, buffer, ds)
                    buffer.setByte(dotPos, '.')
                }
                buffer.writeByte('S')
            }
        } else buffer.writeUnsignedIntLE(0x53305450L) // PT0S
    }

    def writeDurationAsString(buffer: Buffer, duration: Duration): Unit = {}

    private def write2Digits(buffer: Buffer, q0: Int, ds: Array[Short]): Unit =
        buffer.writeShortLE(ds(q0))

    private def write3Digits(buffer: Buffer, q0: Int, ds: Array[Short]): Unit = {
        val q1 = q0 * 1311 >> 17 // divide a small positive int by 100
        buffer.writeMediumLE(ds(q0 - q1 * 100) << 8 | q1 + '0')
    }

    private def write4Digits(buffer: Buffer, q0: Int, ds: Array[Short]): Unit = {
        val q1 = q0 * 5243 >> 19 // divide a small positive int by 100
        val d1 = ds(q0 - q1 * 100) << 16
        val d2 = ds(q1)
        buffer.writeIntLE(d1 | d2)
    }

    private def write8Digits(buffer: Buffer, q0: Long, ds: Array[Short]): Unit = {
        val y1 =
            q0 * 140737489 // Based on James Anhalt's algorithm for 8 digits: https://jk-jeon.github.io/posts/2022/02/jeaiii-algorithm/
        val y2 = (y1 & 0x7fffffffffffL) * 100
        val y3 = (y2 & 0x7fffffffffffL) * 100
        val y4 = (y3 & 0x7fffffffffffL) * 100
        val d1 = ds((y1 >> 47).toInt)
        val d2 = ds((y2 >> 47).toInt) << 16
        val d3 = ds((y3 >> 47).toInt).toLong << 32
        val d4 = ds((y4 >> 47).toInt).toLong << 48
        buffer.writeLongLE(d1 | d2 | d3 | d4)
    }

    private def write18Digits(buffer: Buffer, x: Long, ds: Array[Short]): Unit = {
        val q1 = Math.multiplyHigh(x, 6189700196426901375L) >>> 25 // divide a positive long by 100000000
        val q2 = (q1 >> 8) * 1441151881 >> 49                      // divide a small positive long by 100000000
        write2Digits(buffer, q2.toInt, ds)
        write8Digits(buffer, q1 - q2 * 100000000, ds)
        write8Digits(buffer, x - q1 * 100000000, ds)
    }

    private val MAX_INT_BYTES: Array[Byte] = Int.MaxValue.toString.getBytes(StandardCharsets.US_ASCII)
    private val MIN_INT_BYTES: Array[Byte] = Int.MinValue.toString.getBytes(StandardCharsets.US_ASCII)

    private val INTEGER_TRIM: Array[Byte] = Array(' ', '+')

    /* Use the following code to generate `escapedChars` in Scala REPL:
      val es = new Array[Byte](128)
      java.util.Arrays.fill(es, 0, 32, -1: Byte)
      es('\n') = 'n'
      es('\r') = 'r'
      es('\t') = 't'
      es('\b') = 'b'
      es('\f') = 'f'
      es('\\') = '\\'
      es('\"') = '"'
      es(127) = -1
      es.grouped(16).map(_.mkString(", ")).mkString("Array(\n", ",\n", "\n)")
     */
    private final val escapedChars: Array[Byte] = Array(
      -1, -1, -1, -1, -1, -1, -1, -1, 98, 116, 110, -1, 102, 114, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
      -1, -1, -1, -1, -1, 0, 0, 34, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
      0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 92, 0, 0, 0, 0, 0, 0, 0, 0,
      0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1
    )

    private final val offsets = Array(5088146770730811392L, 5088146770730811392L, 5088146770730811392L,
      5088146770730811392L, 5088146770730811392L, 5088146770730811392L, 5088146770730811392L, 5088146770730811392L,
      4889916394579099648L, 4889916394579099648L, 4889916394579099648L, 4610686018427387904L, 4610686018427387904L,
      4610686018427387904L, 4610686018427387904L, 4323355642275676160L, 4323355642275676160L, 4323355642275676160L,
      4035215266123964416L, 4035215266123964416L, 4035215266123964416L, 3746993889972252672L, 3746993889972252672L,
      3746993889972252672L, 3746993889972252672L, 3458764413820540928L, 3458764413820540928L, 3458764413820540928L,
      3170534127668829184L, 3170534127668829184L, 3170534127668829184L, 2882303760517117440L, 2882303760517117440L,
      2882303760517117440L, 2882303760517117440L, 2594073385265405696L, 2594073385265405696L, 2594073385265405696L,
      2305843009203693952L, 2305843009203693952L, 2305843009203693952L, 2017612633060982208L, 2017612633060982208L,
      2017612633060982208L, 2017612633060982208L, 1729382256910170464L, 1729382256910170464L, 1729382256910170464L,
      1441151880758548720L, 1441151880758548720L, 1441151880758548720L, 1152921504606845976L, 1152921504606845976L,
      1152921504606845976L, 1152921504606845976L, 864691128455135132L, 864691128455135132L, 864691128455135132L,
      576460752303423478L, 576460752303423478L, 576460752303423478L, 576460752303423478L, 576460752303423478L,
      576460752303423478L, 576460752303423478L)
    /* Use the following code to generate `digits` in Scala REPL:
      val ds = new Array[Short](100)
      var i, j = 0
      while (j < 10) {
        var k = 0
        while (k < 10) {
          ds(i) = (((k + '0') << 8) + (j + '0')).toShort
          i += 1
          k += 1
        }
        j += 1
      }
      ds.grouped(10).map(_.mkString(", ")).mkString("Array(\n", ",\n", "\n)")
     */
    private final val digits: Array[Short] = Array(
      12336, 12592, 12848, 13104, 13360, 13616, 13872, 14128, 14384, 14640, 12337, 12593, 12849, 13105, 13361, 13617,
      13873, 14129, 14385, 14641, 12338, 12594, 12850, 13106, 13362, 13618, 13874, 14130, 14386, 14642, 12339, 12595,
      12851, 13107, 13363, 13619, 13875, 14131, 14387, 14643, 12340, 12596, 12852, 13108, 13364, 13620, 13876, 14132,
      14388, 14644, 12341, 12597, 12853, 13109, 13365, 13621, 13877, 14133, 14389, 14645, 12342, 12598, 12854, 13110,
      13366, 13622, 13878, 14134, 14390, 14646, 12343, 12599, 12855, 13111, 13367, 13623, 13879, 14135, 14391, 14647,
      12344, 12600, 12856, 13112, 13368, 13624, 13880, 14136, 14392, 14648, 12345, 12601, 12857, 13113, 13369, 13625,
      13881, 14137, 14393, 14649
    )

    /* Use the following code to generate `lowerCaseHexDigits` in Scala REPL:
      val ds = new Array[Short](256)
      var i, j = 0
      while (j < 16) {
        val d1 =
          if (j <= 9) j + '0'
          else j + 'a' - 10
        var k = 0
        while (k < 16) {
          val d2 =
            if (k <= 9) k + '0'
            else k + 'a' - 10
          ds(i) = ((d2 << 8) + d1).toShort
          i += 1
          k += 1
        }
        j += 1
      }
      ds.grouped(16).map(_.mkString(", ")).mkString("Array(\n", ",\n", "\n)")
     */
    final val lowerCaseHexDigits: Array[Short] = Array(
      12336, 12592, 12848, 13104, 13360, 13616, 13872, 14128, 14384, 14640, 24880, 25136, 25392, 25648, 25904, 26160,
      12337, 12593, 12849, 13105, 13361, 13617, 13873, 14129, 14385, 14641, 24881, 25137, 25393, 25649, 25905, 26161,
      12338, 12594, 12850, 13106, 13362, 13618, 13874, 14130, 14386, 14642, 24882, 25138, 25394, 25650, 25906, 26162,
      12339, 12595, 12851, 13107, 13363, 13619, 13875, 14131, 14387, 14643, 24883, 25139, 25395, 25651, 25907, 26163,
      12340, 12596, 12852, 13108, 13364, 13620, 13876, 14132, 14388, 14644, 24884, 25140, 25396, 25652, 25908, 26164,
      12341, 12597, 12853, 13109, 13365, 13621, 13877, 14133, 14389, 14645, 24885, 25141, 25397, 25653, 25909, 26165,
      12342, 12598, 12854, 13110, 13366, 13622, 13878, 14134, 14390, 14646, 24886, 25142, 25398, 25654, 25910, 26166,
      12343, 12599, 12855, 13111, 13367, 13623, 13879, 14135, 14391, 14647, 24887, 25143, 25399, 25655, 25911, 26167,
      12344, 12600, 12856, 13112, 13368, 13624, 13880, 14136, 14392, 14648, 24888, 25144, 25400, 25656, 25912, 26168,
      12345, 12601, 12857, 13113, 13369, 13625, 13881, 14137, 14393, 14649, 24889, 25145, 25401, 25657, 25913, 26169,
      12385, 12641, 12897, 13153, 13409, 13665, 13921, 14177, 14433, 14689, 24929, 25185, 25441, 25697, 25953, 26209,
      12386, 12642, 12898, 13154, 13410, 13666, 13922, 14178, 14434, 14690, 24930, 25186, 25442, 25698, 25954, 26210,
      12387, 12643, 12899, 13155, 13411, 13667, 13923, 14179, 14435, 14691, 24931, 25187, 25443, 25699, 25955, 26211,
      12388, 12644, 12900, 13156, 13412, 13668, 13924, 14180, 14436, 14692, 24932, 25188, 25444, 25700, 25956, 26212,
      12389, 12645, 12901, 13157, 13413, 13669, 13925, 14181, 14437, 14693, 24933, 25189, 25445, 25701, 25957, 26213,
      12390, 12646, 12902, 13158, 13414, 13670, 13926, 14182, 14438, 14694, 24934, 25190, 25446, 25702, 25958, 26214
    )

    /* Use the following code to generate `upperCaseHexDigits` in Scala REPL:
      val ds = new Array[Short](256)
      var i, j = 0
      while (j < 16) {
        val d1 =
          if (j <= 9) j + '0'
          else j + 'A' - 10
        var k = 0
        while (k < 16) {
          val d2 =
            if (k <= 9) k + '0'
            else k + 'A' - 10
          ds(i) = ((d2 << 8) + d1).toShort
          i += 1
          k += 1
        }
        j += 1
      }
      ds.grouped(16).map(_.mkString(", ")).mkString("Array(\n", ",\n", "\n)")
     */
    private final val upperCaseHexDigits: Array[Short] = Array(
      12336, 12592, 12848, 13104, 13360, 13616, 13872, 14128, 14384, 14640, 16688, 16944, 17200, 17456, 17712, 17968,
      12337, 12593, 12849, 13105, 13361, 13617, 13873, 14129, 14385, 14641, 16689, 16945, 17201, 17457, 17713, 17969,
      12338, 12594, 12850, 13106, 13362, 13618, 13874, 14130, 14386, 14642, 16690, 16946, 17202, 17458, 17714, 17970,
      12339, 12595, 12851, 13107, 13363, 13619, 13875, 14131, 14387, 14643, 16691, 16947, 17203, 17459, 17715, 17971,
      12340, 12596, 12852, 13108, 13364, 13620, 13876, 14132, 14388, 14644, 16692, 16948, 17204, 17460, 17716, 17972,
      12341, 12597, 12853, 13109, 13365, 13621, 13877, 14133, 14389, 14645, 16693, 16949, 17205, 17461, 17717, 17973,
      12342, 12598, 12854, 13110, 13366, 13622, 13878, 14134, 14390, 14646, 16694, 16950, 17206, 17462, 17718, 17974,
      12343, 12599, 12855, 13111, 13367, 13623, 13879, 14135, 14391, 14647, 16695, 16951, 17207, 17463, 17719, 17975,
      12344, 12600, 12856, 13112, 13368, 13624, 13880, 14136, 14392, 14648, 16696, 16952, 17208, 17464, 17720, 17976,
      12345, 12601, 12857, 13113, 13369, 13625, 13881, 14137, 14393, 14649, 16697, 16953, 17209, 17465, 17721, 17977,
      12353, 12609, 12865, 13121, 13377, 13633, 13889, 14145, 14401, 14657, 16705, 16961, 17217, 17473, 17729, 17985,
      12354, 12610, 12866, 13122, 13378, 13634, 13890, 14146, 14402, 14658, 16706, 16962, 17218, 17474, 17730, 17986,
      12355, 12611, 12867, 13123, 13379, 13635, 13891, 14147, 14403, 14659, 16707, 16963, 17219, 17475, 17731, 17987,
      12356, 12612, 12868, 13124, 13380, 13636, 13892, 14148, 14404, 14660, 16708, 16964, 17220, 17476, 17732, 17988,
      12357, 12613, 12869, 13125, 13381, 13637, 13893, 14149, 14405, 14661, 16709, 16965, 17221, 17477, 17733, 17989,
      12358, 12614, 12870, 13126, 13382, 13638, 13894, 14150, 14406, 14662, 16710, 16966, 17222, 17478, 17734, 17990
    )
    /* Use the following code to generate `base64Digits` in Scala REPL:
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".getBytes
        .grouped(16).map(_.mkString(", ")).mkString("Array(\n", ",\n", "\n)")
     */
    private final val base64Digits: Array[Byte] = Array(
      65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 97, 98,
      99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121,
      122, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 43, 47
    )
    /* Use the following code to generate `base64UrlDigits` in Scala REPL:
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".getBytes
        .grouped(16).map(_.mkString(", ")).mkString("Array(\n", ",\n", "\n)")
     */
    private final val base64UrlDigits: Array[Byte] = Array(
      65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 97, 98,
      99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121,
      122, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 45, 95
    )
    /* Use the following code to generate `gs` in Scala REPL:
      val gs = new Array[Long](1234)
      var i = 0
      var pow5 = BigInt(1)
      while (i < 650) {
        val av = (pow5 >> (pow5.bitLength - 126)) + 1
        gs(648 - i) = (av >> 63).longValue & 0x7FFFFFFFFFFFFFFFL
        gs(649 - i) = av.longValue & 0x7FFFFFFFFFFFFFFFL
        pow5 *= 5
        i += 2
      }
      pow5 = BigInt(5)
      while (i < 1234) {
        val inv = ((BigInt(1) << (pow5.bitLength + 125)) / pow5) + 1
        gs(i) = (inv >> 63).longValue & 0x7FFFFFFFFFFFFFFFL
        gs(i + 1) = inv.longValue & 0x7FFFFFFFFFFFFFFFL
        pow5 *= 5
        i += 2
      }
      gs.grouped(4).map(_.mkString("L, ")).mkString("Array(\n", "L,\n", "L\n)")
     */
    private final val gs: Array[Long] = Array(
      5696189077778435540L, 6557778377634271669L, 9113902524445496865L, 1269073367360058862L, 7291122019556397492L,
      1015258693888047090L, 5832897615645117993L, 6346230177223303157L, 4666318092516094394L, 8766332956520552849L,
      7466108948025751031L, 8492109508320019073L, 5972887158420600825L, 4949013199285060097L, 4778309726736480660L,
      3959210559428048077L, 7645295562778369056L, 6334736895084876923L, 6116236450222695245L, 3223115108696946377L,
      4892989160178156196L, 2578492086957557102L, 7828782656285049914L, 436238524390181040L, 6263026125028039931L,
      2193665226883099993L, 5010420900022431944L, 9133629810990300641L, 8016673440035891111L, 9079784475471615541L,
      6413338752028712889L, 5419153173006337271L, 5130671001622970311L, 6179996945776024979L, 8209073602596752498L,
      6198646298499729642L, 6567258882077401998L, 8648265853541694037L, 5253807105661921599L, 1384589460720489745L,
      8406091369059074558L, 5904691951894693915L, 6724873095247259646L, 8413102376257665455L, 5379898476197807717L,
      4885807493635177203L, 8607837561916492348L, 438594360332462878L, 6886270049533193878L, 4040224303007880625L,
      5509016039626555102L, 6921528257148214824L, 8814425663402488164L, 3695747581953323071L, 7051540530721990531L,
      4801272472933613619L, 5641232424577592425L, 1996343570975935733L, 9025971879324147880L, 3194149713561497173L,
      7220777503459318304L, 2555319770849197738L, 5776622002767454643L, 3888930224050313352L, 4621297602213963714L,
      6800492993982161005L, 7394076163542341943L, 5346765568258592123L, 5915260930833873554L, 7966761269348784022L,
      4732208744667098843L, 8218083422849982379L, 7571533991467358150L, 2080887032334240837L, 6057227193173886520L,
      1664709625867392670L, 4845781754539109216L, 1331767700693914136L, 7753250807262574745L, 7664851543223128102L,
      6202600645810059796L, 6131881234578502482L, 4962080516648047837L, 3060830580291846824L, 7939328826636876539L,
      6742003335837910079L, 6351463061309501231L, 7238277076041283225L, 5081170449047600985L, 3945947253462071419L,
      8129872718476161576L, 6313515605539314269L, 6503898174780929261L, 3206138077060496254L, 5203118539824743409L,
      720236054277441842L, 8324989663719589454L, 4841726501585817270L, 6659991730975671563L, 5718055608639608977L,
      5327993384780537250L, 8263793301653597505L, 8524789415648859601L, 3998697245790980200L, 6819831532519087681L,
      1354283389261828999L, 5455865226015270144L, 8462124340893283845L, 8729384361624432231L, 8005375723316388668L,
      6983507489299545785L, 4559626171282155773L, 5586805991439636628L, 3647700937025724618L, 8938889586303418605L,
      3991647091870204227L, 7151111669042734884L, 3193317673496163382L, 5720889335234187907L, 4399328546167885867L,
      9153422936374700651L, 8883600081239572549L, 7322738349099760521L, 5262205657620702877L, 5858190679279808417L,
      2365090118725607140L, 4686552543423846733L, 7426095317093351197L, 7498484069478154774L, 813706063123630946L,
      5998787255582523819L, 2495639257869859918L, 4799029804466019055L, 3841185813666843096L, 7678447687145630488L,
      6145897301866948954L, 6142758149716504390L, 8606066656235469486L, 4914206519773203512L, 6884853324988375589L,
      7862730431637125620L, 3637067690497580296L, 6290184345309700496L, 2909654152398064237L, 5032147476247760397L,
      483048914547496228L, 8051435961996416635L, 2617552670646949126L, 6441148769597133308L, 2094042136517559301L,
      5152919015677706646L, 5364582523955957764L, 8244670425084330634L, 4893983223587622099L, 6595736340067464507L,
      5759860986241052841L, 5276589072053971606L, 918539974250931950L, 8442542515286354569L, 7003687180914356604L,
      6754034012229083655L, 7447624152102440445L, 5403227209783266924L, 5958099321681952356L, 8645163535653227079L,
      3998935692578258285L, 6916130828522581663L, 5043822961433561789L, 5532904662818065330L, 7724407183888759755L,
      8852647460508904529L, 3135679457367239799L, 7082117968407123623L, 4353217973264747001L, 5665694374725698898L,
      7171923193353707924L, 9065110999561118238L, 407030665140201709L, 7252088799648894590L, 4014973346854071690L,
      5801671039719115672L, 3211978677483257352L, 4641336831775292537L, 8103606164099471367L, 7426138930840468060L,
      5587072233075333540L, 5940911144672374448L, 4469657786460266832L, 4752728915737899558L, 7265075043910123789L,
      7604366265180639294L, 556073626030467093L, 6083493012144511435L, 2289533308195328836L, 4866794409715609148L,
      1831626646556263069L, 7786871055544974637L, 1085928227119065748L, 6229496844435979709L, 6402765803808118083L,
      4983597475548783767L, 6966887050417449628L, 7973755960878054028L, 3768321651184098759L, 6379004768702443222L,
      6704006135689189330L, 5103203814961954578L, 1673856093809441141L, 8165126103939127325L, 833495342724150664L,
      6532100883151301860L, 666796274179320531L, 5225680706521041488L, 533437019343456425L, 8361089130433666380L,
      8232196860433350926L, 6688871304346933104L, 6585757488346680741L, 5351097043477546483L, 7113280398048299755L,
      8561755269564074374L, 313202192651548637L, 6849404215651259499L, 2095236161492194072L, 5479523372521007599L,
      3520863336564710419L, 8767237396033612159L, 99358116390671185L, 7013789916826889727L, 1924160900483492110L,
      5611031933461511781L, 7073351942499659173L, 8977651093538418850L, 7628014293257544353L, 7182120874830735080L,
      6102411434606035483L, 5745696699864588064L, 4881929147684828386L, 9193114719783340903L, 2277063414182859933L,
      7354491775826672722L, 5510999546088198270L, 5883593420661338178L, 719450822128648293L, 4706874736529070542L,
      4264909472444828957L, 7530999578446512867L, 8668529563282681493L, 6024799662757210294L, 3245474835884234871L,
      4819839730205768235L, 4441054276078343059L, 7711743568329229176L, 7105686841725348894L, 6169394854663383341L,
      3839875066009323953L, 4935515883730706673L, 1227225645436504001L, 7896825413969130677L, 118886625327451240L,
      6317460331175304541L, 5629132522374826477L, 5053968264940243633L, 2658631610528906020L, 8086349223904389813L,
      2409136169475294470L, 6469079379123511850L, 5616657750322145900L, 5175263503298809480L, 4493326200257716720L,
      8280421605278095168L, 7189321920412346751L, 6624337284222476135L, 217434314217011916L, 5299469827377980908L,
      173947451373609533L, 8479151723804769452L, 7657013551681595899L, 6783321379043815562L, 2436262026603366396L,
      5426657103235052449L, 7483032843395558602L, 8682651365176083919L, 6438829327320028278L, 6946121092140867135L,
      6995737869226977784L, 5556896873712693708L, 5596590295381582227L, 8891034997940309933L, 7109870065239576402L,
      7112827998352247947L, 153872830078795637L, 5690262398681798357L, 5657121486175901994L, 9104419837890877372L,
      1672696748397622544L, 7283535870312701897L, 6872180620830963520L, 5826828696250161518L, 1808395681922860493L,
      4661462957000129214L, 5136065360280198718L, 7458340731200206743L, 2683681354335452463L, 5966672584960165394L,
      5836293898210272294L, 4773338067968132315L, 6513709525939172997L, 7637340908749011705L, 1198563204647900987L,
      6109872726999209364L, 958850563718320789L, 4887898181599367491L, 2611754858345611793L, 7820637090558987986L,
      489458958611068546L, 6256509672447190388L, 7770264796372675483L, 5005207737957752311L, 682188614985274902L,
      8008332380732403697L, 6625525006089305327L, 6406665904585922958L, 1611071190129533939L, 5125332723668738366L,
      4978205766845537474L, 8200532357869981386L, 4275780412210949635L, 6560425886295985109L, 1575949922397804547L,
      5248340709036788087L, 3105434345289198799L, 8397345134458860939L, 6813369359833673240L, 6717876107567088751L,
      7295369895237893754L, 5374300886053671001L, 3991621508819359841L, 8598881417685873602L, 2697245599369065423L,
      6879105134148698881L, 7691819701608117823L, 5503284107318959105L, 4308781353915539097L, 8805254571710334568L,
      6894050166264862555L, 7044203657368267654L, 9204588947753800367L, 5635362925894614123L, 9208345565573995455L,
      9016580681431382598L, 3665306460692661759L, 7213264545145106078L, 6621593983296039730L, 5770611636116084862L,
      8986624001378742108L, 4616489308892867890L, 3499950386361083363L, 7386382894228588624L, 5599920618177733380L,
      5909106315382870899L, 6324610901913141866L, 4727285052306296719L, 6904363128901468655L, 7563656083690074751L,
      5512957784129484362L, 6050924866952059801L, 2565691819932632328L, 4840739893561647841L, 207879048575150701L,
      7745183829698636545L, 5866629699833106606L, 6196147063758909236L, 4693303759866485285L, 4956917651007127389L,
      1909968600522233067L, 7931068241611403822L, 6745298575577483229L, 6344854593289123058L, 1706890045720076260L,
      5075883674631298446L, 5054860851317971332L, 8121413879410077514L, 4398428547366843807L, 6497131103528062011L,
      5363417245264430207L, 5197704882822449609L, 2446059388840589004L, 8316327812515919374L, 7603043836886852730L,
      6653062250012735499L, 7927109476880437346L, 5322449800010188399L, 8186361988875305038L, 8515919680016301439L,
      7564155960087622576L, 6812735744013041151L, 7895999175441053223L, 5450188595210432921L, 4472124932981887417L,
      8720301752336692674L, 3466051078029109543L, 6976241401869354139L, 4617515269794242796L, 5580993121495483311L,
      5538686623206349399L, 8929588994392773298L, 5172549782388248714L, 7143671195514218638L, 7827388640652509295L,
      5714936956411374911L, 727887690409141951L, 9143899130258199857L, 6698643526767492606L, 7315119304206559886L,
      1669566006672083762L, 5852095443365247908L, 8714350434821487656L, 4681676354692198327L, 1437457125744324640L,
      7490682167507517323L, 4144605808561874585L, 5992545734006013858L, 7005033461591409992L, 4794036587204811087L,
      70003547160262509L, 7670458539527697739L, 1956680082827375175L, 6136366831622158191L, 3410018473632855302L,
      4909093465297726553L, 883340371535329080L, 7854549544476362484L, 8792042223940347174L, 6283639635581089987L,
      8878308186523232901L, 5026911708464871990L, 3413297734476675998L, 8043058733543795184L, 5461276375162681596L,
      6434446986835036147L, 6213695507501100438L, 5147557589468028918L, 1281607591258970028L, 8236092143148846269L,
      205897738643396882L, 6588873714519077015L, 2009392598285672668L, 5271098971615261612L, 1607514078628538134L,
      8433758354584418579L, 4416696933176616176L, 6747006683667534863L, 5378031953912248102L, 5397605346934027890L,
      7991774377871708805L, 8636168555094444625L, 3563466967739958280L, 6908934844075555700L, 2850773574191966624L,
      5527147875260444560L, 2280618859353573299L, 8843436600416711296L, 3648990174965717279L, 7074749280333369037L,
      1074517732601618662L, 5659799424266695229L, 6393637408194160414L, 9055679078826712367L, 4695796630997791177L,
      7244543263061369894L, 67288490056322619L, 5795634610449095915L, 1898505199416013257L, 4636507688359276732L,
      1518804159532810606L, 7418412301374842771L, 4274761062623452130L, 5934729841099874217L, 1575134442727806543L,
      4747783872879899373L, 6794130776295110719L, 7596454196607838997L, 9025934834701221989L, 6077163357286271198L,
      3531399053019067268L, 4861730685829016958L, 6514468057157164137L, 7778769097326427133L, 8578474484080507458L,
      6223015277861141707L, 1328756365151540482L, 4978412222288913365L, 6597028314234097870L, 7965459555662261385L,
      1331873265919780784L, 6372367644529809108L, 1065498612735824627L, 5097894115623847286L, 4541747704930570025L,
      8156630584998155658L, 3577447513147001717L, 6525304467998524526L, 6551306825259511697L, 5220243574398819621L,
      3396371052836654196L, 8352389719038111394L, 1744844869796736390L, 6681911775230489115L, 3240550303208344274L,
      5345529420184391292L, 2592440242566675419L, 8552847072295026067L, 5992578795477635832L, 6842277657836020854L,
      1104714221640198342L, 5473822126268816683L, 2728445784683113836L, 8758115402030106693L, 2520838848122026975L,
      7006492321624085354L, 5706019893239531903L, 5605193857299268283L, 6409490321962580684L, 8968310171678829253L,
      8410510107769173933L, 7174648137343063403L, 1194384864102473662L, 5739718509874450722L, 4644856706023889253L,
      9183549615799121156L, 53073100154402158L, 7346839692639296924L, 7421156109607342373L, 5877471754111437539L,
      7781599295056829060L, 4701977403289150031L, 8069953843416418410L, 7523163845262640050L, 9222577334724359132L,
      6018531076210112040L, 7378061867779487306L, 4814824860968089632L, 5902449494223589845L, 7703719777548943412L,
      2065221561273923105L, 6162975822039154729L, 7186200471132003969L, 4930380657631323783L, 7593634784276558337L,
      7888609052210118054L, 1081769210616762369L, 6310887241768094443L, 2710089775864365057L, 5048709793414475554L,
      5857420635433402369L, 8077935669463160887L, 3837849794580578305L, 6462348535570528709L, 8604303057777328129L,
      5169878828456422967L, 8728116853592817665L, 8271806125530276748L, 6586289336264687617L, 6617444900424221398L,
      8958380283753660417L, 5293955920339377119L, 1632681004890062849L, 8470329472543003390L, 6301638422566010881L,
      6776263578034402712L, 5041310738052808705L, 5421010862427522170L, 343699775700336641L, 8673617379884035472L,
      549919641120538625L, 6938893903907228377L, 5973958935009296385L, 5551115123125782702L, 1089818333265526785L,
      8881784197001252323L, 3588383740595798017L, 7105427357601001858L, 6560055807218548737L, 5684341886080801486L,
      8937393460516749313L, 9094947017729282379L, 1387108685230112769L, 7275957614183425903L, 2954361355555045377L,
      5820766091346740722L, 6052837899185946625L, 4656612873077392578L, 1152921504606846977L, 7450580596923828125L, 1L,
      5960464477539062500L, 1L, 4768371582031250000L, 1L, 7629394531250000000L, 1L, 6103515625000000000L, 1L,
      4882812500000000000L, 1L, 7812500000000000000L, 1L, 6250000000000000000L, 1L, 5000000000000000000L, 1L,
      8000000000000000000L, 1L, 6400000000000000000L, 1L, 5120000000000000000L, 1L, 8192000000000000000L, 1L,
      6553600000000000000L, 1L, 5242880000000000000L, 1L, 8388608000000000000L, 1L, 6710886400000000000L, 1L,
      5368709120000000000L, 1L, 8589934592000000000L, 1L, 6871947673600000000L, 1L, 5497558138880000000L, 1L,
      8796093022208000000L, 1L, 7036874417766400000L, 1L, 5629499534213120000L, 1L, 9007199254740992000L, 1L,
      7205759403792793600L, 1L, 5764607523034234880L, 1L, 4611686018427387904L, 1L, 7378697629483820646L,
      3689348814741910324L, 5902958103587056517L, 1106804644422573097L, 4722366482869645213L, 6419466937650923963L,
      7555786372591432341L, 8426472692870523179L, 6044629098073145873L, 4896503746925463381L, 4835703278458516698L,
      7606551812282281028L, 7737125245533626718L, 1102436455425918676L, 6189700196426901374L, 4571297979082645264L,
      4951760157141521099L, 5501712790637071373L, 7922816251426433759L, 3268717242906448711L, 6338253001141147007L,
      4459648201696114131L, 5070602400912917605L, 9101741783469756789L, 8112963841460668169L, 5339414816696835055L,
      6490371073168534535L, 6116206260728423206L, 5192296858534827628L, 4892965008582738565L, 8307674973655724205L,
      5984069606361426541L, 6646139978924579364L, 4787255685089141233L, 5316911983139663491L, 5674478955442268148L,
      8507059173023461586L, 5389817513965718714L, 6805647338418769269L, 2467179603801619810L, 5444517870735015415L,
      3818418090412251009L, 8711228593176024664L, 6109468944659601615L, 6968982874540819731L, 6732249563098636453L,
      5575186299632655785L, 3541125243107954001L, 8920298079412249256L, 5665800388972726402L, 7136238463529799405L,
      2687965903807225960L, 5708990770823839524L, 2150372723045780768L, 9134385233318143238L, 7129945171615159552L,
      7307508186654514591L, 169932915179262157L, 5846006549323611672L, 7514643961627230372L, 4676805239458889338L,
      2322366354559873974L, 7482888383134222941L, 1871111759924843197L, 5986310706507378352L, 8875587037423695204L,
      4789048565205902682L, 3411120815197045840L, 7662477704329444291L, 7302467711686228506L, 6129982163463555433L,
      3997299761978027643L, 4903985730770844346L, 6887188624324332438L, 7846377169233350954L, 7330152984177021577L,
      6277101735386680763L, 7708796794712572423L, 5021681388309344611L, 633014213657192454L, 8034690221294951377L,
      6546845963964373411L, 6427752177035961102L, 1548127956429588405L, 5142201741628768881L, 6772525587256536209L,
      8227522786606030210L, 7146692124868547611L, 6582018229284824168L, 5717353699894838089L, 5265614583427859334L,
      8263231774657780795L, 8424983333484574935L, 7687147617339583786L, 6739986666787659948L, 6149718093871667029L,
      5391989333430127958L, 8609123289839243947L, 8627182933488204734L, 2706550819517059345L, 6901746346790563787L,
      4009915062984602637L, 5521397077432451029L, 8741955272500547595L, 8834235323891921647L, 8453105213888010667L,
      7067388259113537318L, 3073135356368498210L, 5653910607290829854L, 6147857099836708891L, 9046256971665327767L,
      4302548137625868741L, 7237005577332262213L, 8976061732213560478L, 5789604461865809771L, 1646826163657982898L,
      4631683569492647816L, 8696158560410206965L, 7410693711188236507L, 1001132845059645012L, 5928554968950589205L,
      6334929498160581494L, 4742843975160471364L, 5067943598528465196L, 7588550360256754183L, 2574686535532678828L,
      6070840288205403346L, 5749098043168053386L, 4856672230564322677L, 2754604027163487547L, 7770675568902916283L,
      6252040850832535236L, 6216540455122333026L, 8690981495407938512L, 4973232364097866421L, 5108110788955395648L,
      7957171782556586274L, 4483628447586722714L, 6365737426045269019L, 5431577165440333333L, 5092589940836215215L,
      6189936139723221828L, 8148143905337944345L, 680525786702379117L, 6518515124270355476L, 544420629361903293L,
      5214812099416284380L, 7814234132973343281L, 8343699359066055009L, 3279402575902573442L, 6674959487252844007L,
      4468196468093013915L, 5339967589802275205L, 9108580396587276617L, 8543948143683640329L, 5350356597684866779L,
      6835158514946912263L, 6124959685518848585L, 5468126811957529810L, 8589316563156989191L, 8749002899132047697L,
      4519534464196406897L, 6999202319305638157L, 9149650793469991003L, 5599361855444510526L, 3630371820034082479L,
      8958978968711216842L, 2119246097312621643L, 7167183174968973473L, 7229420099962962799L, 5733746539975178779L,
      249512857857504755L, 9173994463960286046L, 4088569387313917931L, 7339195571168228837L, 1426181102480179183L,
      5871356456934583069L, 6674968104097008831L, 4697085165547666455L, 7184648890648562227L, 7515336264876266329L,
      2272066188182923754L, 6012269011901013063L, 3662327357917294165L, 4809815209520810450L, 6619210701075745655L,
      7695704335233296721L, 1367365084866417240L, 6156563468186637376L, 8472589697376954439L, 4925250774549309901L,
      4933397350530608390L, 7880401239278895842L, 4204086946107063100L, 6304320991423116673L, 8897292778998515965L,
      5043456793138493339L, 1583811001085947287L, 8069530869021589342L, 6223446416479425982L, 6455624695217271474L,
      1289408318441630463L, 5164499756173817179L, 2876201062124259532L, 8263199609878107486L, 8291270514140725574L,
      6610559687902485989L, 4788342003941625298L, 5288447750321988791L, 5675348010524255400L, 8461516400515182066L,
      5391208002096898316L, 6769213120412145653L, 2468291994306563491L, 5415370496329716522L, 5663982410187161116L,
      8664592794127546436L, 1683674226815637140L, 6931674235302037148L, 8725637010936330358L, 5545339388241629719L,
      1446486386636198802L, 8872543021186607550L, 6003727033359828406L, 7098034416949286040L, 4802981626687862725L,
      5678427533559428832L, 3842385301350290180L, 9085484053695086131L, 7992490889531419449L, 7268387242956068905L,
      4549318304254180398L, 5814709794364855124L, 3639454643403344318L, 4651767835491884099L, 4756238122093630616L,
      7442828536787014559L, 2075957773236943501L, 5954262829429611647L, 3505440625960509963L, 4763410263543689317L,
      8338375722881273455L, 7621456421669902908L, 5962703527126216881L, 6097165137335922326L, 8459511636442883828L,
      4877732109868737861L, 4922934901783351901L, 7804371375789980578L, 4187347028111452718L, 6243497100631984462L,
      7039226437231072498L, 4994797680505587570L, 1942032335042947675L, 7991676288808940112L, 3107251736068716280L,
      6393341031047152089L, 8019824610967838509L, 5114672824837721671L, 8260534096145225969L, 8183476519740354675L,
      304133702235675419L, 6546781215792283740L, 243306961788540335L, 5237424972633826992L, 194645569430832268L,
      8379879956214123187L, 2156107318460286790L, 6703903964971298549L, 7258909076881094917L, 5363123171977038839L,
      7651801668875831096L, 8580997075163262143L, 6708859448088464268L, 6864797660130609714L, 9056436373212681737L,
      5491838128104487771L, 9089823505941100552L, 8786941004967180435L, 1630996757909074751L, 7029552803973744348L,
      1304797406327259801L, 5623642243178995478L, 4733186739803718164L, 8997827589086392765L, 5728424376314993901L,
      7198262071269114212L, 4582739501051995121L, 5758609657015291369L, 9200214822954461581L, 9213775451224466191L,
      9186320494614273045L, 7371020360979572953L, 5504381988320463275L, 5896816288783658362L, 8092854405398280943L,
      4717453031026926690L, 2784934709576714431L, 7547924849643082704L, 4455895535322743090L, 6038339879714466163L,
      5409390835629149634L, 4830671903771572930L, 8016861483245230030L, 7729075046034516689L, 3603606336337592240L,
      6183260036827613351L, 4727559476441028954L, 4946608029462090681L, 1937373173781868001L, 7914572847139345089L,
      8633820300163854287L, 6331658277711476071L, 8751730647502038591L, 5065326622169180857L, 5156710110630675711L,
      8104522595470689372L, 872038547525260492L, 6483618076376551497L, 6231654060133073878L, 5186894461101241198L,
      1295974433364548779L, 8299031137761985917L, 228884686012322885L, 6639224910209588733L, 5717130970922723793L,
      5311379928167670986L, 8263053591480089358L, 8498207885068273579L, 308164894771456841L, 6798566308054618863L,
      2091206323188120634L, 5438853046443695090L, 5362313873292406831L, 8702164874309912144L, 8579702197267850929L,
      6961731899447929715L, 8708436165185235905L, 5569385519558343772L, 6966748932148188724L, 8911016831293350036L,
      3768100661953281312L, 7128813465034680029L, 1169806122191669888L, 5703050772027744023L, 2780519305124291072L,
      9124881235244390437L, 2604156480827910553L, 7299904988195512349L, 7617348406775193928L, 5839923990556409879L,
      7938553132791110304L, 4671939192445127903L, 8195516913603843405L, 7475102707912204646L, 2044780617540418478L,
      5980082166329763716L, 9014522123516155429L, 4784065733063810973L, 5366943291441969181L, 7654505172902097557L,
      6742434858936195528L, 6123604138321678046L, 1704599072407046100L, 4898883310657342436L, 8742376887409457526L,
      7838213297051747899L, 1075082168258445910L, 6270570637641398319L, 2704740141977711890L, 5016456510113118655L,
      4008466520953124674L, 8026330416180989848L, 6413546433524999478L, 6421064332944791878L, 8820185961561909905L,
      5136851466355833503L, 1522125547136662440L, 8218962346169333605L, 590726468047704741L, 6575169876935466884L,
      472581174438163793L, 5260135901548373507L, 2222739346921486196L, 8416217442477397611L, 5401057362445333075L,
      6732973953981918089L, 2476171482585311299L, 5386379163185534471L, 3825611593439204201L, 8618206661096855154L,
      2431629734760816398L, 6894565328877484123L, 3789978195179608280L, 5515652263101987298L, 6721331370885596947L,
      8825043620963179677L, 8909455786045999954L, 7060034896770543742L, 3438215814094889640L, 5648027917416434993L,
      8284595873388777197L, 9036844667866295990L, 2187306953196312545L, 7229475734293036792L, 1749845562557050036L,
      5783580587434429433L, 6933899672158505514L, 4626864469947543547L, 13096515613938926L, 7402983151916069675L,
      1865628832353257443L, 5922386521532855740L, 1492503065882605955L, 4737909217226284592L, 1194002452706084764L,
      7580654747562055347L, 3755078331700690783L, 6064523798049644277L, 8538085887473418112L, 4851619038439715422L,
      3141119895236824166L, 7762590461503544675L, 6870466239749873827L, 6210072369202835740L, 5496372991799899062L,
      4968057895362268592L, 4397098393439919250L, 7948892632579629747L, 8880031836874825961L, 6359114106063703798L,
      3414676654757950445L, 5087291284850963038L, 6421090138548270680L, 8139666055761540861L, 8429069814306277926L,
      6511732844609232689L, 4898581444074067179L, 5209386275687386151L, 5763539562630208905L, 8335018041099817842L,
      5532314485466423924L, 6668014432879854274L, 736502773631228816L, 5334411546303883419L, 2433876626275938215L,
      8535058474086213470L, 7583551416783411467L, 6828046779268970776L, 6066841133426729173L, 5462437423415176621L,
      3008798499370428177L, 8739899877464282594L, 1124728784250774760L, 6991919901971426075L, 2744457434771574970L,
      5593535921577140860L, 2195565947817259976L, 8949657474523425376L, 3512905516507615961L, 7159725979618740301L,
      965650005835137607L, 5727780783694992240L, 8151217634151930732L, 9164449253911987585L, 3818576177788313364L,
      7331559403129590068L, 3054860942230650691L, 5865247522503672054L, 6133237568526430876L, 4692198018002937643L,
      6751264462192099863L, 7507516828804700229L, 8957348732136404618L, 6006013463043760183L, 9010553393080078856L,
      4804810770435008147L, 1674419492351197600L, 7687697232696013035L, 4523745595132871322L, 6150157786156810428L,
      3618996476106297057L, 4920126228925448342L, 6584545995626947969L, 7872201966280717348L, 3156575963519296104L,
      6297761573024573878L, 6214609585557347207L, 5038209258419659102L, 8661036483187788089L, 8061134813471454564L,
      6478960743616640295L, 6448907850777163651L, 7027843002264267398L, 5159126280621730921L, 3777599994440458757L,
      8254602048994769474L, 2354811176362823687L, 6603681639195815579L, 3728523348461214111L, 5282945311356652463L,
      4827493086139926451L, 8452712498170643941L, 5879314530452927160L, 6762169998536515153L, 2858777216991386566L,
      5409735998829212122L, 5976370588335019576L, 8655577598126739396L, 2183495311852210675L, 6924462078501391516L,
      9125493878965589187L, 5539569662801113213L, 5455720695801516188L, 8863311460481781141L, 6884478705911470739L,
      7090649168385424913L, 3662908557358221429L, 5672519334708339930L, 6619675660628487467L, 9076030935533343889L,
      1368109020150804139L, 7260824748426675111L, 2939161623491598473L, 5808659798741340089L, 506654891422323617L,
      4646927838993072071L, 2249998320508814055L, 7435084542388915313L, 9134020534926967972L, 5948067633911132251L,
      1773193205828708893L, 4758454107128905800L, 8797252194146787761L, 7613526571406249281L, 4852231473780084609L,
      6090821257124999425L, 2037110771653112526L, 4872657005699999540L, 1629688617322490021L, 7796251209119999264L,
      2607501787715984033L, 6237000967295999411L, 3930675837543742388L, 4989600773836799529L, 1299866262664038749L,
      7983361238138879246L, 5769134835004372321L, 6386688990511103397L, 2770633460632542696L, 5109351192408882717L,
      7750529990618899641L, 8174961907854212348L, 5022150355506418780L, 6539969526283369878L, 7707069099147045347L,
      5231975621026695903L, 631632057204770793L, 8371160993642713444L, 8389308921011453915L, 6696928794914170755L,
      8556121544180118293L, 5357543035931336604L, 6844897235344094635L, 8572068857490138567L, 5417812354437685931L,
      6857655085992110854L, 644901068808238421L, 5486124068793688683L, 2360595262417545899L, 8777798510069901893L,
      1932278012497118276L, 7022238808055921514L, 5235171224739604944L, 5617791046444737211L, 6032811387162639117L,
      8988465674311579538L, 5963149404718312264L, 7190772539449263630L, 8459868338516560134L, 5752618031559410904L,
      6767894670813248108L, 9204188850495057447L, 5294608251188331487L
    )

    private val tenPow18Squares: Array[BigInteger] = {
        val ss = new Array[BigInteger](8)
        ss(0) = BigInteger.valueOf(1000000000000000000L)
        var i = 1
        var s = ss(0)
        while (i <= 7) {
            s = s.multiply(s)
            ss(i) = s
            i += 1
        }
        ss
    }

    def getTenPow18Squares(n: Int): Array[BigInteger] = {
        var ss = tenPow18Squares
        var i  = ss.length
        if (n >= i) {
            var s = ss(i - 1)
            ss = java.util.Arrays.copyOf(ss, n + 1)
            while (i <= n) {
                s = s.multiply(s)
                ss(i) = s
                i += 1
            }
        }
        ss
    }

    //// -------------------- READ ----------------------

    private final val pow10Doubles: Array[Double] =
        Array(1, 1e+1, 1e+2, 1e+3, 1e+4, 1e+5, 1e+6, 1e+7, 1e+8, 1e+9, 1e+10, 1e+11, 1e+12, 1e+13, 1e+14, 1e+15, 1e+16,
          1e+17, 1e+18, 1e+19, 1e+20, 1e+21, 1e+22)
    /* Use the following code to generate `pow10Mantissas` in Scala REPL:
      val ms = new Array[Long](653)
      var pow10 = BigInt(10)
      var i = 342
      while (i >= 0) {
        ms(i) = ((BigInt(1) << (pow10.bitLength + 63)) / pow10).longValue
        pow10 *= 10
        i -= 1
      }
      pow10 = BigInt(1) << 63
      i = 343
      while (i < 653) {
        ms(i) = (pow10 >> (pow10.bitLength - 64)).longValue
        pow10 *= 10
        i += 1
      }
      ms.grouped(4).map(_.mkString("L, ")).mkString("Array(\n", "L,\n", "L\n)")
     */
    private final val pow10Mantissas: Array[Long] = Array(
      -4671960508600951122L, -1228264617323800998L, -7685194413468457480L, -4994806998408183946L, -1631822729582842029L,
      -7937418233630358124L, -5310086773610559751L, -2025922448585811785L, -8183730558007214222L, -5617977179081629873L,
      -2410785455424649437L, -8424269937281487754L, -5918651403174471789L, -2786628235540701832L, -8659171674854020501L,
      -6212278575140137722L, -3153662200497784248L, -8888567902952197011L, -6499023860262858360L, -3512093806901185046L,
      -9112587656954322510L, -6779048552765515233L, -3862124672529506138L, -215969822234494768L, -7052510166537641086L,
      -4203951689744663454L, -643253593753441413L, -7319562523736982739L, -4537767136243840520L, -1060522901877412746L,
      -7580355841314464822L, -4863758783215693124L, -1468012460592228501L, -7835036815511224669L, -5182110000961642932L,
      -1865951482774665761L, -8083748704375247957L, -5492999862041672042L, -2254563809124702148L, -8326631408344020699L,
      -5796603242002637969L, -2634068034075909558L, -8563821548938525330L, -6093090917745768758L, -3004677628754823043L,
      -8795452545612846258L, -6382629663588669919L, -3366601061058449494L, -9021654690802612790L, -6665382345075878084L,
      -3720041912917459700L, -38366372719436721L, -6941508010590729807L, -4065198994811024355L, -469812725086392539L,
      -7211161980820077193L, -4402266457597708587L, -891147053569747830L, -7474495936122174250L, -4731433901725329908L,
      -1302606358729274481L, -7731658001846878407L, -5052886483881210105L, -1704422086424124727L, -7982792831656159810L,
      -5366805021142811859L, -2096820258001126919L, -8228041688891786181L, -5673366092687344822L, -2480021597431793123L,
      -8467542526035952558L, -5972742139117552794L, -2854241655469553088L, -8701430062309552536L, -6265101559459552766L,
      -3219690930897053053L, -8929835859451740015L, -6550608805887287114L, -3576574988931720989L, -9152888395723407474L,
      -6829424476226871438L, -3925094576856201394L, -294682202642863838L, -7101705404292871755L, -4265445736938701790L,
      -720121152745989333L, -7367604748107325189L, -4597819916706768583L, -1135588877456072824L, -7627272076051127371L,
      -4922404076636521310L, -1541319077368263733L, -7880853450996246689L, -5239380795317920458L, -1937539975720012668L,
      -8128491512466089774L, -5548928372155224313L, -2324474446766642487L, -8370325556870233411L, -5851220927660403859L,
      -2702340141148116920L, -8606491615858654931L, -6146428501395930760L, -3071349608317525546L, -8837122532839535322L,
      -6434717147622031249L, -3431710416100151157L, -9062348037703676329L, -6716249028702207507L, -3783625267450371480L,
      -117845565885576446L, -6991182506319567135L, -4127292114472071014L, -547429124662700864L, -7259672230555269896L,
      -4462904269766699466L, -966944318780986428L, -7521869226879198374L, -4790650515171610063L, -1376627125537124675L,
      -7777920981101784778L, -5110715207949843068L, -1776707991509915931L, -8027971522334779313L, -5423278384491086237L,
      -2167411962186469893L, -8272161504007625539L, -5728515861582144020L, -2548958808550292121L, -8510628282985014432L,
      -6026599335303880135L, -2921563150702462265L, -8743505996830120772L, -6317696477610263061L, -3285434578585440922L,
      -8970925639256982432L, -6601971030643840136L, -3640777769877412266L, -9193015133814464522L, -6879582898840692749L,
      -3987792605123478032L, -373054737976959636L, -7150688238876681629L, -4326674280168464132L, -796656831783192261L,
      -7415439547505577019L, -4657613415954583370L, -1210330751515841308L, -7673985747338482674L, -4980796165745715438L,
      -1614309188754756393L, -7926472270612804602L, -5296404319838617848L, -2008819381370884406L, -8173041140997884610L,
      -5604615407819967859L, -2394083241347571919L, -8413831053483314306L, -5905602798426754978L, -2770317479606055818L,
      -8648977452394866743L, -6199535797066195524L, -3137733727905356501L, -8878612607581929669L, -6486579741050024183L,
      -3496538657885142324L, -9102865688819295809L, -6766896092596731857L, -3846934097318526917L, -196981603220770742L,
      -7040642529654063570L, -4189117143640191558L, -624710411122851544L, -7307973034592864071L, -4523280274813692185L,
      -1042414325089727327L, -7569037980822161435L, -4849611457600313890L, -1450328303573004458L, -7823984217374209643L,
      -5168294253290374149L, -1848681798185579782L, -8072955151507069220L, -5479507920956448621L, -2237698882768172872L,
      -8316090829371189901L, -5783427518286599473L, -2617598379430861437L, -8553528014785370254L, -6080224000054324913L,
      -2988593981640518238L, -8785400266166405755L, -6370064314280619289L, -3350894374423386208L, -9011838011655698236L,
      -6653111496142234891L, -3704703351750405709L, -19193171260619233L, -6929524759678968877L, -4050219931171323192L,
      -451088895536766085L, -7199459587351560659L, -4387638465762062920L, -872862063775190746L, -7463067817500576073L,
      -4717148753448332187L, -1284749923383027329L, -7720497729755473937L, -5038936143766954517L, -1686984161281305242L,
      -7971894128441897632L, -5353181642124984136L, -2079791034228842266L, -8217398424034108273L, -5660062011615247437L,
      -2463391496091671392L, -8457148712698376476L, -5959749872445582691L, -2838001322129590460L, -8691279853972075893L,
      -6252413799037706963L, -3203831230369745799L, -8919923546622172981L, -6538218414850328322L, -3561087000135522498L,
      -9143208402725783417L, -6817324484979841368L, -3909969587797413806L, -275775966319379353L, -7089889006590693952L,
      -4250675239810979535L, -701658031336336515L, -7356065297226292178L, -4583395603105477319L, -1117558485454458744L,
      -7616003081050118571L, -4908317832885260310L, -1523711272679187483L, -7869848573065574033L, -5225624697904579637L,
      -1920344853953336643L, -8117744561361917258L, -5535494683275008668L, -2307682335666372931L, -8359830487432564938L,
      -5838102090863318269L, -2685941595151759932L, -8596242524610931813L, -6133617137336276863L, -3055335403242958174L,
      -8827113654667930715L, -6422206049907525490L, -3416071543957018958L, -9052573742614218705L, -6704031159840385477L,
      -3768352931373093942L, -98755145788979524L, -6979250993759194058L, -4112377723771604669L, -528786136287117932L,
      -7248020362820530564L, -4448339435098275301L, -948738275445456222L, -7510490449794491995L, -4776427043815727089L,
      -1358847786342270957L, -7766808894105001205L, -5096825099203863602L, -1759345355577441598L, -8017119874876982855L,
      -5409713825168840664L, -2150456263033662926L, -8261564192037121185L, -5715269221619013577L, -2532400508596379068L,
      -8500279345513818773L, -6013663163464885563L, -2905392935903719049L, -8733399612580906262L, -6305063497298744923L,
      -3269643353196043250L, -8961056123388608887L, -6589634135808373205L, -3625356651333078602L, -9183376934724255983L,
      -6867535149977932074L, -3972732919045027189L, -354230130378896082L, -7138922859127891907L, -4311967555482476980L,
      -778273425925708321L, -7403949918844649557L, -4643251380128424042L, -1192378206733142148L, -7662765406849295699L,
      -4966770740134231719L, -1596777406740401745L, -7915514906853832947L, -5282707615139903279L, -1991698500497491195L,
      -8162340590452013853L, -5591239719637629412L, -2377363631119648861L, -8403381297090862394L, -5892540602936190089L,
      -2753989735242849707L, -8638772612167862923L, -6186779746782440750L, -3121788665050663033L, -8868646943297746252L,
      -6474122660694794911L, -3480967307441105734L, -9093133594791772940L, -6754730975062328271L, -3831727700400522434L,
      -177973607073265139L, -7028762532061872568L, -4174267146649952806L, -606147914885053103L, -7296371474444240046L,
      -4508778324627912153L, -1024286887357502287L, -7557708332239520786L, -4835449396872013078L, -1432625727662628443L,
      -7812920107430224633L, -5154464115860392887L, -1831394126398103205L, -8062150356639896359L, -5466001927372482545L,
      -2220816390788215277L, -8305539271883716405L, -5770238071427257602L, -2601111570856684098L, -8543223759426509417L,
      -6067343680855748868L, -2972493582642298180L, -8775337516792518219L, -6357485877563259869L, -3335171328526686933L,
      -9002011107970261189L, -6640827866535438582L, -3689348814741910324L, -9223372036854775808L, -6917529027641081856L,
      -4035225266123964416L, -432345564227567616L, -7187745005283311616L, -4372995238176751616L, -854558029293551616L,
      -7451627795949551616L, -4702848726509551616L, -1266874889709551616L, -7709325833709551616L, -5024971273709551616L,
      -1669528073709551616L, -7960984073709551616L, -5339544073709551616L, -2062744073709551616L, -8206744073709551616L,
      -5646744073709551616L, -2446744073709551616L, -8446744073709551616L, -5946744073709551616L, -2821744073709551616L,
      -8681119073709551616L, -6239712823709551616L, -3187955011209551616L, -8910000909647051616L, -6525815118631426616L,
      -3545582879861895366L, -9133518327554766460L, -6805211891016070171L, -3894828845342699810L, -256850038250986858L,
      -7078060301547948643L, -4235889358507547899L, -683175679707046970L, -7344513827457986212L, -4568956265895094861L,
      -1099509313941480672L, -7604722348854507276L, -4894216917640746191L, -1506085128623544835L, -7858832233030797378L,
      -5211854272861108819L, -1903131822648998119L, -8106986416796705681L, -5522047002568494197L, -2290872734783229842L,
      -8349324486880600507L, -5824969590173362730L, -2669525969289315508L, -8585982758446904049L, -6120792429631242157L,
      -3039304518611664792L, -8817094351773372351L, -6409681921289327535L, -3400416383184271515L, -9042789267131251553L,
      -6691800565486676537L, -3753064688430957767L, -79644842111309304L, -6967307053960650171L, -4097447799023424810L,
      -510123730351893109L, -7236356359111015049L, -4433759430461380907L, -930513269649338230L, -7499099821171918250L,
      -4762188758037509908L, -1341049929119499481L, -7755685233340769032L, -5082920523248573386L, -1741964635633328828L,
      -8006256924911912374L, -5396135137712502563L, -2133482903713240300L, -8250955842461857044L, -5702008784649933400L,
      -2515824962385028846L, -8489919629131724885L, -6000713517987268202L, -2889205879056697349L, -8723282702051517699L,
      -6292417359137009220L, -3253835680493873621L, -8951176327949752869L, -6577284391509803182L, -3609919470959866074L,
      -9173728696990998152L, -6855474852811359786L, -3957657547586811828L, -335385916056126881L, -7127145225176161157L,
      -4297245513042813542L, -759870872876129024L, -7392448323188662496L, -4628874385558440216L, -1174406963520662366L,
      -7651533379841495835L, -4952730706374481889L, -1579227364540714458L, -7904546130479028392L, -5268996644671397586L,
      -1974559787411859078L, -8151628894773493780L, -5577850100039479321L, -2360626606621961247L, -8392920656779807636L,
      -5879464802547371641L, -2737644984756826647L, -8628557143114098510L, -6174010410465235234L, -3105826994654156138L,
      -8858670899299929442L, -6461652605697523899L, -3465379738694516970L, -9083391364325154962L, -6742553186979055799L,
      -3816505465296431844L, -158945813193151901L, -7016870160886801794L, -4159401682681114339L, -587566084924005019L,
      -7284757830718584993L, -4494261269970843337L, -1006140569036166268L, -7546366883288685774L, -4821272585683469313L,
      -1414904713676948737L, -7801844473689174817L, -5140619573684080617L, -1814088448677712867L, -8051334308064652398L,
      -5452481866653427593L, -2203916314889396588L, -8294976724446954723L, -5757034887131305500L, -2584607590486743971L,
      -8532908771695296838L, -6054449946191733143L, -2956376414312278525L, -8765264286586255934L, -6344894339805432014L,
      -3319431906329402113L, -8992173969096958177L, -6628531442943809817L, -3673978285252374367L, -9213765455923815836L,
      -6905520801477381891L, -4020214983419339459L, -413582710846786420L, -7176018221920323369L, -4358336758973016307L,
      -836234930288882479L, -7440175859071633406L, -4688533805412153853L, -1248981238337804412L, -7698142301602209614L,
      -5010991858575374113L, -1652053804791829737L, -7950062655635975442L, -5325892301117581398L, -2045679357969588844L,
      -8196078626372074883L, -5633412264537705700L, -2430079312244744221L, -8436328597794046994L, -5933724728815170839L,
      -2805469892591575644L, -8670947710510816634L, -6226998619711132888L, -3172062256211528206L, -8900067937773286985L,
      -6513398903789220827L, -3530062611309138130L, -9123818159709293187L, -6793086681209228580L, -3879672333084147821L,
      -237904397927796872L, -7066219276345954901L, -4221088077005055722L, -664674077828931749L, -7332950326284164199L,
      -4554501889427817345L, -1081441343357383777L, -7593429867239446717L, -4880101315621920492L, -1488440626100012711L,
      -7847804418953589800L, -5198069505264599346L, -1885900863153361279L, -8096217067111932656L, -5508585315462527915L,
      -2274045625900771990L, -8338807543829064350L, -5811823411358942533L, -2653093245771290262L, -8575712306248138270L,
      -6107954364382784934L, -3023256937051093263L, -8807064613298015146L, -6397144748195131028L, -3384744916816525881L,
      -9032994600651410532L, -6679557232386875260L, -3737760522056206171L, -60514634142869810L, -6955350673980375487L,
      -4082502324048081455L, -491441886632713915L, -7224680206786528053L, -4419164240055772162L, -912269281642327298L,
      -7487697328667536418L, -4747935642407032618L, -1323233534581402868L, -7744549986754458649L, -5069001465015685407L,
      -1724565812842218855L, -7995382660667468640L, -5382542307406947896L, -2116491865831296966L, -8240336443785642460L,
      -5688734536304665171L, -2499232151953443560L, -8479549122611984081L, -5987750384837592197L, -2873001962619602342L,
      -8713155254278333320L, -6279758049420528746L, -3238011543348273028L, -8941286242233752499L, -6564921784364802720L,
      -3594466212028615495L, -9164070410158966541L, -6843401994271320272L, -3942566474411762436L, -316522074587315140L,
      -7115355324258153819L, -4282508136895304370L, -741449152691742558L, -7380934748073420955L, -4614482416664388289L,
      -1156417002403097458L, -7640289654143017767L, -4938676049251384305L, -1561659043136842477L, -7893565929601608404L,
      -5255271393574622601L, -1957403223540890347L, -8140906042354138323L, -5564446534515285000L, -2343872149716718346L,
      -8382449121214030822L, -5866375383090150624L, -2721283210435300376L, -8618331034163144591L, -6161227774276542835L,
      -3089848699418290639L, -8848684464777513506L, -6449169562544503978L, -3449775934753242068L, -9073638986861858149L,
      -6730362715149934782L, -3801267375510030573L, -139898200960150313L, -7004965403241175802L, -4144520735624081848L,
      -568964901102714406L, -7273132090830278360L, -4479729095110460046L, -987975350460687153L, -7535013621679011327L,
      -4807081008671376254L, -1397165242411832414L, -7790757304148477115L, -5126760611758208489L, -1796764746270372707L,
      -8040506994060064798L, -5438947724147693094L, -2186998636757228463L, -8284403175614349646L, -5743817951090549153L,
      -2568086420435798537L, -8522583040413455942L, -6041542782089432023L, -2940242459184402125L, -8755180564631333184L,
      -6332289687361778576L, -3303676090774835316L, -8982326584375353929L, -6616222212041804507L, -3658591746624867729L,
      -9204148869281624187L, -6893500068174642330L, -4005189066790915008L, -394800315061255856L, -7164279224554366766L,
      -4343663012265570553L, -817892746904575288L, -7428711994456441411L, -4674203974643163860L, -1231068949876566920L,
      -7686947121313936181L, -4996997883215032323L, -1634561335591402499L, -7939129862385708418L, -5312226309554747619L,
      -2028596868516046619L, -8185402070463610993L, -5620066569652125837L
    )
    /* Use the following code to generate `nibbles` in Scala REPL:
      val ns = new Array[Byte](256)
      java.util.Arrays.fill(ns, -1: Byte)
      ns('0') = 0
      ns('1') = 1
      ns('2') = 2
      ns('3') = 3
      ns('4') = 4
      ns('5') = 5
      ns('6') = 6
      ns('7') = 7
      ns('8') = 8
      ns('9') = 9
      ns('A') = 10
      ns('B') = 11
      ns('C') = 12
      ns('D') = 13
      ns('E') = 14
      ns('F') = 15
      ns('a') = 10
      ns('b') = 11
      ns('c') = 12
      ns('d') = 13
      ns('e') = 14
      ns('f') = 15
      ns.grouped(16).map(_.mkString(", ")).mkString("Array(\n", ",\n", "\n)")
     */
    final val nibbles: Array[Byte] = Array(
      -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
      -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, -1,
      -1, -1, -1, -1, -1, -1, 10, 11, 12, 13, 14, 15, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
      -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 10, 11, 12, 13, 14, 15, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
      -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
      -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
      -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
      -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
      -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1
    )
    /* Use the following code to generate `base64Bytes` in Scala REPL:
      val bs = new Array[Byte](256)
      java.util.Arrays.fill(bs, -1: Byte)
      val ds = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
      var i = 0
      while (i < ds.length) {
        bs(ds.charAt(i).toInt) = i.toByte
        i += 1
      }
      bs.grouped(16).map(_.mkString(", ")).mkString("Array(\n", ",\n", "\n)")
     */
    private final val base64Bytes: Array[Byte] = Array(
      -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
      -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 62, -1, -1, -1, 63, 52, 53, 54, 55, 56, 57, 58, 59,
      60, 61, -1, -1, -1, -1, -1, -1, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21,
      22, 23, 24, 25, -1, -1, -1, -1, -1, -1, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43,
      44, 45, 46, 47, 48, 49, 50, 51, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
      -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
      -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
      -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
      -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1
    )
    /* Use the following code to generate `base64UrlBytes` in Scala REPL:
      val bs = new Array[Byte](256)
      java.util.Arrays.fill(bs, -1: Byte)
      val ds = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
      var i = 0
      while (i < ds.length) {
        bs(ds.charAt(i).toInt) = i.toByte
        i += 1
      }
      bs.grouped(16).map(_.mkString(", ")).mkString("Array(\n", ",\n", "\n)")
     */
    private final val base64UrlBytes: Array[Byte] = Array(
      -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
      -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 62, -1, -1, 52, 53, 54, 55, 56, 57, 58, 59,
      60, 61, -1, -1, -1, -1, -1, -1, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21,
      22, 23, 24, 25, -1, -1, -1, -1, 63, -1, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43,
      44, 45, 46, 47, 48, 49, 50, 51, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
      -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
      -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
      -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
      -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1
    )

    private final val hexDigits: Array[Char] =
        Array('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')
    /* Use the following code to generate `dumpBorder` in Scala REPL:
      "\n+----------+-------------------------------------------------+------------------+".toCharArray
        .grouped(16).map(_.map(_.toInt).mkString(", ")).mkString("Array(\n", ",\n", "\n)")
     */
    private final val dumpBorder: Array[Char] = Array(
      10, 43, 45, 45, 45, 45, 45, 45, 45, 45, 45, 45, 43, 45, 45, 45, 45, 45, 45, 45, 45, 45, 45, 45, 45, 45, 45, 45,
      45, 45, 45, 45, 45, 45, 45, 45, 45, 45, 45, 45, 45, 45, 45, 45, 45, 45, 45, 45, 45, 45, 45, 45, 45, 45, 45, 45,
      45, 45, 45, 45, 45, 45, 43, 45, 45, 45, 45, 45, 45, 45, 45, 45, 45, 45, 45, 45, 45, 45, 45, 45, 45, 43
    )
    /* Use the following code to generate `dumpHeader` in Scala REPL:
      "\n|          |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f | 0123456789abcdef |".toCharArray
        .grouped(16).map(_.map(_.toInt).mkString(", ")).mkString("Array(\n", ",\n", "\n)")
     */
    private final val dumpHeader: Array[Char] = Array(
      10, 124, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 124, 32, 32, 48, 32, 32, 49, 32, 32, 50, 32, 32, 51, 32, 32, 52,
      32, 32, 53, 32, 32, 54, 32, 32, 55, 32, 32, 56, 32, 32, 57, 32, 32, 97, 32, 32, 98, 32, 32, 99, 32, 32, 100, 32,
      32, 101, 32, 32, 102, 32, 124, 32, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 97, 98, 99, 100, 101, 102, 32, 124
    )

    /** The default math context used for rounding of `BigDecimal` values when parsing. */
    final val bigDecimalMathContext: MathContext = MathContext.DECIMAL128

    /** The default limit for number of decimal digits in mantissa of parsed `BigDecimal` values. */
    final val bigDecimalDigitsLimit: Int = 308

    /** The default limit for scale of parsed `BigDecimal` values. */
    final val bigDecimalScaleLimit: Int = 6178

    /** The maximum number of digits in `BigInt` values. */
    final val bigIntDigitsLimit: Int = 308

}
