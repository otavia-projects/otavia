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

import java.math.BigInteger
import scala.language.unsafeNulls

trait BufferNumberUtils extends BufferBaseUtils {

    final def readStringAsShort(buffer: Buffer): Short = {
        val isNeg = buffer.skipIfNextIs('-')
        if (isNeg && !buffer.nextInRange('0', '9'))
            throw new NumberFormatException(s"except number at '-', but got ${buffer.getByte(buffer.readerOffset)}")
        var x: Int = 0
        val limit = if (isNeg) 32768 else 32767
        while (buffer.readableBytes > 0 && buffer.nextInRange('0', '9')) {
            x = x * 10 + (buffer.readByte - '0')
            if (x > limit) throw new NumberFormatException(s"short value overflow error")
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

    final def readStringAsLong(buffer: Buffer): Long = {
        var b = buffer.readByte
        var s = -1L
        if (b == '+') b = buffer.readByte
        if (b == '-') {
            b = buffer.readByte
            s = 0L
        }
        if (b < '0' || b > '9') throw new NumberFormatException(s"expected digit, but got '${b.toChar}'")
        var x = ('0' - b).toLong
        while (buffer.readableBytes > 0) {
            b = buffer.getByte(buffer.readerOffset)
            if (b < '0' || b > '9') {
                x ^= s
                x -= s
                if ((s & x) == Long.MinValue) throw new NumberFormatException(s"long value overflow error")
                return x
            }
            buffer.skipReadableBytes(1)
            if (x < -922337203685477580L || {
                x = x * 10 + ('0' - b)
                x > 0
            }) throw new NumberFormatException(s"long value overflow error")
        }
        x ^= s
        x -= s
        if ((s & x) == Long.MinValue) throw new NumberFormatException(s"long value overflow error")
        x
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

    final def readStringAsFloat(buffer: Buffer): Float = {
        var b = buffer.readByte
        var isNeg = false
        if (b == '+') b = buffer.readByte
        if (b == '-') {
            isNeg = true
            b = buffer.readByte
        }
        if (b < '0' || b > '9')
            throw new NumberFormatException(s"expected digit, but got '${b.toChar}'")

        // Parse mantissa as a Long, tracking overflow as exponent adjustment
        var m10: Long = (b - '0').toLong
        var e10       = 0
        var digits    = 1
        var continue  = true
        while (continue && buffer.readableBytes > 0) {
            b = buffer.getByte(buffer.readerOffset)
            if (b < '0' || b > '9') continue = false
            else {
                buffer.skipReadableBytes(1)
                if (m10 < 922337203685477580L) { m10 = m10 * 10 + (b - '0'); digits += 1 }
                else e10 += 1
            }
        }

        // Parse fractional part
        if (buffer.readableBytes > 0 && buffer.getByte(buffer.readerOffset) == '.') {
            buffer.skipReadableBytes(1)
            var noFracDigits = true
            continue = true
            while (continue && buffer.readableBytes > 0) {
                b = buffer.getByte(buffer.readerOffset)
                if (b < '0' || b > '9') {
                    if (noFracDigits) throw new NumberFormatException("expected digit after '.'")
                    continue = false
                } else {
                    buffer.skipReadableBytes(1)
                    noFracDigits = false
                    if (m10 < 922337203685477580L) { m10 = m10 * 10 + (b - '0'); digits += 1 }
                    e10 -= 1
                }
            }
        }

        // Parse exponent
        if (buffer.readableBytes > 0) {
            b = buffer.getByte(buffer.readerOffset)
            if ((b | 0x20) == 'e') {
                buffer.skipReadableBytes(1)
                val expSign =
                    if (buffer.readableBytes > 0 && buffer.getByte(buffer.readerOffset) == '-') {
                        buffer.skipReadableBytes(1); -1
                    } else {
                        if (buffer.readableBytes > 0 && buffer.getByte(buffer.readerOffset) == '+')
                            buffer.skipReadableBytes(1)
                        1
                    }
                if (buffer.readableBytes == 0 || buffer.getByte(buffer.readerOffset) < '0' ||
                    buffer.getByte(buffer.readerOffset) > '9')
                    throw new NumberFormatException("expected digit in exponent")
                var exp      = 0
                continue = true
                while (continue && buffer.readableBytes > 0) {
                    b = buffer.getByte(buffer.readerOffset)
                    if (b < '0' || b > '9') continue = false
                    else {
                        buffer.skipReadableBytes(1)
                        exp = exp * 10 + (b - '0')
                        if (exp > 10000) throw new NumberFormatException("exponent too large")
                    }
                }
                e10 += exp * expSign
            }
        }

        // Three-tier conversion with Float-specific bounds
        val x: Float =
            if (m10 == 0) 0.0f
            else if (e10 == 0) m10.toFloat
            else if (m10 < 4294967296L && e10 >= -22 && e10 <= 19 - digits) {
                val pow10 = BufferConstants.pow10Doubles
                if (e10 < 0) (m10 / pow10(-e10)).toFloat
                else if (e10 <= 22) (m10 * pow10(e10)).toFloat
                else (m10 * math.pow(10, e10)).toFloat
            } else {
                if (e10 >= 39) Float.PositiveInfinity
                else if (e10 < -64) 0.0f
                else (m10 * math.pow(10, e10)).toFloat
            }
        if (isNeg) -x else x
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
                if (e10 < 0) {
                    buffer.writeShortLE(0x2d45) // E-
                    e10 = -e10
                } else {
                    buffer.writeShortLE(0x2b45) // E+
                }
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

    final def readStringAsDouble(buffer: Buffer): Double = {
        var b = buffer.readByte
        var isNeg = false
        if (b == '+') b = buffer.readByte
        if (b == '-') {
            isNeg = true
            b = buffer.readByte
        }
        if (b < '0' || b > '9')
            throw new NumberFormatException(s"expected digit, but got '${b.toChar}'")

        // Parse mantissa as a Long, tracking overflow as exponent adjustment
        var m10: Long = (b - '0').toLong
        var e10       = 0
        var digits    = 1
        var continue  = true
        while (continue && buffer.readableBytes > 0) {
            b = buffer.getByte(buffer.readerOffset)
            if (b < '0' || b > '9') continue = false
            else {
                buffer.skipReadableBytes(1)
                if (m10 < 922337203685477580L) { m10 = m10 * 10 + (b - '0'); digits += 1 }
                else e10 += 1
            }
        }

        // Parse fractional part
        if (buffer.readableBytes > 0 && buffer.getByte(buffer.readerOffset) == '.') {
            buffer.skipReadableBytes(1)
            var noFracDigits = true
            continue = true
            while (continue && buffer.readableBytes > 0) {
                b = buffer.getByte(buffer.readerOffset)
                if (b < '0' || b > '9') {
                    if (noFracDigits) throw new NumberFormatException("expected digit after '.'")
                    continue = false
                } else {
                    buffer.skipReadableBytes(1)
                    noFracDigits = false
                    if (m10 < 922337203685477580L) { m10 = m10 * 10 + (b - '0'); digits += 1 }
                    e10 -= 1
                }
            }
        }

        // Parse exponent
        if (buffer.readableBytes > 0) {
            b = buffer.getByte(buffer.readerOffset)
            if ((b | 0x20) == 'e') {
                buffer.skipReadableBytes(1)
                val expSign =
                    if (buffer.readableBytes > 0 && buffer.getByte(buffer.readerOffset) == '-') {
                        buffer.skipReadableBytes(1); -1
                    } else {
                        if (buffer.readableBytes > 0 && buffer.getByte(buffer.readerOffset) == '+')
                            buffer.skipReadableBytes(1)
                        1
                    }
                if (buffer.readableBytes == 0 || buffer.getByte(buffer.readerOffset) < '0' ||
                    buffer.getByte(buffer.readerOffset) > '9')
                    throw new NumberFormatException("expected digit in exponent")
                var exp      = 0
                continue = true
                while (continue && buffer.readableBytes > 0) {
                    b = buffer.getByte(buffer.readerOffset)
                    if (b < '0' || b > '9') continue = false
                    else {
                        buffer.skipReadableBytes(1)
                        exp = exp * 10 + (b - '0')
                        if (exp > 10000) throw new NumberFormatException("exponent too large")
                    }
                }
                e10 += exp * expSign
            }
        }

        // Three-tier conversion
        val x: Double =
            if (m10 == 0) 0.0
            else if (e10 == 0) m10.toDouble
            else if (m10 < 4503599627370496L && e10 >= -22 && e10 <= 38 - digits) {
                val pow10 = BufferConstants.pow10Doubles
                if (e10 < 0) m10 / pow10(-e10)
                else if (e10 <= 22) m10 * pow10(e10)
                else {
                    val slop = 16 - digits
                    (m10 * pow10(slop)) * pow10(e10 - slop)
                }
            } else {
                if (e10 >= 310) Double.PositiveInfinity
                else if (e10 < -343) 0.0
                else m10 * math.pow(10, e10)
            }
        if (isNeg) -x else x
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
                if (e10 < 0) {
                    buffer.writeShortLE(0x2d45) // E-
                    e10 = -e10
                } else {
                    buffer.writeShortLE(0x2b45) // E+
                }
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

    final def readStringAsBigInt(buffer: Buffer): BigInt = {
        val isNeg = buffer.skipIfNextIs('-')
        val sb    = new StringBuilder
        while (buffer.readableBytes > 0 && buffer.nextInRange('0', '9')) {
            sb.append(buffer.readByte.toChar)
        }
        if (sb.isEmpty) throw new NumberFormatException("expected digits for BigInt")
        if (isNeg) BigInt("-" + sb.toString) else BigInt(sb.toString)
    }

    final def readStringAsBigInteger(buffer: Buffer): BigInteger = {
        val isNeg = buffer.skipIfNextIs('-')
        val sb    = new StringBuilder
        while (buffer.readableBytes > 0 && buffer.nextInRange('0', '9')) {
            sb.append(buffer.readByte.toChar)
        }
        if (sb.isEmpty) throw new NumberFormatException("expected digits for BigInteger")
        new BigInteger(if (isNeg) "-" + sb.toString else sb.toString)
    }

    final def readStringAsBigDecimal(buffer: Buffer): BigDecimal = {
        val sb = new StringBuilder
        if (buffer.skipIfNextIs('-')) sb.append('-')
        while (buffer.readableBytes > 0) {
            val b = buffer.getByte(buffer.readerOffset)
            if ((b >= '0' && b <= '9') || b == '.' || b == 'e' || b == 'E' || b == '+' || b == '-')
                sb.append(buffer.readByte.toChar)
            else return BigDecimal(sb.toString)
        }
        BigDecimal(sb.toString)
    }

    final def readStringAsJBigDecimal(buffer: Buffer): java.math.BigDecimal = {
        val sb = new StringBuilder
        if (buffer.skipIfNextIs('-')) sb.append('-')
        while (buffer.readableBytes > 0) {
            val b = buffer.getByte(buffer.readerOffset)
            if ((b >= '0' && b <= '9') || b == '.' || b == 'e' || b == 'E' || b == '+' || b == '-')
                sb.append(buffer.readByte.toChar)
            else return new java.math.BigDecimal(sb.toString)
        }
        new java.math.BigDecimal(sb.toString)
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
            val ss1 = if (ss eq null) BufferConstants.getTenPow18Squares(n) else ss
            val qr  = x.divideAndRemainder(ss1(n))
            writeBigInteger(buffer, qr(0), ss1)
            writeBigIntegerRemainder(buffer, qr(1), n - 1, ss1)
        }
    }

    private def writeBigIntegerRemainder(buffer: Buffer, x: BigInteger, n: Int, ss: Array[BigInteger]): Unit =
        if (n < 0) write18Digits(buffer, Math.abs(x.longValue), BufferConstants.digits)
        else {
            val qr = x.divideAndRemainder(ss(n))
            writeBigIntegerRemainder(buffer, qr(0), n - 1, ss)
            writeBigIntegerRemainder(buffer, qr(1), n - 1, ss)
        }

    private def writeBigDecimal(buffer: Buffer, x: BigInteger, scale: Int, block: Int, ss: Array[BigInteger]): Long = {
        val bitLen = x.bitLength
        if (bitLen < 64) {
            val v   = x.longValue
            val pos = buffer.writerOffset
            writeLongAsString(buffer, v)
            val lastPos = buffer.writerOffset
            val digits  = (v >> 63).toInt + lastPos - pos
            val dotOff  = scale.toLong - block
            val exp     = (digits - 1) - dotOff
            if (scale >= 0 && exp >= -6) {
                if (exp < 0) insertDotWithZeroes(buffer, digits, -1 - exp.toInt, lastPos)
                else if (dotOff > 0) insertDot(buffer, lastPos - dotOff.toInt, lastPos)
                0
            } else {
                if (digits > 1 || block > 0) insertDot(buffer, lastPos - digits + 1, lastPos)
                exp
            }
        } else {
            val n   = calculateTenPow18SquareNumber(bitLen)
            val ss1 = if (ss eq null) BufferConstants.getTenPow18Squares(n) else ss
            val qr  = x.divideAndRemainder(ss1(n))
            val exp = writeBigDecimal(buffer, qr(0), scale, (18 << n) + block, ss1)
            writeBigDecimalRemainder(buffer, qr(1), scale, block, n - 1, ss1)
            exp
        }
    }

    private def writeBigDecimalRemainder(
        buffer: Buffer,
        x: BigInteger,
        scale: Int,
        block: Int,
        n: Int,
        ss: Array[BigInteger]
    ): Unit =
        if (n < 0) {
            val v       = Math.abs(x.longValue)
            val ds      = BufferConstants.digits
            write18Digits(buffer, v, ds)
            val lastPos = buffer.writerOffset
            val digits  = 18
            val dotOff  = scale.toLong - block
            if (dotOff > 0 && dotOff < digits) insertDot(buffer, lastPos - dotOff.toInt, lastPos)
            else if (dotOff >= digits) insertDotWithZeroes(buffer, digits, dotOff.toInt - digits, lastPos)
        } else {
            val qr = x.divideAndRemainder(ss(n))
            writeBigDecimalRemainder(buffer, qr(0), scale, block, n - 1, ss)
            writeBigDecimalRemainder(buffer, qr(1), scale, block + (18 << n), n - 1, ss)
        }

    final def writeBigDecimalAsString(buffer: Buffer, bigDecimal: BigDecimal): Unit = {
        val bd  = bigDecimal.bigDecimal
        val exp = writeBigDecimal(buffer, bd.unscaledValue, bd.scale, 0, null)
        writeExp(buffer, exp)
    }

    final def writeJBigDecimalAsString(buffer: Buffer, bigDecimal: java.math.BigDecimal): Unit = {
        val exp = writeBigDecimal(buffer, bigDecimal.unscaledValue, bigDecimal.scale, 0, null)
        writeExp(buffer, exp)
    }

    private def writeExp(buffer: Buffer, exp: Long): Unit = {
        if (exp != 0) {
            val absExp = Math.abs(exp)
            if (exp > 0) buffer.writeShortLE(0x2B45) // "E+"
            else buffer.writeShortLE(0x2D45)         // "E-"
            if (absExp < 10) buffer.writeByte((absExp.toInt + '0').toByte)
            else write2Digits(buffer, absExp.toInt, BufferConstants.digits)
        }
    }

    private def calculateTenPow18SquareNumber(bitLen: Int): Int = {
        // Math.max((x.bitLength * Math.log(2) / Math.log(1e18)).toInt - 1, 1)
        val m = Math.max((bitLen * 71828554L >> 32).toInt - 1, 1)
        31 - java.lang.Integer.numberOfLeadingZeros(m)
    }

    private def insertDot(buffer: Buffer, dotPos: Int, lastPos: Int): Unit = {
        var pos = lastPos
        while (pos > dotPos) {
            buffer.setByte(pos, buffer.getByte(pos - 1))
            pos -= 1
        }
        buffer.setByte(dotPos, '.')
        buffer.writerOffset(lastPos + 1)
    }

    private def insertDotWithZeroes(buffer: Buffer, digits: Int, pad: Int, lastPos: Int): Unit = {
        var pos    = lastPos + pad + 1
        val numPos = pos - digits
        val off    = pad + 2
        while (pos > numPos) {
            buffer.setByte(pos, buffer.getByte(pos - off))
            pos -= 1
        }
        val dotPos = pos - pad
        while (pos > dotPos) {
            buffer.setByte(pos, '0'.toByte)
            pos -= 1
        }
        buffer.setShortLE(dotPos - 1, 0x2E30.toShort) // ".0"
        buffer.writerOffset(lastPos + off)
    }

    // =========================================================================
    // Fixed-length string-to-number parsing methods
    // =========================================================================

    /** Parses a fixed-length string in the buffer as a signed Long, then advances the readerOffset by `length`.
      * Uses direct byte-level parsing with overflow checking (port of JDK Long.parseLong).
      */
    final def readFixedStringAsLong(buffer: Buffer, length: Int, radix: Int = 10): Long = {
        val value = getFixedStringAsLong(buffer, buffer.readerOffset, length, radix)
        buffer.skipReadableBytes(length)
        value
    }

    /** Parses a fixed-length string at the given index as a signed Long, without advancing the readerOffset.
      * Uses direct byte-level parsing with overflow checking (port of JDK Long.parseLong).
      */
    final def getFixedStringAsLong(buffer: Buffer, index: Int, length: Int, radix: Int = 10): Long = {
        if (radix < Character.MIN_RADIX)
            throw new NumberFormatException(s"radix $radix less than Character.MIN_RADIX")
        if (radix > Character.MAX_RADIX)
            throw new NumberFormatException(s"radix $radix greater than Character.MAX_RADIX")
        if (length <= 0)
            throw new NumberFormatException(s"string length must be positive: length = $length")

        var negative = false
        var i        = 0
        var limit    = -Long.MaxValue

        val firstByte = buffer.getByte(index)
        if (firstByte < '0') {
            if (firstByte == '-') {
                negative = true
                limit = Long.MinValue
            } else if (firstByte != '+')
                throw new NumberFormatException(s"For input string: \"${buffer.getCharSequence(index, length)}\"")
            if (length == 1)
                throw new NumberFormatException(s"For input string: \"${firstByte.toChar}\"")
            i = 1
        }

        val multmin = limit / radix
        var result  = 0L
        while (i < length) {
            val digit = Character.digit(buffer.getByte(index + i), radix)
            i += 1
            if (digit < 0 || result < multmin)
                throw new NumberFormatException(s"For input string: \"${buffer.getCharSequence(index, length)}\"")
            result *= radix
            if (result < limit + digit)
                throw new NumberFormatException(s"For input string: \"${buffer.getCharSequence(index, length)}\"")
            result -= digit
        }

        if (negative) result else -result
    }

    /** Parses a fixed-length string in the buffer as a Double, then advances the readerOffset by `length`.
      * Uses zero-allocation manual mantissa/exponent parsing with three-tier conversion.
      */
    final def readFixedStringAsDouble(buffer: Buffer, length: Int): Double = {
        val value = getFixedStringAsDouble(buffer, buffer.readerOffset, length)
        buffer.skipReadableBytes(length)
        value
    }

    /** Parses a fixed-length string at the given index as a Double, without advancing the readerOffset.
      * Uses zero-allocation manual mantissa/exponent parsing with three-tier conversion.
      */
    final def getFixedStringAsDouble(buffer: Buffer, index: Int, length: Int): Double = {
        if (length <= 0)
            throw new NumberFormatException(s"string length must be positive: length = $length")

        val end = index + length
        var pos = index

        // Parse sign
        var isNeg = false
        var b     = buffer.getByte(pos)
        if (b == '+') { pos += 1; b = buffer.getByte(pos) }
        if (b == '-') { isNeg = true; pos += 1; b = buffer.getByte(pos) }
        if (b < '0' || b > '9')
            throw new NumberFormatException(s"expected digit, but got '${b.toChar}'")

        // Parse integer part as mantissa, tracking overflow as exponent adjustment
        var m10: Long = (b - '0').toLong
        var e10       = 0
        var digits    = 1
        pos += 1
        var intContinue = true
        while (intContinue && pos < end) {
            b = buffer.getByte(pos)
            if (b < '0' || b > '9') intContinue = false
            else {
                pos += 1
                if (m10 < 922337203685477580L) { m10 = m10 * 10 + (b - '0'); digits += 1 }
                else e10 += 1
            }
        }

        // Parse fractional part
        if (pos < end) {
            b = buffer.getByte(pos)
            if (b == '.') {
                pos += 1
                var noFracDigits = true
                var fracContinue = true
                while (fracContinue && pos < end) {
                    b = buffer.getByte(pos)
                    if (b < '0' || b > '9') {
                        if (noFracDigits) throw new NumberFormatException("expected digit after '.'")
                        fracContinue = false
                    } else {
                        pos += 1
                        noFracDigits = false
                        if (m10 < 922337203685477580L) { m10 = m10 * 10 + (b - '0'); digits += 1 }
                        e10 -= 1
                    }
                }
            }
        }

        // Parse exponent
        if (pos < end) {
            b = buffer.getByte(pos)
            if ((b | 0x20) == 'e') {
                pos += 1
                val expSign =
                    if (pos < end && buffer.getByte(pos) == '-') { pos += 1; -1 }
                    else {
                        if (pos < end && buffer.getByte(pos) == '+') pos += 1
                        1
                    }
                if (pos >= end || buffer.getByte(pos) < '0' || buffer.getByte(pos) > '9')
                    throw new NumberFormatException("expected digit in exponent")
                var exp = 0
                var expContinue = true
                while (expContinue && pos < end) {
                    b = buffer.getByte(pos)
                    if (b < '0' || b > '9') expContinue = false
                    else {
                        pos += 1
                        exp = exp * 10 + (b - '0')
                        if (exp > 10000) throw new NumberFormatException("exponent too large")
                    }
                }
                e10 += exp * expSign
            }
        }

        // Three-tier conversion
        val x: Double =
            if (m10 == 0) 0.0
            else if (e10 == 0) m10.toDouble
            else if (m10 < 4503599627370496L && e10 >= -22 && e10 <= 38 - digits) {
                val pow10 = BufferConstants.pow10Doubles
                if (e10 < 0) m10 / pow10(-e10)
                else if (e10 <= 22) m10 * pow10(e10)
                else {
                    val slop = 16 - digits
                    (m10 * pow10(slop)) * pow10(e10 - slop)
                }
            } else {
                if (e10 >= 310) Double.PositiveInfinity
                else if (e10 < -343) 0.0
                else m10 * math.pow(10, e10)
            }
        if (isNeg) -x else x
    }

}
