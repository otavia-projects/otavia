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

trait BufferNumberBaseUtils extends BufferBaseUtils {

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
        val ds = BufferConstants.digits
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
        val ds = BufferConstants.digits
        val q0 =
            if (int >= 0) int
            else {
                buffer.writeByte('-')
                -int
            }
        buffer.writerOffset(buffer.writerOffset + digitCount(q0))
        writePositiveIntDigits(q0, buffer.writerOffset, buffer, ds)
    } else buffer.writeBytes(BufferConstants.MIN_INT_BYTES)

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
        val ds = BufferConstants.digits
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
                val g      = BufferConstants.gs(e10 + 324 << 1) + 1
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
            val ds  = BufferConstants.digits
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
                val g1     = BufferConstants.gs(i)
                val g0     = BufferConstants.gs(i + 1)
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
            val ds  = BufferConstants.digits
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

    protected final def writeSignificantFractionDigits(
        x: Int,
        p: Int,
        posLim: Int,
        buffer: Buffer,
        ds: Array[Short]
    ): Unit = {
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

}
