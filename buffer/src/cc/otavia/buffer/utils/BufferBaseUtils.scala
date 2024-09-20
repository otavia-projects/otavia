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

import scala.annotation.switch

trait BufferBaseUtils {

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
        else if (q0 < 100) buffer.writeShortLE(BufferConstants.digits(q0))
        else buffer.writeMediumLE(BufferConstants.digits(q0 - 100) << 8 | 0x31)
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

    protected final def byteToChar(b2: Byte): Char = (b2: @switch) match
        case 'b'  => '\b'
        case 'f'  => '\f'
        case 'n'  => '\n'
        case 'r'  => '\r'
        case 't'  => '\t'
        case '"'  => '"'
        case '/'  => '/'
        case '\\' => '\\'

    /** Checks if a character does not require JSON escaping or encoding.
     *
     *  @param ch
     *    the character to check
     *  @return
     *    `true` if the character is a basic ASCII character (code point less than `0x80`) that does not need JSON
     *    escaping
     */
    final def isNonEscapedAscii(ch: Char): Boolean = ch < 0x80 && BufferConstants.escapedChars(ch) == 0

    protected final def rop(g1: Long, g0: Long, cp: Long): Long = {
        val x = Math.multiplyHigh(g0, cp) + (g1 * cp >>> 1)
        Math.multiplyHigh(g1, cp) + (x >>> 63) | (-x ^ x) >>> 63
    }

    protected final def rop(g: Long, cp: Int): Int = {
        val x = Math.multiplyHigh(g, cp.toLong << 32)
        (x >>> 31).toInt | -x.toInt >>> 31
    }

    // Adoption of a nice trick from Daniel Lemire's blog that works for numbers up to 10^18:
    // https://lemire.me/blog/2021/06/03/computing-the-number-of-digits-of-an-integer-even-faster/
    protected final def digitCount(q0: Long): Int =
        (BufferConstants.offsets(java.lang.Long.numberOfLeadingZeros(q0)) + q0 >> 58).toInt

    protected final def writePositiveIntDigits(q: Int, p: Int, buffer: Buffer, ds: Array[Short]): Unit = {
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

    protected final def write2Digits(buffer: Buffer, q0: Int, ds: Array[Short]): Unit =
        buffer.writeShortLE(ds(q0))

    protected final def write3Digits(buffer: Buffer, q0: Int, ds: Array[Short]): Unit = {
        val q1 = q0 * 1311 >> 17 // divide a small positive int by 100
        buffer.writeMediumLE(ds(q0 - q1 * 100) << 8 | q1 + '0')
    }

    protected final def write4Digits(buffer: Buffer, q0: Int, ds: Array[Short]): Unit = {
        val q1 = q0 * 5243 >> 19 // divide a small positive int by 100
        val d1 = ds(q0 - q1 * 100) << 16
        val d2 = ds(q1)
        buffer.writeIntLE(d1 | d2)
    }

    protected final def write8Digits(buffer: Buffer, q0: Long, ds: Array[Short]): Unit = {
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

    protected final def write18Digits(buffer: Buffer, x: Long, ds: Array[Short]): Unit = {
        val q1 = Math.multiplyHigh(x, 6189700196426901375L) >>> 25 // divide a positive long by 100000000
        val q2 = (q1 >> 8) * 1441151881 >> 49                      // divide a small positive long by 100000000
        write2Digits(buffer, q2.toInt, ds)
        write8Digits(buffer, q1 - q2 * 100000000, ds)
        write8Digits(buffer, x - q1 * 100000000, ds)
    }

    protected final def isLeap(year: Int): Boolean =
        (year & 0x3) == 0 && (year * -1030792151 - 2061584303 > -1975684958 || (year & 0xf) == 0) // year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

}
