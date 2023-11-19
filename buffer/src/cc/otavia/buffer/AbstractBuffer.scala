/*
 * Copyright 2022 Yan Kun <yan_kun_1992@foxmail.com>
 *
 * This file fork from netty.
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

import cc.otavia.buffer.BytesUtil.{bytes8Long, ignoreCaseEqual}

import java.lang.{Byte as JByte, Double as JDouble, Float as JFloat, Long as JLong, Short as JShort}
import java.nio.channels.{FileChannel, ReadableByteChannel, WritableByteChannel}
import java.nio.charset.{Charset, StandardCharsets}
import java.nio.{ByteBuffer, ByteOrder}
import scala.language.unsafeNulls

abstract class AbstractBuffer(val underlying: ByteBuffer) extends Buffer {

    private var ridx: Int = 0
    private var widx: Int = 0

    underlying.limit(underlying.capacity())
    underlying.order(ByteOrder.BIG_ENDIAN)

    override def toString: String = s"Buffer[ridx:$ridx, widx:$widx, cap:${capacity}]"

    override def readerOffset: Int = ridx

    override def readerOffset(offset: Int): Buffer = {
        checkRead(offset, 0)
        ridx = offset
        this
    }

    override def writerOffset: Int = widx

    override def writerOffset(offset: Int): Buffer = {
        checkWrite(offset, 0)
        widx = offset
        this
    }

    override def compact(): Buffer = {
        if (readableBytes == 0) {
            ridx = 0
            widx = 0
        } else if (ridx == 0) {} else {
            underlying.put(0, underlying, ridx, widx - ridx)
            widx -= ridx
            ridx = 0
        }
        this
    }

    override def writeCharSequence(source: CharSequence, charset: Charset): Buffer = {
        val array = source.toString.getBytes(charset)
        underlying.put(widx, array)
        widx += array.length
        this
    }

    override def readCharSequence(length: Int, charset: Charset): CharSequence = {
        val array = new Array[Byte](length)
        underlying.get(ridx, array, 0, length)
        ridx += length
        new String(array, 0, length, charset)
    }

    override def writeBytes(source: Buffer, length: Int): Buffer = {
        underlying.position(widx)
        source.readBytes(underlying, length)
        widx = underlying.position()
        underlying.clear()
        this
    }

    override def writeBytes(source: Array[Byte], srcPos: Int, length: Int): Buffer = {
        underlying.put(widx, source, srcPos, length)
        widx += length
        this
    }

    override def writeBytes(length: Int, value: Byte): Buffer = {
        if (writableBytes < length)
            throw new IndexOutOfBoundsException(s"except length ${length}, but only $writableBytes")
        var i = 0
        while (i < length) {
            underlying.put(widx + i, value)
            i += 1
        }
        widx += length
        this
    }

    override def setBytes(index: Int, source: Array[Byte], srcPos: Int, length: Int): Buffer = {
        if (closed) throw new BufferClosedException()
        if (srcPos + length > source.length)
            throw new IndexOutOfBoundsException(
              s"srcPos + length is underflow of the length of source: srcPos + length = ${srcPos + length}, source.length = ${source.length}"
            )
        if (capacity < length + index)
            throw new IndexOutOfBoundsException(
              s"length + index is large than the capacity of this buffer: length + index = ${length + index}, capacity = $capacity"
            )

        underlying.put(index, source, srcPos, length)

        this
    }

    override def writeBytes(source: ByteBuffer, length: Int): Buffer = {
        val len = math.min(length, source.remaining())
        checkWrite(widx, len)
        underlying.put(widx, source, source.position(), len)
        widx += len
        source.position(source.position() + len)
        this
    }

    override def readBytes(destination: ByteBuffer, length: Int): Buffer = {
        val len    = math.min(math.min(readableBytes, length), destination.remaining())
        val desPos = destination.position()
        destination.put(desPos, underlying, ridx, len)
        destination.position(desPos + len)
        ridx += len
        this
    }

    override def readBytes(destination: Array[Byte], destPos: Int, length: Int): Buffer = {
        underlying.get(ridx, destination, destPos, length)
        ridx += length
        this
    }

    override def readBytes(destination: Buffer, length: Int): Buffer = {
        underlying.position(ridx)
        destination.writeBytes(underlying, length)
        ridx = underlying.position()
        underlying.clear()
        this
    }

    override def bytesBefore(needle: Byte): Int = bytesBefore1(needle, ridx, widx)

    private def bytesBefore1(a: Byte, from: Int, to: Int): Int = {
        var offset: Int       = from
        var continue: Boolean = true
        while (continue && offset < to) {
            if (underlying.get(offset) == a) continue = false else offset += 1
        }
        if (continue) -1 else offset - from
    }

    private def bytesBefore1ignoreCase(a: Byte, from: Int, to: Int): Int = {
        var offset: Int       = from
        var continue: Boolean = true
        while (continue && offset < to) {
            if (ignoreCaseEqual(underlying.get(offset), a)) continue = false else offset += 1
        }
        if (continue) -1 else offset - from
    }

    override def bytesBeforeIn(set: Array[Byte]): Int = {
        var offset: Int       = ridx
        var continue: Boolean = true
        while (continue && offset < widx) {
            var i     = 0
            var notin = true
            val b     = underlying.get(offset)
            while (i < set.length && notin) {
                if (b != set(i)) i += 1 else notin = false
            }
            if (notin) offset += 1 else continue = false
        }
        if (continue) -1 else offset - ridx
    }

    override def bytesBeforeInRange(lower: Byte, upper: Byte): Int = {
        var offset: Int       = ridx
        var continue: Boolean = true
        while (continue && offset < widx) {
            val b = underlying.get(offset)
            if (b >= lower && b <= upper) continue = false else offset += 1
        }
        if (continue) -1 else offset - ridx
    }

    override def bytesBefore(needle1: Byte, needle2: Byte): Int = bytesBefore2(needle1, needle2, ridx, widx)

    private def bytesBefore2(a1: Byte, a2: Byte, from: Int, to: Int): Int = if (readableBytes >= 2) {
        var offset: Int       = from
        var continue: Boolean = true
        var b1: Byte          = 0
        var b2: Byte          = underlying.get(offset)
        while (continue && offset < to - 1) {
            b1 = b2
            b2 = underlying.get(offset + 1)
            if (b1 == a1 && b2 == a2) continue = false else offset += 1
        }
        if (continue) -1 else offset - from
    } else -1

    private def bytesBefore2ignoreCase(a1: Byte, a2: Byte, from: Int, to: Int): Int = if (readableBytes >= 2) {
        var offset: Int       = from
        var continue: Boolean = true
        var b1: Byte          = 0
        var b2: Byte          = underlying.get(offset)
        while (continue && offset < to - 1) {
            b1 = b2
            b2 = underlying.get(offset + 1)
            if (ignoreCaseEqual(b1, a1) && ignoreCaseEqual(b2, a2)) continue = false else offset += 1
        }
        if (continue) -1 else offset - from
    } else -1

    override def bytesBefore(needle1: Byte, needle2: Byte, needle3: Byte): Int =
        bytesBefore3(needle1, needle2, needle3, ridx, widx)

    private def bytesBefore3(a1: Byte, a2: Byte, a3: Byte, from: Int, to: Int): Int = if (readableBytes >= 3) {
        var offset: Int       = from
        var continue: Boolean = true
        var b1: Byte          = 0
        var b2: Byte          = underlying.get(offset)
        var b3: Byte          = underlying.get(offset + 1)
        while (continue && offset < to - 2) {
            b1 = b2; b2 = b3
            b3 = underlying.get(offset + 2)
            if (b1 == a1 && b2 == a2 && b3 == a3) continue = false else offset += 1
        }
        if (continue) -1 else offset - from
    } else -1

    private def bytesBefore3ignoreCase(a1: Byte, a2: Byte, a3: Byte, from: Int, to: Int): Int =
        if (readableBytes >= 3) {
            var offset: Int       = from
            var continue: Boolean = true
            var b1: Byte          = 0
            var b2: Byte          = underlying.get(offset)
            var b3: Byte          = underlying.get(offset + 1)
            while (continue && offset < to - 2) {
                b1 = b2; b2 = b3
                b3 = underlying.get(offset + 2)
                if (ignoreCaseEqual(b1, a1) && ignoreCaseEqual(b2, a2) && ignoreCaseEqual(b3, a3)) continue = false
                else offset += 1
            }
            if (continue) -1 else offset - from
        } else -1

    override def bytesBefore(needle1: Byte, needle2: Byte, needle3: Byte, needle4: Byte): Int =
        bytesBefore4(needle1, needle2, needle3, needle4, ridx, widx)

    private def bytesBefore4(a1: Byte, a2: Byte, a3: Byte, a4: Byte, from: Int, to: Int): Int =
        if (readableBytes >= 4) {
            var offset: Int       = from
            var continue: Boolean = true
            var b1: Byte          = 0
            var b2: Byte          = underlying.get(offset)
            var b3: Byte          = underlying.get(offset + 1)
            var b4: Byte          = underlying.get(offset + 2)
            while (continue && offset < to - 3) {
                b1 = b2; b2 = b3; b3 = b4
                b4 = underlying.get(offset + 3)
                if (b1 == a1 && b2 == a2 && b3 == a3 && b4 == a4) {
                    continue = false
                } else offset += 1
            }
            if (continue) -1 else offset - from
        } else -1

    private def bytesBefore4ignoreCase(a1: Byte, a2: Byte, a3: Byte, a4: Byte, from: Int, to: Int): Int =
        if (readableBytes >= 4) {
            var offset: Int       = from
            var continue: Boolean = true
            var b1: Byte          = 0
            var b2: Byte          = underlying.get(offset)
            var b3: Byte          = underlying.get(offset + 1)
            var b4: Byte          = underlying.get(offset + 2)
            while (continue && offset < to - 3) {
                b1 = b2; b2 = b3; b3 = b4
                b4 = underlying.get(offset + 3)
                if (
                  ignoreCaseEqual(b1, a1) && ignoreCaseEqual(b2, a2) && ignoreCaseEqual(b3, a3) &&
                  ignoreCaseEqual(b4, a4)
                ) {
                    continue = false
                } else offset += 1
            }
            if (continue) -1 else offset - from
        } else -1

    private def bytesBefore5(b1: Byte, b2: Byte, b3: Byte, b4: Byte, b5: Byte, from: Int, to: Int): Int =
        if (readableBytes >= 5) {
            var offset: Int       = from
            var continue: Boolean = true
            var a1: Byte          = 0
            var a2: Byte          = underlying.get(offset)
            var a3: Byte          = underlying.get(offset + 1)
            var a4: Byte          = underlying.get(offset + 2)
            var a5: Byte          = underlying.get(offset + 3)
            while (continue && offset < to - 4) {
                a1 = a2; a2 = a3; a3 = a4; a4 = a5
                a5 = underlying.get(offset + 4)
                if (a1 == b1 && a2 == b2 && a3 == b3 && a4 == b4 && a5 == b5) {
                    continue = false
                } else offset += 1
            }
            if (continue) -1 else offset - from
        } else -1

    private def bytesBefore5ignoreCase(b1: Byte, b2: Byte, b3: Byte, b4: Byte, b5: Byte, from: Int, to: Int): Int =
        if (readableBytes >= 5) {
            var offset: Int       = from
            var continue: Boolean = true
            var a1: Byte          = 0
            var a2: Byte          = underlying.get(offset)
            var a3: Byte          = underlying.get(offset + 1)
            var a4: Byte          = underlying.get(offset + 2)
            var a5: Byte          = underlying.get(offset + 3)
            while (continue && offset < to - 4) {
                a1 = a2; a2 = a3; a3 = a4; a4 = a5
                a5 = underlying.get(offset + 4)
                if (
                  ignoreCaseEqual(a1, b1) && ignoreCaseEqual(a2, b2) && ignoreCaseEqual(a3, b3) &&
                  ignoreCaseEqual(a4, b4) && ignoreCaseEqual(a5, b5)
                ) {
                    continue = false
                } else offset += 1
            }
            if (continue) -1 else offset - from
        } else -1

    private def bytesBefore6(b1: Byte, b2: Byte, b3: Byte, b4: Byte, b5: Byte, b6: Byte, from: Int, to: Int): Int =
        if (readableBytes >= 6) {
            var offset: Int       = from
            var continue: Boolean = true
            var a1: Byte          = 0
            var a2: Byte          = underlying.get(offset)
            var a3: Byte          = underlying.get(offset + 1)
            var a4: Byte          = underlying.get(offset + 2)
            var a5: Byte          = underlying.get(offset + 3)
            var a6: Byte          = underlying.get(offset + 4)
            while (continue && offset < to - 5) {
                a1 = a2; a2 = a3; a3 = a4; a4 = a5; a5 = a6
                a6 = underlying.get(offset + 5)
                if (a1 == b1 && a2 == b2 && a3 == b3 && a4 == b4 && a5 == b5 && a6 == b6) {
                    continue = false
                } else offset += 1
            }
            if (continue) -1 else offset - from
        } else -1

    // format: off
    private def bytesBefore6ignoreCase(b1: Byte, b2: Byte, b3: Byte, b4: Byte, b5: Byte, b6: Byte,
                                       from: Int, to: Int): Int = // format: on
        if (readableBytes >= 6) {
            var offset: Int       = from
            var continue: Boolean = true
            var a1: Byte          = 0
            var a2: Byte          = underlying.get(offset)
            var a3: Byte          = underlying.get(offset + 1)
            var a4: Byte          = underlying.get(offset + 2)
            var a5: Byte          = underlying.get(offset + 3)
            var a6: Byte          = underlying.get(offset + 4)
            while (continue && offset < to - 5) {
                a1 = a2; a2 = a3; a3 = a4; a4 = a5; a5 = a6
                a6 = underlying.get(offset + 5)
                if (
                  ignoreCaseEqual(a1, b1) && ignoreCaseEqual(a2, b2) && ignoreCaseEqual(a3, b3) &&
                  ignoreCaseEqual(a4, b4) && ignoreCaseEqual(a5, b5) && ignoreCaseEqual(a6, b6)
                ) {
                    continue = false
                } else offset += 1
            }
            if (continue) -1 else offset - from
        } else -1

    // format: off
    private def bytesBefore7(b1: Byte, b2: Byte, b3: Byte, b4: Byte, b5: Byte, b6: Byte, b7: Byte,
                             from: Int, to: Int): Int = // format: on
        if (readableBytes >= 7) {
            var offset: Int       = from
            var continue: Boolean = true
            var a1: Byte          = 0
            var a2: Byte          = underlying.get(offset)
            var a3: Byte          = underlying.get(offset + 1)
            var a4: Byte          = underlying.get(offset + 2)
            var a5: Byte          = underlying.get(offset + 3)
            var a6: Byte          = underlying.get(offset + 4)
            var a7: Byte          = underlying.get(offset + 5)
            while (continue && offset < to - 6) {
                a1 = a2; a2 = a3; a3 = a4; a4 = a5; a5 = a6; a6 = a7
                a7 = underlying.get(offset + 6)
                if (a1 == b1 && a2 == b2 && a3 == b3 && a4 == b4 && a5 == b5 && a6 == b6 && a7 == b7) {
                    continue = false
                } else offset += 1
            }
            if (continue) -1 else offset - from
        } else -1

    // format: off
    private def bytesBefore7ignoreCase(b1: Byte, b2: Byte, b3: Byte, b4: Byte, b5: Byte, b6: Byte, b7: Byte,
                                       from: Int, to: Int): Int =
    // format: on
        if (readableBytes >= 7) {
            var offset: Int       = from
            var continue: Boolean = true
            var a1: Byte          = 0
            var a2: Byte          = underlying.get(offset)
            var a3: Byte          = underlying.get(offset + 1)
            var a4: Byte          = underlying.get(offset + 2)
            var a5: Byte          = underlying.get(offset + 3)
            var a6: Byte          = underlying.get(offset + 4)
            var a7: Byte          = underlying.get(offset + 5)
            while (continue && offset < to - 6) {
                a1 = a2; a2 = a3; a3 = a4; a4 = a5; a5 = a6; a6 = a7
                a7 = underlying.get(offset + 6)
                if (
                  ignoreCaseEqual(a1, b1) && ignoreCaseEqual(a2, b2) && ignoreCaseEqual(a3, b3) &&
                  ignoreCaseEqual(a4, b4) && ignoreCaseEqual(a5, b5) && ignoreCaseEqual(a6, b6) &&
                  ignoreCaseEqual(a7, b7)
                ) {
                    continue = false
                } else offset += 1
            }
            if (continue) -1 else offset - from
        } else -1

    // format: off
    private def bytesBefore8(b1: Byte, b2: Byte, b3: Byte, b4: Byte, b5: Byte, b6: Byte, b7: Byte, b8: Byte,
                             from: Int, to: Int): Int = // format: on
        if (readableBytes >= 8) {
            var offset: Int       = from
            var continue: Boolean = true
            var a1: Byte          = 0
            var a2: Byte          = underlying.get(offset)
            var a3: Byte          = underlying.get(offset + 1)
            var a4: Byte          = underlying.get(offset + 2)
            var a5: Byte          = underlying.get(offset + 3)
            var a6: Byte          = underlying.get(offset + 4)
            var a7: Byte          = underlying.get(offset + 5)
            var a8: Byte          = underlying.get(offset + 6)
            while (continue && offset < to - 7) {
                a1 = a2; a2 = a3; a3 = a4; a4 = a5; a5 = a6; a6 = a7; a7 = a8
                a8 = underlying.get(offset + 7)
                if (a1 == b1 && a2 == b2 && a3 == b3 && a4 == b4 && a5 == b5 && a6 == b6 && a7 == b7 && a8 == b8) {
                    continue = false
                } else offset += 1
            }
            if (continue) -1 else offset - from
        } else -1

    // format: off
    private def bytesBefore8ignoreCase(b1: Byte, b2: Byte, b3: Byte, b4: Byte, b5: Byte, b6: Byte, b7: Byte, b8: Byte,
                             from: Int, to: Int): Int = // format: on
        if (readableBytes >= 8) {
            var offset: Int       = from
            var continue: Boolean = true
            var a1: Byte          = 0
            var a2: Byte          = underlying.get(offset)
            var a3: Byte          = underlying.get(offset + 1)
            var a4: Byte          = underlying.get(offset + 2)
            var a5: Byte          = underlying.get(offset + 3)
            var a6: Byte          = underlying.get(offset + 4)
            var a7: Byte          = underlying.get(offset + 5)
            var a8: Byte          = underlying.get(offset + 6)
            while (continue && offset < to - 7) {
                a1 = a2; a2 = a3; a3 = a4; a4 = a5; a5 = a6; a6 = a7; a7 = a8
                a8 = underlying.get(offset + 7)
                if (
                  ignoreCaseEqual(a1, b1) && ignoreCaseEqual(a2, b2) && ignoreCaseEqual(a3, b3) &&
                  ignoreCaseEqual(a4, b4) && ignoreCaseEqual(a5, b5) && ignoreCaseEqual(a6, b6) &&
                  ignoreCaseEqual(a7, b7) && ignoreCaseEqual(a8, b8)
                ) {
                    continue = false
                } else offset += 1
            }
            if (continue) -1 else offset - from
        } else -1

    // format: off
    private def bytesBefore9(b1: Byte, b2: Byte, b3: Byte, b4: Byte, b5: Byte, b6: Byte, b7: Byte, b8: Byte, b9: Byte,
                             from: Int, to: Int): Int = if (readableBytes >= 9) { // format: on
        var offset: Int       = from
        var continue: Boolean = true
        var a1: Byte          = 0
        var a2: Byte          = underlying.get(offset)
        var a3: Byte          = underlying.get(offset + 1)
        var a4: Byte          = underlying.get(offset + 2)
        var a5: Byte          = underlying.get(offset + 3)
        var a6: Byte          = underlying.get(offset + 4)
        var a7: Byte          = underlying.get(offset + 5)
        var a8: Byte          = underlying.get(offset + 6)
        var a9: Byte          = underlying.get(offset + 7)
        while (continue && offset < to - 8) {
            a1 = a2; a2 = a3; a3 = a4; a4 = a5; a5 = a6; a6 = a7; a7 = a8; a8 = a9
            a9 = underlying.get(offset + 8)
            if (
              a1 == b1 && a2 == b2 && a3 == b3 && a4 == b4 && a5 == b5 && a6 == b6 && a7 == b7 && a8 == b8 &&
              a9 == b9
            ) {
                continue = false
            } else offset += 1
        }
        if (continue) -1 else offset - from
    } else -1

    // format: off
    private def bytesBefore9ignoreCase(b1: Byte, b2: Byte, b3: Byte, b4: Byte, b5: Byte, b6: Byte, b7: Byte, b8: Byte,
                                       b9: Byte, from: Int, to: Int): Int = if (readableBytes >= 9) { // format: on
        var offset: Int       = from
        var continue: Boolean = true
        var a1: Byte          = 0
        var a2: Byte          = underlying.get(offset)
        var a3: Byte          = underlying.get(offset + 1)
        var a4: Byte          = underlying.get(offset + 2)
        var a5: Byte          = underlying.get(offset + 3)
        var a6: Byte          = underlying.get(offset + 4)
        var a7: Byte          = underlying.get(offset + 5)
        var a8: Byte          = underlying.get(offset + 6)
        var a9: Byte          = underlying.get(offset + 7)
        while (continue && offset < to - 8) {
            a1 = a2; a2 = a3; a3 = a4; a4 = a5; a5 = a6; a6 = a7; a7 = a8; a8 = a9
            a9 = underlying.get(offset + 8)
            if (
              ignoreCaseEqual(a1, b1) && ignoreCaseEqual(a2, b2) && ignoreCaseEqual(a3, b3) &&
              ignoreCaseEqual(a4, b4) && ignoreCaseEqual(a5, b5) && ignoreCaseEqual(a6, b6) &&
              ignoreCaseEqual(a7, b7) && ignoreCaseEqual(a8, b8) && ignoreCaseEqual(a9, b9)
            ) {
                continue = false
            } else offset += 1
        }
        if (continue) -1 else offset - from
    } else -1

    // format: off
    private def bytesBefore10(b1: Byte, b2: Byte, b3: Byte, b4: Byte, b5: Byte, b6: Byte, b7: Byte, b8: Byte,
                               b9: Byte, b10: Byte, from: Int, to: Int): Int = if (readableBytes >= 10) {
        // format: on
        var offset: Int       = from
        var continue: Boolean = true
        var a1: Byte          = 0
        var a2: Byte          = underlying.get(offset)
        var a3: Byte          = underlying.get(offset + 1)
        var a4: Byte          = underlying.get(offset + 2)
        var a5: Byte          = underlying.get(offset + 3)
        var a6: Byte          = underlying.get(offset + 4)
        var a7: Byte          = underlying.get(offset + 5)
        var a8: Byte          = underlying.get(offset + 6)
        var a9: Byte          = underlying.get(offset + 7)
        var a10: Byte         = underlying.get(offset + 8)
        while (continue && offset < to - 9) {
            a1 = a2; a2 = a3; a3 = a4; a4 = a5; a5 = a6; a6 = a7; a7 = a8; a8 = a9; a9 = a10
            a10 = underlying.get(offset + 9)
            if (
              a1 == b1 && a2 == b2 && a3 == b3 && a4 == b4 && a5 == b5 && a6 == b6 && a7 == b7 &&
              a8 == b8 && a9 == b9 && a10 == b10
            ) {
                continue = false
            } else offset += 1
        }
        if (continue) -1 else offset - from
    } else -1

    // format: off
    private def bytesBefore10ignoreCase(b1: Byte, b2: Byte, b3: Byte, b4: Byte, b5: Byte, b6: Byte, b7: Byte, b8: Byte,
                              b9: Byte, b10: Byte, from: Int, to: Int): Int = if (readableBytes >= 10) {
        // format: on
        var offset: Int       = from
        var continue: Boolean = true
        var a1: Byte          = 0
        var a2: Byte          = underlying.get(offset)
        var a3: Byte          = underlying.get(offset + 1)
        var a4: Byte          = underlying.get(offset + 2)
        var a5: Byte          = underlying.get(offset + 3)
        var a6: Byte          = underlying.get(offset + 4)
        var a7: Byte          = underlying.get(offset + 5)
        var a8: Byte          = underlying.get(offset + 6)
        var a9: Byte          = underlying.get(offset + 7)
        var a10: Byte         = underlying.get(offset + 8)
        while (continue && offset < to - 9) {
            a1 = a2; a2 = a3; a3 = a4; a4 = a5; a5 = a6; a6 = a7; a7 = a8; a8 = a9; a9 = a10
            a10 = underlying.get(offset + 9)
            if (
              ignoreCaseEqual(a1, b1) && ignoreCaseEqual(a2, b2) && ignoreCaseEqual(a3, b3) &&
              ignoreCaseEqual(a4, b4) && ignoreCaseEqual(a5, b5) && ignoreCaseEqual(a6, b6) &&
              ignoreCaseEqual(a7, b7) && ignoreCaseEqual(a8, b8) && ignoreCaseEqual(a9, b9) &&
              ignoreCaseEqual(a10, b10)
            ) {
                continue = false
            } else offset += 1
        }
        if (continue) -1 else offset - from
    } else -1

    // format: off
    private def bytesBefore11(b1: Byte, b2: Byte, b3: Byte, b4: Byte, b5: Byte, b6: Byte, b7: Byte, b8: Byte,
                               b9: Byte, b10: Byte, b11: Byte, from: Int, to: Int): Int = if (readableBytes >= 11) {
        // format: on
        var offset: Int       = from
        var continue: Boolean = true
        var a1: Byte          = 0
        var a2: Byte          = underlying.get(offset)
        var a3: Byte          = underlying.get(offset + 1)
        var a4: Byte          = underlying.get(offset + 2)
        var a5: Byte          = underlying.get(offset + 3)
        var a6: Byte          = underlying.get(offset + 4)
        var a7: Byte          = underlying.get(offset + 5)
        var a8: Byte          = underlying.get(offset + 6)
        var a9: Byte          = underlying.get(offset + 7)
        var a10: Byte         = underlying.get(offset + 8)
        var a11: Byte         = underlying.get(offset + 9)
        while (continue && offset < to - 10) {
            a1 = a2; a2 = a3; a3 = a4; a4 = a5; a5 = a6; a6 = a7; a7 = a8; a8 = a9; a9 = a10; a10 = a11
            a11 = underlying.get(offset + 10)
            if (
              a1 == b1 && a2 == b2 && a3 == b3 && a4 == b4 && a5 == b5 && a6 == b6 && a7 == b7 && a8 == b8 &&
              a9 == b9 && a10 == b10 && a11 == b11
            ) {
                continue = false
            } else offset += 1
        }
        if (continue) -1 else offset - from
    } else -1

    // format: off
    private def bytesBefore11ignoreCase(b1: Byte, b2: Byte, b3: Byte, b4: Byte, b5: Byte, b6: Byte, b7: Byte, b8: Byte,
                              b9: Byte, b10: Byte, b11: Byte, from: Int, to: Int): Int = if (readableBytes >= 11) {
        // format: on
        var offset: Int       = from
        var continue: Boolean = true
        var a1: Byte          = 0
        var a2: Byte          = underlying.get(offset)
        var a3: Byte          = underlying.get(offset + 1)
        var a4: Byte          = underlying.get(offset + 2)
        var a5: Byte          = underlying.get(offset + 3)
        var a6: Byte          = underlying.get(offset + 4)
        var a7: Byte          = underlying.get(offset + 5)
        var a8: Byte          = underlying.get(offset + 6)
        var a9: Byte          = underlying.get(offset + 7)
        var a10: Byte         = underlying.get(offset + 8)
        var a11: Byte         = underlying.get(offset + 9)
        while (continue && offset < to - 10) {
            a1 = a2; a2 = a3; a3 = a4; a4 = a5; a5 = a6; a6 = a7; a7 = a8; a8 = a9; a9 = a10; a10 = a11
            a11 = underlying.get(offset + 10)
            if (
              ignoreCaseEqual(a1, b1) && ignoreCaseEqual(a2, b2) && ignoreCaseEqual(a3, b3) &&
              ignoreCaseEqual(a4, b4) && ignoreCaseEqual(a5, b5) && ignoreCaseEqual(a6, b6) &&
              ignoreCaseEqual(a7, b7) && ignoreCaseEqual(a8, b8) && ignoreCaseEqual(a9, b9) &&
              ignoreCaseEqual(a10, b10) && ignoreCaseEqual(a11, b11)
            ) {
                continue = false
            } else offset += 1
        }
        if (continue) -1 else offset - from
    } else -1

    // format: off
    private def bytesBefore12(b1: Byte, b2: Byte, b3: Byte, b4: Byte, b5: Byte, b6: Byte, b7: Byte, b8: Byte,
                               b9: Byte, b10: Byte, b11: Byte, b12: Byte, from: Int, to: Int): Int = // format: on
        if (readableBytes >= 12) {
            var offset: Int       = from
            var continue: Boolean = true
            var a1: Byte          = 0
            var a2: Byte          = underlying.get(offset)
            var a3: Byte          = underlying.get(offset + 1)
            var a4: Byte          = underlying.get(offset + 2)
            var a5: Byte          = underlying.get(offset + 3)
            var a6: Byte          = underlying.get(offset + 4)
            var a7: Byte          = underlying.get(offset + 5)
            var a8: Byte          = underlying.get(offset + 6)
            var a9: Byte          = underlying.get(offset + 7)
            var a10: Byte         = underlying.get(offset + 8)
            var a11: Byte         = underlying.get(offset + 9)
            var a12: Byte         = underlying.get(offset + 10)
            while (continue && offset < to - 11) {
                a1 = a2; a2 = a3; a3 = a4; a4 = a5; a5 = a6; a6 = a7; a7 = a8; a8 = a9; a9 = a10; a10 = a11; a11 = a12
                a12 = underlying.get(offset + 11)
                if (
                  a1 == b1 && a2 == b2 && a3 == b3 && a4 == b4 && a5 == b5 && a6 == b6 && a7 == b7 && a8 == b8 &&
                  a9 == b9 && a10 == b10 && a11 == b11 && a12 == b12
                ) {
                    continue = false
                } else offset += 1
            }
            if (continue) -1 else offset - from
        } else -1

    // format: off
    private def bytesBefore12ignoreCase(b1: Byte, b2: Byte, b3: Byte, b4: Byte, b5: Byte, b6: Byte, b7: Byte, b8: Byte,
                              b9: Byte, b10: Byte, b11: Byte, b12: Byte, from: Int, to: Int): Int = // format: on
        if (readableBytes >= 12) {
            var offset: Int       = from
            var continue: Boolean = true
            var a1: Byte          = 0
            var a2: Byte          = underlying.get(offset)
            var a3: Byte          = underlying.get(offset + 1)
            var a4: Byte          = underlying.get(offset + 2)
            var a5: Byte          = underlying.get(offset + 3)
            var a6: Byte          = underlying.get(offset + 4)
            var a7: Byte          = underlying.get(offset + 5)
            var a8: Byte          = underlying.get(offset + 6)
            var a9: Byte          = underlying.get(offset + 7)
            var a10: Byte         = underlying.get(offset + 8)
            var a11: Byte         = underlying.get(offset + 9)
            var a12: Byte         = underlying.get(offset + 10)
            while (continue && offset < to - 11) {
                a1 = a2; a2 = a3; a3 = a4; a4 = a5; a5 = a6; a6 = a7; a7 = a8; a8 = a9; a9 = a10; a10 = a11; a11 = a12
                a12 = underlying.get(offset + 11)
                if (
                  ignoreCaseEqual(a1, b1) && ignoreCaseEqual(a2, b2) && ignoreCaseEqual(a3, b3) &&
                  ignoreCaseEqual(a4, b4) && ignoreCaseEqual(a5, b5) && ignoreCaseEqual(a6, b6) &&
                  ignoreCaseEqual(a7, b7) && ignoreCaseEqual(a8, b8) && ignoreCaseEqual(a9, b9) &&
                  ignoreCaseEqual(a10, b10) && ignoreCaseEqual(a11, b11) && ignoreCaseEqual(a12, b12)
                ) {
                    continue = false
                } else offset += 1
            }
            if (continue) -1 else offset - from
        } else -1

    // format: off
    private def bytesBefore13(b1: Byte, b2: Byte, b3: Byte, b4: Byte, b5: Byte, b6: Byte, b7: Byte, b8: Byte,
                               b9: Byte, b10: Byte, b11: Byte, b12: Byte, b13: Byte, from: Int, to: Int): Int =
    // format: on
        if (readableBytes >= 13) {
            var offset: Int       = from
            var continue: Boolean = true
            var a1: Byte          = 0
            var a2: Byte          = underlying.get(offset)
            var a3: Byte          = underlying.get(offset + 1)
            var a4: Byte          = underlying.get(offset + 2)
            var a5: Byte          = underlying.get(offset + 3)
            var a6: Byte          = underlying.get(offset + 4)
            var a7: Byte          = underlying.get(offset + 5)
            var a8: Byte          = underlying.get(offset + 6)
            var a9: Byte          = underlying.get(offset + 7)
            var a10: Byte         = underlying.get(offset + 8)
            var a11: Byte         = underlying.get(offset + 9)
            var a12: Byte         = underlying.get(offset + 10)
            var a13: Byte         = underlying.get(offset + 11)
            while (continue && offset < to - 12) {
                a1 = a2; a2 = a3; a3 = a4; a4 = a5; a5 = a6; a6 = a7; a7 = a8; a8 = a9; a9 = a10; a10 = a11
                a11 = a12; a12 = a13
                a13 = underlying.get(offset + 12)
                if (
                  a1 == b1 && a2 == b2 && a3 == b3 && a4 == b4 && a5 == b5 && a6 == b6 && a7 == b7 && a8 == b8 &&
                  a9 == b9 && a10 == b10 && a11 == b11 && a12 == b12 && a13 == b13
                ) {
                    continue = false
                } else offset += 1
            }
            if (continue) -1 else offset - from
        } else -1

    // format: off
    private def bytesBefore13ignoreCase(b1: Byte, b2: Byte, b3: Byte, b4: Byte, b5: Byte, b6: Byte, b7: Byte, b8: Byte,
                              b9: Byte, b10: Byte, b11: Byte, b12: Byte, b13: Byte, from: Int, to: Int): Int =
    // format: on
        if (readableBytes >= 13) {
            var offset: Int       = from
            var continue: Boolean = true
            var a1: Byte          = 0
            var a2: Byte          = underlying.get(offset)
            var a3: Byte          = underlying.get(offset + 1)
            var a4: Byte          = underlying.get(offset + 2)
            var a5: Byte          = underlying.get(offset + 3)
            var a6: Byte          = underlying.get(offset + 4)
            var a7: Byte          = underlying.get(offset + 5)
            var a8: Byte          = underlying.get(offset + 6)
            var a9: Byte          = underlying.get(offset + 7)
            var a10: Byte         = underlying.get(offset + 8)
            var a11: Byte         = underlying.get(offset + 9)
            var a12: Byte         = underlying.get(offset + 10)
            var a13: Byte         = underlying.get(offset + 11)
            while (continue && offset < to - 12) {
                a1 = a2; a2 = a3; a3 = a4; a4 = a5; a5 = a6; a6 = a7; a7 = a8; a8 = a9; a9 = a10; a10 = a11
                a11 = a12; a12 = a13
                a13 = underlying.get(offset + 12)
                if (
                  ignoreCaseEqual(a1, b1) && ignoreCaseEqual(a2, b2) && ignoreCaseEqual(a3, b3) &&
                  ignoreCaseEqual(a4, b4) && ignoreCaseEqual(a5, b5) && ignoreCaseEqual(a6, b6) &&
                  ignoreCaseEqual(a7, b7) && ignoreCaseEqual(a8, b8) && ignoreCaseEqual(a9, b9) &&
                  ignoreCaseEqual(a10, b10) && ignoreCaseEqual(a11, b11) && ignoreCaseEqual(a12, b12) &&
                  ignoreCaseEqual(a13, b13)
                ) {
                    continue = false
                } else offset += 1
            }
            if (continue) -1 else offset - from
        } else -1
        
    // format: off
    private def bytesBefore14(b1: Byte, b2: Byte, b3: Byte, b4: Byte, b5: Byte, b6: Byte, b7: Byte, b8: Byte,
                               b9: Byte, b10: Byte, b11: Byte, b12: Byte, b13: Byte, b14: Byte,
                              from: Int, to: Int): Int =
    // format: on
        if (readableBytes >= 14) {
            var offset: Int       = from
            var continue: Boolean = true
            var a1: Byte          = 0
            var a2: Byte          = underlying.get(offset)
            var a3: Byte          = underlying.get(offset + 1)
            var a4: Byte          = underlying.get(offset + 2)
            var a5: Byte          = underlying.get(offset + 3)
            var a6: Byte          = underlying.get(offset + 4)
            var a7: Byte          = underlying.get(offset + 5)
            var a8: Byte          = underlying.get(offset + 6)
            var a9: Byte          = underlying.get(offset + 7)
            var a10: Byte         = underlying.get(offset + 8)
            var a11: Byte         = underlying.get(offset + 9)
            var a12: Byte         = underlying.get(offset + 10)
            var a13: Byte         = underlying.get(offset + 11)
            var a14: Byte         = underlying.get(offset + 12)
            while (continue && offset < to - 13) {
                a1 = a2; a2 = a3; a3 = a4; a4 = a5; a5 = a6; a6 = a7; a7 = a8; a8 = a9; a9 = a10; a10 = a11
                a11 = a12; a12 = a13; a13 = a14
                a14 = underlying.get(offset + 13)
                if (
                  a1 == b1 && a2 == b2 && a3 == b3 && a4 == b4 && a5 == b5 && a6 == b6 && a7 == b7 && a8 == b8 &&
                  a9 == b9 && a10 == b10 && a11 == b11 && a12 == b12 && a13 == b13 && a14 == b14
                ) {
                    continue = false
                } else offset += 1
            }
            if (continue) -1 else offset - from
        } else -1

    // format: off
    private def bytesBefore14ignoreCase(b1: Byte, b2: Byte, b3: Byte, b4: Byte, b5: Byte, b6: Byte, b7: Byte, b8: Byte,
                              b9: Byte, b10: Byte, b11: Byte, b12: Byte, b13: Byte, b14: Byte,
                              from: Int, to: Int): Int =
    // format: on
        if (readableBytes >= 14) {
            var offset: Int       = from
            var continue: Boolean = true
            var a1: Byte          = 0
            var a2: Byte          = underlying.get(offset)
            var a3: Byte          = underlying.get(offset + 1)
            var a4: Byte          = underlying.get(offset + 2)
            var a5: Byte          = underlying.get(offset + 3)
            var a6: Byte          = underlying.get(offset + 4)
            var a7: Byte          = underlying.get(offset + 5)
            var a8: Byte          = underlying.get(offset + 6)
            var a9: Byte          = underlying.get(offset + 7)
            var a10: Byte         = underlying.get(offset + 8)
            var a11: Byte         = underlying.get(offset + 9)
            var a12: Byte         = underlying.get(offset + 10)
            var a13: Byte         = underlying.get(offset + 11)
            var a14: Byte         = underlying.get(offset + 12)
            while (continue && offset < to - 13) {
                a1 = a2; a2 = a3; a3 = a4; a4 = a5; a5 = a6; a6 = a7; a7 = a8; a8 = a9; a9 = a10; a10 = a11
                a11 = a12; a12 = a13; a13 = a14
                a14 = underlying.get(offset + 13)
                if (
                  ignoreCaseEqual(a1, b1) && ignoreCaseEqual(a2, b2) && ignoreCaseEqual(a3, b3) &&
                  ignoreCaseEqual(a4, b4) && ignoreCaseEqual(a5, b5) && ignoreCaseEqual(a6, b6) &&
                  ignoreCaseEqual(a7, b7) && ignoreCaseEqual(a8, b8) && ignoreCaseEqual(a9, b9) &&
                  ignoreCaseEqual(a10, b10) && ignoreCaseEqual(a11, b11) && ignoreCaseEqual(a12, b12) &&
                  ignoreCaseEqual(a13, b13) && ignoreCaseEqual(a14, b14)
                ) {
                    continue = false
                } else offset += 1
            }
            if (continue) -1 else offset - from
        } else -1

    // format: off
    private def bytesBefore15(b1: Byte, b2: Byte, b3: Byte, b4: Byte, b5: Byte, b6: Byte, b7: Byte, b8: Byte,
                               b9: Byte, b10: Byte, b11: Byte, b12: Byte, b13: Byte, b14: Byte, b15: Byte,
                              from: Int, to: Int): Int =
    // format: on
        if (readableBytes >= 15) {
            var offset: Int       = from
            var continue: Boolean = true
            var a1: Byte          = 0
            var a2: Byte          = underlying.get(offset)
            var a3: Byte          = underlying.get(offset + 1)
            var a4: Byte          = underlying.get(offset + 2)
            var a5: Byte          = underlying.get(offset + 3)
            var a6: Byte          = underlying.get(offset + 4)
            var a7: Byte          = underlying.get(offset + 5)
            var a8: Byte          = underlying.get(offset + 6)
            var a9: Byte          = underlying.get(offset + 7)
            var a10: Byte         = underlying.get(offset + 8)
            var a11: Byte         = underlying.get(offset + 9)
            var a12: Byte         = underlying.get(offset + 10)
            var a13: Byte         = underlying.get(offset + 11)
            var a14: Byte         = underlying.get(offset + 12)
            var a15: Byte         = underlying.get(offset + 13)
            while (continue && offset < to - 14) {
                a1 = a2; a2 = a3; a3 = a4; a4 = a5; a5 = a6; a6 = a7; a7 = a8; a8 = a9; a9 = a10; a10 = a11
                a11 = a12; a12 = a13; a13 = a14; a14 = a15
                a15 = underlying.get(offset + 14)
                if (
                  a1 == b1 && a2 == b2 && a3 == b3 && a4 == b4 && a5 == b5 && a6 == b6 && a7 == b7 && a8 == b8 &&
                  a9 == b9 && a10 == b10 && a11 == b11 && a12 == b12 && a13 == b13 && a14 == b14 && a15 == b15
                ) {
                    continue = false
                } else offset += 1
            }
            if (continue) -1 else offset - from
        } else -1

    // format: off
    private def bytesBefore15ignoreCase(b1: Byte, b2: Byte, b3: Byte, b4: Byte, b5: Byte, b6: Byte, b7: Byte, b8: Byte,
                              b9: Byte, b10: Byte, b11: Byte, b12: Byte, b13: Byte, b14: Byte, b15: Byte,
                              from: Int, to: Int): Int =
    // format: on
        if (readableBytes >= 15) {
            var offset: Int       = from
            var continue: Boolean = true
            var a1: Byte          = 0
            var a2: Byte          = underlying.get(offset)
            var a3: Byte          = underlying.get(offset + 1)
            var a4: Byte          = underlying.get(offset + 2)
            var a5: Byte          = underlying.get(offset + 3)
            var a6: Byte          = underlying.get(offset + 4)
            var a7: Byte          = underlying.get(offset + 5)
            var a8: Byte          = underlying.get(offset + 6)
            var a9: Byte          = underlying.get(offset + 7)
            var a10: Byte         = underlying.get(offset + 8)
            var a11: Byte         = underlying.get(offset + 9)
            var a12: Byte         = underlying.get(offset + 10)
            var a13: Byte         = underlying.get(offset + 11)
            var a14: Byte         = underlying.get(offset + 12)
            var a15: Byte         = underlying.get(offset + 13)
            while (continue && offset < to - 14) {
                a1 = a2; a2 = a3; a3 = a4; a4 = a5; a5 = a6; a6 = a7; a7 = a8; a8 = a9; a9 = a10; a10 = a11;
                a11 = a12; a12 = a13; a13 = a14; a14 = a15
                a15 = underlying.get(offset + 14)
                if (
                  ignoreCaseEqual(a1, b1) && ignoreCaseEqual(a2, b2) && ignoreCaseEqual(a3, b3) &&
                  ignoreCaseEqual(a4, b4) && ignoreCaseEqual(a5, b5) && ignoreCaseEqual(a6, b6) &&
                  ignoreCaseEqual(a7, b7) && ignoreCaseEqual(a8, b8) && ignoreCaseEqual(a9, b9) &&
                  ignoreCaseEqual(a10, b10) && ignoreCaseEqual(a11, b11) && ignoreCaseEqual(a12, b12) &&
                  ignoreCaseEqual(a13, b13) && ignoreCaseEqual(a14, b14) && ignoreCaseEqual(a15, b15)
                ) {
                    continue = false
                } else offset += 1
            }
            if (continue) -1 else offset - from
        } else -1

    // format: off
    private def bytesBefore16(b1: Byte, b2: Byte, b3: Byte, b4: Byte, b5: Byte, b6: Byte, b7: Byte, b8: Byte,
                               b9: Byte, b10: Byte, b11: Byte, b12: Byte, b13: Byte, b14: Byte, b15: Byte, b16: Byte,
                              from: Int, to: Int): Int =
    // format: on
        if (readableBytes >= 16) {
            var offset: Int       = from
            var continue: Boolean = true
            var a1: Byte          = 0
            var a2: Byte          = underlying.get(offset)
            var a3: Byte          = underlying.get(offset + 1)
            var a4: Byte          = underlying.get(offset + 2)
            var a5: Byte          = underlying.get(offset + 3)
            var a6: Byte          = underlying.get(offset + 4)
            var a7: Byte          = underlying.get(offset + 5)
            var a8: Byte          = underlying.get(offset + 6)
            var a9: Byte          = underlying.get(offset + 7)
            var a10: Byte         = underlying.get(offset + 8)
            var a11: Byte         = underlying.get(offset + 9)
            var a12: Byte         = underlying.get(offset + 10)
            var a13: Byte         = underlying.get(offset + 11)
            var a14: Byte         = underlying.get(offset + 12)
            var a15: Byte         = underlying.get(offset + 13)
            var a16: Byte         = underlying.get(offset + 14)
            while (continue && offset < to - 15) {
                a1 = a2; a2 = a3; a3 = a4; a4 = a5; a5 = a6; a6 = a7; a7 = a8; a8 = a9; a9 = a10; a10 = a11
                a11 = a12; a12 = a13; a13 = a14; a14 = a15; a15 = a16
                a16 = underlying.get(offset + 15)
                if (
                  a1 == b1 && a2 == b2 && a3 == b3 && a4 == b4 && a5 == b5 && a6 == b6 && a7 == b7 && a8 == b8 &&
                  a9 == b9 && a10 == b10 && a11 == b11 && a12 == b12 && a13 == b13 && a14 == b14 && a15 == b15 && a16 == b16
                ) {
                    continue = false
                } else offset += 1
            }
            if (continue) -1 else offset - from
        } else -1

    // format: off
    private def bytesBefore16ignoreCase(b1: Byte, b2: Byte, b3: Byte, b4: Byte, b5: Byte, b6: Byte, b7: Byte, b8: Byte,
                              b9: Byte, b10: Byte, b11: Byte, b12: Byte, b13: Byte, b14: Byte, b15: Byte, b16: Byte,
                              from: Int, to: Int): Int =
    // format: on
        if (readableBytes >= 16) {
            var offset: Int       = from
            var continue: Boolean = true
            var a1: Byte          = 0
            var a2: Byte          = underlying.get(offset)
            var a3: Byte          = underlying.get(offset + 1)
            var a4: Byte          = underlying.get(offset + 2)
            var a5: Byte          = underlying.get(offset + 3)
            var a6: Byte          = underlying.get(offset + 4)
            var a7: Byte          = underlying.get(offset + 5)
            var a8: Byte          = underlying.get(offset + 6)
            var a9: Byte          = underlying.get(offset + 7)
            var a10: Byte         = underlying.get(offset + 8)
            var a11: Byte         = underlying.get(offset + 9)
            var a12: Byte         = underlying.get(offset + 10)
            var a13: Byte         = underlying.get(offset + 11)
            var a14: Byte         = underlying.get(offset + 12)
            var a15: Byte         = underlying.get(offset + 13)
            var a16: Byte         = underlying.get(offset + 14)
            while (continue && offset < to - 15) {
                a1 = a2; a2 = a3; a3 = a4; a4 = a5; a5 = a6; a6 = a7; a7 = a8; a8 = a9; a9 = a10; a10 = a11
                a11 = a12; a12 = a13; a13 = a14; a14 = a15; a15 = a16
                a16 = underlying.get(offset + 15)
                if (
                  ignoreCaseEqual(a1, b1) && ignoreCaseEqual(a2, b2) && ignoreCaseEqual(a3, b3) &&
                  ignoreCaseEqual(a4, b4) && ignoreCaseEqual(a5, b5) && ignoreCaseEqual(a6, b6) &&
                  ignoreCaseEqual(a7, b7) && ignoreCaseEqual(a8, b8) && ignoreCaseEqual(a9, b9) &&
                  ignoreCaseEqual(a10, b10) && ignoreCaseEqual(a11, b11) && ignoreCaseEqual(a12, b12) &&
                  ignoreCaseEqual(a13, b13) && ignoreCaseEqual(a14, b14) && ignoreCaseEqual(a15, b15) &&
                  ignoreCaseEqual(a16, b16)
                ) {
                    continue = false
                } else offset += 1
            }
            if (continue) -1 else offset - from
        } else -1

    override def bytesBefore(needle: Array[Byte]): Int = bytesBefore(needle, ridx, widx)

    private def bytesBeforeBytes(bts: Array[Byte], from: Int, to: Int): Int = {
        val length            = bts.length
        val a1                = bytes8Long(bts(0), bts(1), bts(2), bts(3), bts(4), bts(5), bts(6), bts(7))
        val a2                = bytes8Long(bts(8), bts(9), bts(10), bts(11), bts(12), bts(13), bts(14), bts(15))
        var offset: Int       = from
        var continue: Boolean = true
        while (continue && offset < to - length) {
            if (underlying.getLong(offset) != a1 || underlying.getLong(offset + 8) != a2)
                offset += 1 // 16 bytes prefix match
            else {
                var i    = 16
                var same = true
                while (same && i < length) {
                    val b = underlying.get(offset + i)
                    val a = bts(i)
                    same = same && a == b
                    i += 1
                }
                if (same) continue = false else offset += 1
            }
        }
        if (continue) -1 else offset - from
    }

    private def bytesBeforeBytesIgnoreCase(bts: Array[Byte], from: Int, to: Int): Int = {
        val a1                = bytes8Long(bts(0), bts(1), bts(2), bts(3), bts(4), bts(5), bts(6), bts(7))
        val a2                = bytes8Long(bts(8), bts(9), bts(10), bts(11), bts(12), bts(13), bts(14), bts(15))
        var offset: Int       = from
        var continue: Boolean = true
        while (continue && offset < to - bts.length) {
            if (underlying.getLong(offset) != a1 || underlying.getLong(offset + 8) != a2)
                offset += 1 // 16 bytes prefix match
            else {
                var i    = 16
                var same = true
                while (same && i < bts.length) {
                    val b = underlying.get(offset + i)
                    val a = bts(i)
                    same = same && ignoreCaseEqual(a, b)
                    i += 1
                }
                if (same) continue = false else offset += 1
            }
        }
        if (continue) -1 else offset - from
    }

    override def bytesBefore(needle: Array[Byte], from: Int, to: Int, ignoreCase: Boolean): Int =
        if (to - from >= needle.length) {
            checkFromTo(from, to)

            if (ignoreCase) {
                needle.length match
                    case 1 => bytesBefore1ignoreCase(needle(0), from, to)
                    case 2 => bytesBefore2ignoreCase(needle(0), needle(1), from, to)
                    case 3 => bytesBefore3ignoreCase(needle(0), needle(1), needle(2), from, to)
                    case 4 => bytesBefore4ignoreCase(needle(0), needle(1), needle(2), needle(3), from, to)
                    case 5 => bytesBefore5ignoreCase(needle(0), needle(1), needle(2), needle(3), needle(4), from, to)
                    // format: off
                    case 6 => bytesBefore6ignoreCase(needle(0), needle(1), needle(2), needle(3), needle(4), needle(5), from, to)
                    case 7 => bytesBefore7ignoreCase(needle(0), needle(1), needle(2), needle(3), needle(4), needle(5), needle(6), from, to)
                    case 8 => bytesBefore8ignoreCase(needle(0), needle(1), needle(2), needle(3), needle(4), needle(5), needle(6), needle(7), from, to)
                    case 9 => bytesBefore9ignoreCase(needle(0), needle(1), needle(2), needle(3), needle(4), needle(5), needle(6), needle(7), needle(8), from, to)
                    case 10 => bytesBefore10ignoreCase(needle(0), needle(1), needle(2), needle(3), needle(4), needle(5), needle(6), needle(7), needle(8), needle(9), from, to)
                    case 11 => bytesBefore11ignoreCase(needle(0), needle(1), needle(2), needle(3), needle(4), needle(5), needle(6), needle(7),
                        needle(8), needle(9), needle(10), from, to)
                    case 12 => bytesBefore12ignoreCase(needle(0), needle(1), needle(2), needle(3), needle(4), needle(5), needle(6), needle(7),
                        needle(8), needle(9), needle(10), needle(11), from, to)
                    case 13 => bytesBefore13ignoreCase(needle(0), needle(1), needle(2), needle(3), needle(4), needle(5), needle(6), needle(7),
                        needle(8), needle(9), needle(10), needle(11), needle(12),from, to)
                    case 14 => bytesBefore14ignoreCase(needle(0), needle(1), needle(2), needle(3), needle(4), needle(5), needle(6), needle(7),
                        needle(8), needle(9), needle(10), needle(11), needle(12), needle(13), from, to)
                    case 15 => bytesBefore15ignoreCase(needle(0), needle(1), needle(2), needle(3), needle(4), needle(5), needle(6), needle(7),
                        needle(8), needle(9), needle(10), needle(11), needle(12), needle(13), needle(14), from, to)
                    case 16 => bytesBefore16ignoreCase(needle(0), needle(1), needle(2), needle(3), needle(4), needle(5), needle(6), needle(7),
                        needle(8), needle(9), needle(10), needle(11), needle(12), needle(13), needle(14), needle(15), from, to)
                    // format: on
                    case _ => bytesBeforeBytesIgnoreCase(needle, from, to)
            } else {
                needle.length match
                    case 1 => bytesBefore1(needle(0), from, to)
                    case 2 => bytesBefore2(needle(0), needle(1), from, to)
                    case 3 => bytesBefore3(needle(0), needle(1), needle(2), from, to)
                    case 4 => bytesBefore4(needle(0), needle(1), needle(2), needle(3), from, to)
                    case 5 => bytesBefore5(needle(0), needle(1), needle(2), needle(3), needle(4), from, to)
                    // format: off
                    case 6 => bytesBefore6(needle(0), needle(1), needle(2), needle(3), needle(4), needle(5), from, to)
                    case 7 => bytesBefore7(needle(0), needle(1), needle(2), needle(3), needle(4), needle(5), needle(6), from, to)

                    case 8 => bytesBefore8(needle(0), needle(1), needle(2), needle(3), needle(4), needle(5), needle(6), needle(7), from, to)
                    case 9 => bytesBefore9(needle(0), needle(1), needle(2), needle(3), needle(4), needle(5), needle(6), needle(7),
                        needle(8), from, to)
                    case 10 => bytesBefore10(needle(0), needle(1), needle(2), needle(3), needle(4), needle(5), needle(6), needle(7),
                        needle(8), needle(9), from, to)
                    case 11 => bytesBefore11(needle(0), needle(1), needle(2), needle(3), needle(4), needle(5), needle(6), needle(7),
                        needle(8), needle(9), needle(10), from, to)
                    case 12 => bytesBefore12(needle(0), needle(1), needle(2), needle(3), needle(4), needle(5), needle(6), needle(7),
                        needle(8), needle(9), needle(10), needle(11), from, to)
                    case 13 => bytesBefore13(needle(0), needle(1), needle(2), needle(3), needle(4), needle(5), needle(6), needle(7),
                        needle(8), needle(9), needle(10), needle(11), needle(12), from, to)
                    case 14 => bytesBefore14(needle(0), needle(1), needle(2), needle(3), needle(4), needle(5), needle(6), needle(7),
                        needle(8), needle(9), needle(10), needle(11), needle(12), needle(13), from, to)
                    case 15 => bytesBefore15(needle(0), needle(1), needle(2), needle(3), needle(4), needle(5), needle(6), needle(7),
                        needle(8), needle(9), needle(10), needle(11), needle(12), needle(13), needle(14), from, to)
                    case 16 => bytesBefore16(needle(0), needle(1), needle(2), needle(3), needle(4), needle(5), needle(6), needle(7),
                        needle(8), needle(9), needle(10), needle(11), needle(12), needle(13), needle(14), needle(15), from, to)
                    // format: on
                    case _ => bytesBeforeBytes(needle, from, to)
            }
        } else -1

    override def openCursor(fromOffset: Int, length: Int): ByteCursor = {
        if (closed) throw new BufferClosedException()
        if (fromOffset < 0) throw new IndexOutOfBoundsException(s"The fromOffset cannot be negative: ${fromOffset}")
        if (length < 0) throw new IndexOutOfBoundsException(s"The length cannot be negative: ${length}")
        if (capacity < fromOffset + length)
            throw new IndexOutOfBoundsException(
              s"The fromOffset + length is beyond the end of the buffer: fromOffset = ${fromOffset}, length = ${length}"
            )

        new ByteCursor {
            private var idx: Int    = fromOffset
            private val end: Int    = fromOffset + length
            private var value: Byte = _

            override def readByte: Boolean = if (idx < end) {
                value = underlying.get(idx)
                idx += 1
                true
            } else false

            override def getByte: Byte = value

            override def currentOffset: Int = idx

            override def bytesLeft: Int = end - idx
        }

    }

    override def openReverseCursor(fromOffset: Int, length: Int): ByteCursor = {
        if (closed) throw new BufferClosedException()
        if (fromOffset < 0) throw new IndexOutOfBoundsException(s"The fromOffset cannot be negative: ${fromOffset}")
        if (length < 0) throw new IndexOutOfBoundsException(s"The length cannot be negative: ${length}")
        if (capacity <= fromOffset)
            throw new IndexOutOfBoundsException(s"The fromOffset is beyond the end of the buffer: ${fromOffset}")
        if (fromOffset - length < -1)
            new IndexOutOfBoundsException(
              "The fromOffset - length would underflow the buffer: " + "fromOffset = " + fromOffset + ", length = " + length + '.'
            )

        new ByteCursor {
            private var idx: Int    = fromOffset
            private val end: Int    = fromOffset - length
            private var value: Byte = -1

            override def readByte: Boolean = if (idx > end) {
                value = underlying.get(idx)
                idx -= 1
                true
            } else false

            override def getByte: Byte = value

            override def currentOffset: Int = idx

            override def bytesLeft: Int = idx - end
        }
    }

    override def ensureWritable(size: Int, minimumGrowth: Int, allowCompaction: Boolean): Buffer = {
        if (this.writableBytes >= size) {} else if (capacity - this.readableBytes >= size) {
            if (allowCompaction) compact()
            else throw new IllegalStateException(s"${this} can't write with size ${size} ")
        } else throw new IllegalStateException(s"${this} can't write with size ${size} ")
        this
    }

    override def readByte: Byte = {
        val b = underlying.get(ridx)
        ridx += 1
        b
    }

    override def getByte(index: Int): Byte = {
        underlying.get(index)
    }

    override def readUnsignedByte: Int = readByte & 0xff

    override def getUnsignedByte(index: Int): Int = getByte(index) & 0xff

    override def writeByte(value: Byte): Buffer = {
        underlying.put(widx, value)
        widx += JByte.BYTES
        this
    }

    override def setByte(index: Int, value: Byte): Buffer = {
        underlying.put(index, value)
        this
    }

    override def writeUnsignedByte(value: Int): Buffer = {
        underlying.put(widx, (value & 0xff).toByte)
        widx += JByte.BYTES
        this
    }

    override def setUnsignedByte(index: Int, value: Int): Buffer = {
        underlying.put(index, (value & 0xff).toByte)
        this
    }

    override def readChar: Char = {
        val idx = ridx
        ridx += Character.BYTES
        underlying.getChar(idx)
    }

    override def getChar(index: Int): Char = underlying.getChar(index)

    override def writeChar(value: Char): Buffer = {
        underlying.putChar(widx, value)
        widx += Character.BYTES
        this
    }

    override def setChar(index: Int, value: Char): Buffer = {
        underlying.putChar(index, value)
        this
    }

    override def readShort: Short = {
        val idx = ridx
        ridx += JShort.BYTES
        underlying.getShort(idx)
    }

    override def getShort(index: Int): Short = {
        underlying.getShort(ridx)
    }

    override def readUnsignedShort: Int = {
        val idx = ridx
        ridx += JShort.BYTES
        underlying.getShort(idx) & 0xffff
    }

    override def getUnsignedShort(index: Int): Int = underlying.getShort(ridx) & 0xffff

    override def writeShort(value: Short): Buffer = {
        underlying.putShort(widx, value)
        widx += JShort.BYTES
        this
    }

    override def setShort(index: Int, value: Short): Buffer = {
        underlying.putShort(index, value)
        this
    }

    override def writeUnsignedShort(value: Int): Buffer = {
        underlying.putShort(widx, (value & 0xffff).toShort)
        widx += JShort.BYTES
        this
    }

    override def setUnsignedShort(index: Int, value: Int): Buffer = {
        underlying.putShort(index, (value & 0xffff).toShort)
        this
    }

    override def readMedium: Int = {
        val value =
            underlying.get(ridx) << 16 | (underlying.get(ridx + 1) & 0xff) << 8 | underlying.get(ridx + 2) & 0xff
        ridx += 3
        value
    }

    override def readMediumLE: Int = {
        val value =
            underlying.get(ridx + 2) << 16 | (underlying.get(ridx + 1) & 0xff) << 8 | underlying.get(ridx) & 0xff
        ridx += 3
        value
    }

    override def getMedium(index: Int): Int =
        underlying.get(index) << 16 | (underlying.get(index + 1) & 0xff) << 8 | underlying.get(index + 2) & 0xff

    override def getMediumLE(index: Int): Int =
        underlying.get(index + 2) << 16 | (underlying.get(index + 1) & 0xff) << 8 | underlying.get(index) & 0xff

    override def readUnsignedMedium: Int = {
        val value =
            (underlying.get(ridx) << 16 | (underlying.get(ridx + 1) & 0xff) << 8 |
                underlying.get(ridx + 2) & 0xff) & 0xffffff
        ridx += 3
        value
    }

    override def readUnsignedMediumLE: Int = {
        val value =
            (underlying.get(ridx + 2) << 16 | (underlying.get(ridx + 1) & 0xff) << 8 |
                underlying.get(ridx) & 0xff) & 0xffffff
        ridx += 3
        value
    }

    override def getUnsignedMedium(index: Int): Int =
        (underlying.get(index) << 16 | (underlying.get(index + 1) & 0xff) << 8 |
            underlying.get(index + 2) & 0xff) & 0xffffff

    override def getUnsignedMediumLE(index: Int): Int =
        (underlying.get(index + 2) << 16 | (underlying.get(index + 1) & 0xff) << 8 |
            underlying.get(index) & 0xff) & 0xffffff

    override def writeMedium(value: Int): Buffer = {
        underlying.put(widx, (value >> 16).toByte)
        underlying.put(widx + 1, (value >> 8 & 0xff).toByte)
        underlying.put(widx + 2, (value & 0xff).toByte)
        widx += 3
        this
    }

    override def writeMediumLE(value: Int): Buffer = {
        underlying.put(widx, (value & 0xff).toByte)
        underlying.put(widx + 1, (value >> 8 & 0xff).toByte)
        underlying.put(widx + 2, (value >> 16).toByte)
        widx += 3
        this
    }

    override def setMedium(index: Int, value: Int): Buffer = {
        underlying.put(index, (value >> 16).toByte)
        underlying.put(index + 1, (value >> 8 & 0xff).toByte)
        underlying.put(index + 2, (value & 0xff).toByte)
        this
    }

    override def setMediumLE(index: Int, value: Int): Buffer = {
        underlying.put(index, value.toByte)
        underlying.put(index + 1, (value >>> 8).toByte)
        underlying.put(index + 2, (value >>> 16).toByte)
        this
    }

    override def writeUnsignedMedium(value: Int): Buffer = {
        underlying.put(widx, (value >> 16).toByte)
        underlying.put(widx + 1, (value >> 8 & 0xff).toByte)
        underlying.put(widx + 2, (value & 0xff).toByte)
        widx += 3
        this
    }

    override def writeUnsignedMediumLE(value: Int): Buffer = {
        underlying.put(widx, (value & 0xff).toByte)
        underlying.put(widx + 1, (value >> 8 & 0xff).toByte)
        underlying.put(widx + 2, (value >> 16).toByte)
        widx += 3
        this
    }

    override def setUnsignedMedium(index: Int, value: Int): Buffer = {
        underlying.put(widx, (value >> 16).toByte)
        underlying.put(widx + 1, (value >> 8 & 0xff).toByte)
        underlying.put(widx + 2, (value & 0xff).toByte)
        this
    }

    override def setUnsignedMediumLE(index: Int, value: Int): Buffer = {
        underlying.put(widx, (value & 0xff).toByte)
        underlying.put(widx + 1, (value >> 8 & 0xff).toByte)
        underlying.put(widx + 2, (value >> 16).toByte)
        this
    }

    override def readInt: Int = {
        val value = underlying.getInt(ridx)
        ridx += Integer.BYTES
        value
    }

    override def getInt(index: Int): Int = underlying.getInt(index)

    override def readUnsignedInt: Long = {
        val value = underlying.getInt(ridx) & 0xffffffffL
        ridx += Integer.BYTES
        value
    }

    override def getUnsignedInt(index: Int): Long = underlying.getInt(index) & 0xffffffffL

    override def writeInt(value: Int): Buffer = {
        underlying.putInt(widx, value)
        widx += Integer.BYTES
        this
    }

    override def setInt(index: Int, value: Int): Buffer = {
        underlying.putInt(index, value)
        this
    }

    override def writeUnsignedInt(value: Long): Buffer = {
        underlying.putInt(widx, (value & 0xffffffffL).toInt)
        widx += Integer.BYTES
        this
    }

    override def setUnsignedInt(index: Int, value: Long): Buffer = {
        underlying.putInt(index, (value & 0xffffffffL).toInt)
        this
    }

    override def readFloat: Float = {
        val value = underlying.getFloat(ridx)
        ridx += JFloat.BYTES
        value
    }

    override def getFloat(index: Int): Float = underlying.getFloat(index)

    override def writeFloat(value: Float): Buffer = {
        underlying.putFloat(widx, value)
        widx += JFloat.BYTES
        this
    }

    override def setFloat(index: Int, value: Float): Buffer = {
        underlying.putFloat(index, value)
        this
    }

    override def readLong: Long = {
        val value = underlying.getLong(ridx)
        ridx += JLong.BYTES
        value
    }

    override def getLong(index: Int): Long = {
        val value = underlying.getLong(index)
        value
    }

    override def writeLong(value: Long): Buffer = {
        underlying.putLong(ridx, value)
        ridx += JLong.BYTES
        this
    }

    override def setLong(index: Int, value: Long): Buffer = {
        underlying.putLong(index, value)
        this
    }

    override def readDouble: Double = {
        val value = underlying.getDouble(ridx)
        ridx += JDouble.BYTES
        value
    }

    override def getDouble(index: Int): Double = {
        val value = underlying.getDouble(index)
        value
    }

    override def writeDouble(value: Double): Buffer = {
        underlying.putDouble(widx, value)
        widx += JDouble.BYTES
        this
    }

    override def setDouble(index: Int, value: Double): Buffer = {
        underlying.putDouble(index, value)
        this
    }

    override def close(): Unit = {
        ridx = 0
        widx = 0
        underlying.clear()
    }

    override def clean(): this.type = {
        widx = 0
        ridx = 0
        this
    }

    override def capacity: Int = underlying.capacity()

    override def fill(value: Byte): Buffer = {
        underlying.clear()
        var i = 0
        while (i < capacity) {
            underlying.put(i, value)
            i += 1
        }
        this
    }

    override def copyInto(srcPos: Int, dest: Array[Byte], destPos: Int, length: Int): Unit = {
        underlying.get(srcPos, dest, destPos, length)
    }

    override def copyInto(srcPos: Int, dest: ByteBuffer, destPos: Int, length: Int): Unit = {
        dest.put(destPos, underlying, srcPos, length)
    }

    override def copyInto(srcPos: Int, dest: Buffer, destPos: Int, length: Int): Unit = {
        dest match
            case buffer: AbstractBuffer => copyInto(srcPos, buffer.underlying, destPos, length)
            case _                      => throw new UnsupportedOperationException()
    }

    override def transferTo(channel: WritableByteChannel, length: Int): Int = {
        if (length > 0) {
            underlying.position(ridx)
            if (length > readableBytes) underlying.limit(ridx + readableBytes) else underlying.limit(ridx + length)
            underlying.clear()
            val write = channel.write(underlying)
            if (write > 0) skipReadableBytes(write)
            write
        } else 0
    }

    override def transferFrom(channel: FileChannel, position: Long, length: Int): Int = {
        if (length > 0) {
            underlying.position(widx)
            if (length > writableBytes) underlying.limit(widx + writableBytes) else underlying.limit(widx + length)
            underlying.clear()
            val read = channel.read(underlying, position)
            if (read > 0) skipWritableBytes(read)
            read
        } else 0
    }

    override def transferFrom(channel: ReadableByteChannel, length: Int): Int = {
        if (length > 0) {
            underlying.position(widx)
            if (length > writableBytes) underlying.limit(widx + writableBytes) else underlying.limit(widx + length)
            underlying.clear()
            val read = channel.read(underlying)
            if (read > 0) skipWritableBytes(read)
            read
        } else 0
    }

    override def nextIs(byte: Byte): Boolean = underlying.get(ridx) == byte

    override def nextAre(bytes: Array[Byte]): Boolean = if (readableBytes > bytes.length) {
        var same: Boolean = true
        var i             = 0
        while (same && i < bytes.length) {
            same = same && underlying.get(ridx + i) == bytes(i)
            i += 1
        }
        same
    } else false

    override def indexIs(byte: Byte, index: Int): Boolean = underlying.get(index) == byte

    override def nextIn(bytes: Array[Byte]): Boolean = {
        var notIn = true
        var i     = 0
        val b     = underlying.get(ridx)
        while (notIn && i < bytes.length) {
            notIn = b != bytes(i)
            i += 1
        }
        !notIn
    }

    override def indexIn(bytes: Array[Byte], index: Int): Boolean = {
        var notIn = true
        var i     = 0
        val b     = underlying.get(index)
        while (notIn && i < bytes.length) {
            notIn = b != bytes(i)
            i += 1
        }
        !notIn
    }

    override def nextInRange(lower: Byte, upper: Byte): Boolean = {
        val b = underlying.get(ridx)
        b >= lower && b <= upper
    }

    override def indexInRange(lower: Byte, upper: Byte, index: Int): Boolean = {
        val b = underlying.get(index)
        b >= lower && b <= upper
    }

    override def skipIfNextIs(byte: Byte): Boolean = if (underlying.get(ridx) == byte) {
        ridx += 1
        true
    } else false

    override def skipIfNextAre(bytes: Array[Byte]): Boolean = {
        var skip = true
        var i    = 0
        while (skip && i < bytes.length) {
            skip = underlying.get(ridx + i) == bytes(i)
            i += 1
        }
        if (skip) ridx += bytes.length
        skip
    }

    override def skipIfNextIn(set: Array[Byte]): Boolean = {
        var notIn = true
        var i     = 0
        val b     = underlying.get(ridx)
        while (notIn && i < set.length) {
            notIn = b != set(i)
            i += 1
        }
        if (!notIn) ridx += 1
        !notIn
    }

    inline private def checkRead(index: Int, size: Int): Unit =
        if (index < 0 || widx < index + size) throw outOfBounds(index, size)

    inline private def checkGet(index: Int, size: Int): Unit =
        if (index < 0 || capacity < index + size) throw outOfBounds(index, size)

    inline private def checkWrite(index: Int, size: Int): Unit =
        if (index < ridx || capacity < index + size) throw outOfBounds(index, size)

    inline private def checkSet(index: Int, size: Int): Unit =
        if (index < 0 || capacity < index + size) throw outOfBounds(index, size)

    inline private def outOfBounds(index: Int, size: Int): IndexOutOfBoundsException =
        new IndexOutOfBoundsException(
          s"Access at index ${index} of size ${size} is out of bounds: [read 0 to ${widx}, write 0 to ${capacity}]."
        )

    inline private def checkFromTo(from: Int, to: Int): Unit = {
        if (from < ridx)
            throw new IndexOutOfBoundsException(s"from is less than readerOffset: form = $from, readerOffset = $ridx")
        if (to > widx)
            throw new IndexOutOfBoundsException(s"to is beyond the end of the buffer: to = $to, writerOffset = $widx")
    }

}
