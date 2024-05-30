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

package cc.otavia.buffer.pool

import cc.otavia.buffer.*
import cc.otavia.buffer.BytesUtil.ignoreCaseEqual
import cc.otavia.buffer.pool.{PooledPageAllocator, RecyclablePageBuffer}

import java.lang.{Byte as JByte, Double as JDouble, Float as JFloat, Long as JLong, Short as JShort}
import java.nio.ByteBuffer
import java.nio.channels.{FileChannel, ReadableByteChannel, WritableByteChannel}
import java.nio.charset.Charset
import java.util.UUID
import javax.net.ssl.{SSLEngine, SSLEngineResult}
import scala.collection.mutable
import scala.language.unsafeNulls

final private class AdaptiveBufferImpl(val allocator: PooledPageAllocator)
    extends mutable.ArrayDeque[RecyclablePageBuffer]
    with AdaptiveBuffer {

    private var ridx: Int = 0
    private var widx: Int = 0

    private var stClosed: Boolean = false

    private def startIndex = ridx - head.readerOffset

    private def endIndex: Int = widx + last.writableBytes

    private def offsetAtOffset(offset: Int): (Int, Int) = if (nonEmpty && offset - ridx <= head.readableBytes) {
        (0, offset - ridx)
    } else if (nonEmpty && offset >= startIndex && offset < endIndex) {
        var len    = offset - ridx
        var cursor = 0
        while (cursor < size && len > apply(cursor).readableBytes) {
            len -= apply(cursor).readableBytes
            cursor += 1
        }
        (cursor, len)
    } else (-1, 0)

    private def recycleHead(compact: Boolean = false): Unit = {
        if (nonEmpty) {
            val buffer = removeHead()
            buffer.close()
        }
        if (compact) {
            widx = widx - ridx
            ridx = 0
        }
    }

    private def recycleAll(compact: Boolean = false): Unit = {
        while (nonEmpty) {
            val buffer = removeHead()
            buffer.close()
        }
        if (compact) {
            ridx = 0
            widx = 0
            clearAndShrink()
        } else ridx = widx
    }

    override def compact(): Buffer = {
        widx -= ridx
        ridx = 0
        this
    }

    private def extendBuffer(): Unit = {
        val buffer: RecyclablePageBuffer = allocator.allocate()
        addOne(buffer)
        widx += buffer.readableBytes
    }

    final private[otavia] def extend(buffer: RecyclablePageBuffer): Unit = {
        addOne(buffer)
        widx += buffer.readableBytes
    }

    override private[otavia] def splitBefore(offset: Int): RecyclablePageBuffer = {
        val (idx, off) = offsetAtOffset(offset)
        val split      = apply(idx)
        val len        = split.readableBytes - off
        val first      = head
        var cursor     = head
        var continue   = true
        while (continue) {
            if (cursor == split) {
                if (len == 0) {
                    removeHead()
                    continue = false
                } else { // len > 0, copy from head buffer
                    val buf = allocator.allocate()
                    cursor.copyInto(cursor.readerOffset + off, buf, cursor.readerOffset + off, len)
                    buf.writerOffset(cursor.writerOffset)
                    buf.readerOffset(cursor.readerOffset + off)
                    cursor.writerOffset(cursor.writerOffset - len)
                    removeHead()
                    insert(0, buf)
                    continue = false
                }
            } else {
                removeHead()
                cursor.next = head
                cursor = cursor.next
            }
        }
        ridx = offset
        first
    }

    override private[otavia] def splitLast(): RecyclablePageBuffer = {
        val last = removeLast()
        widx -= last.readableBytes
        last
    }

    override def capacity: Int = Int.MaxValue

    override def readerOffset: Int = ridx

    private def checkReadBounds(index: Int): Unit = {
        if (isEmpty && index > 0) throw new IndexOutOfBoundsException("The buffer is empty")
        if (index > widx) throw new IndexOutOfBoundsException("The new readerOffset is bigger than writerOffset")
        if (index < startIndex)
            throw new IndexOutOfBoundsException(
              s"The memory set by $index has been release already! " +
                  s"Current the minimum readerOffset can be set as $startIndex"
            )
    }

    override def readerOffset(offset: Int): Buffer = {
        if (offset == widx) recycleAll()
        else {
            checkReadBounds(offset)
            val (index, off) = offsetAtOffset(offset)
            var cursor       = index
            while (cursor > 0) {
                recycleHead()
                cursor -= 1
            }
            if (head.readableBytes > off) head.readerOffset(head.readerOffset + off) else recycleHead()
            ridx = offset
        }
        this
    }

    override def writerOffset: Int = widx

    private def checkWriteBound(index: Int): Unit = {
        if (index < ridx)
            throw new IndexOutOfBoundsException(s"Set writerOffset $index is little than readerOffset $ridx")
    }

    override def writerOffset(offset: Int): Buffer = { // FIXME:
        checkWriteBound(offset)
        if (offset > widx) {
            if (offset > endIndex) {
                var len = offset - endIndex
                last.writerOffset(last.capacity)
                while {
                    val buffer = allocator.allocate()
                    val cap    = buffer.capacity
                    if (len > cap) {
                        buffer.writerOffset(buffer.capacity)
                        len -= cap
                    } else {
                        buffer.writerOffset(len)
                        len = 0
                    }
                    len > 0
                } do ()
                widx = offset
            } else {
                widx = offset
            }
        } else {
            val (index, off) = offsetAtOffset(offset)
            var release      = size - index - 1
            while (release > 0) {
                val last = splitLast()
                last.close()
                release -= 1
            }
            last.writerOffset(last.readerOffset + off)
        }
        this
    }

    override def fill(value: Byte): Buffer = {
        for (buffer <- this) buffer.fill(value)
        this
    }

    override def isDirect: Boolean = allocator.isDirect

    override def copyInto(srcPos: Int, dest: Array[Byte], destPos: Int, length: Int): Unit = {
        if (destPos + length > dest.length)
            throw new IndexOutOfBoundsException(s"destPos + length = ${destPos + length} is large than length of dest")
        if (srcPos < ridx)
            throw new IndexOutOfBoundsException(s"srcPos = ${srcPos} can't less than readerOffset = ${ridx}")
        if (srcPos >= widx)
            throw new IndexOutOfBoundsException(s"srcPos = ${srcPos} can't large than writerOffset = ${widx}")
        if (length > widx - srcPos)
            throw new IndexOutOfBoundsException(
              s"length = ${length} can't large than writerOffset - srcPos = ${widx - srcPos} of this buffer"
            )

        if (size == 1) {
            head.copyInto(head.readerOffset + (srcPos - ridx), dest, destPos, length)
        } else {
            val (srcIndex, srcOff) = offsetAtOffset(srcPos)
            val (endIndex, off)    = offsetAtOffset(srcPos + length)
            if (srcIndex == endIndex) {
                val buffer = apply(srcIndex)
                buffer.copyInto(buffer.readerOffset + srcOff, dest, destPos, length)
            } else {
                // first component buffer
                val first  = apply(srcIndex)
                var accPos = destPos
                var len    = first.readableBytes - srcOff
                first.copyInto(first.readerOffset + srcOff, dest, accPos, len)
                accPos += len

                var i = srcIndex + 1
                while (i < endIndex) {
                    val buffer = apply(i)
                    len = buffer.readableBytes
                    buffer.copyInto(buffer.readerOffset, dest, accPos, len)
                    accPos += len
                    i += 1
                }

                // last component buffer
                val last = apply(endIndex)
                len = off
                last.copyInto(last.readerOffset, dest, accPos, len)
            }
        }
    }

    override def copyInto(srcPos: Int, dest: ByteBuffer, destPos: Int, length: Int): Unit = {
        if (destPos + length > dest.capacity())
            throw new IndexOutOfBoundsException(
              s"destPos + length = ${destPos + length} is large than capacity of dest"
            )
        if (srcPos < ridx)
            throw new IndexOutOfBoundsException(s"srcPos = ${srcPos} can't less than readerOffset = ${ridx}")
        if (srcPos >= widx)
            throw new IndexOutOfBoundsException(s"srcPos = ${srcPos} can't large than writerOffset = ${widx}")
        if (length > widx - srcPos)
            throw new IndexOutOfBoundsException(
              s"length = ${length} can't large than writerOffset - srcPos = ${widx - srcPos} of this buffer"
            )

        if (size == 1) {
            head.copyInto(head.readerOffset + (srcPos - ridx), dest, destPos, length)
        } else {
            val (srcIndex, srcOff) = offsetAtOffset(srcPos)
            val (endIndex, off)    = offsetAtOffset(srcPos + length)
            if (srcIndex == endIndex) {
                val buffer = apply(srcIndex)
                buffer.copyInto(buffer.readerOffset + srcOff, dest, destPos, length)
            } else {
                // first component buffer
                val first  = apply(srcIndex)
                var accPos = destPos
                var len    = first.readableBytes - srcOff
                first.copyInto(first.readerOffset + srcOff, dest, accPos, len)
                accPos += len

                var i = srcIndex + 1
                while (i < endIndex) {
                    val buffer = apply(i)
                    len = buffer.readableBytes
                    buffer.copyInto(buffer.readerOffset, dest, accPos, len)
                    accPos += len
                    i += 1
                }

                // last component buffer
                val last = apply(endIndex)
                len = off
                last.copyInto(last.readerOffset, dest, accPos, len)
            }
        }
    }

    override def copyInto(srcPos: Int, dest: Buffer, destPos: Int, length: Int): Unit =
        throw new UnsupportedOperationException("AdaptiveBuffer not support copyInto Buffer")

    override def transferTo(channel: WritableByteChannel, length: Int): Int =
        throw new UnsupportedOperationException("AdaptiveBuffer not support transferTo WritableByteChannel")

    override def transferFrom(channel: FileChannel, position: Long, length: Int): Int =
        throw new UnsupportedOperationException("AdaptiveBuffer not support transferFrom FileChannel")

    override def transferFrom(channel: ReadableByteChannel, length: Int): Int =
        throw new UnsupportedOperationException("AdaptiveBuffer not support transferFrom ReadableByteChannel")

    override def bytesBefore(needle: Byte): Int = {
        var cursor: Int       = ridx
        var continue: Boolean = true
        var idx               = 0
        var idxStart          = ridx
        while (continue && idx < size) {
            val buffer     = apply(idx)
            val len        = buffer.readableBytes
            val byteBuffer = buffer.underlying
            while (continue && cursor < idxStart + len)
                if (byteBuffer.get(buffer.readerOffset + cursor - idxStart) == needle) continue = false else cursor += 1
            // buffer not find
            if (continue) { idxStart += len; idx += 1 }
        }
        if (continue) -1 else cursor - ridx
    }

    private def bytesBefore1(a: Byte, from: Int, to: Int): Int = {
        val (start, offset) = offsetAtOffset(from)
        val end         = if (to == widx) size else { val tp = offsetAtOffset(to); if (tp._2 > 0) tp._1 + 1 else tp._1 }
        var cursor: Int = from
        var continue: Boolean = true
        var idx               = start
        var idxStart          = from - offset
        while (continue && idx < end) {
            val buffer     = apply(idx)
            val len        = buffer.readableBytes
            val byteBuffer = buffer.underlying
            while (continue && cursor < idxStart + len && cursor < to)
                if (byteBuffer.get(buffer.readerOffset + cursor - idxStart) == a) continue = false else cursor += 1
            // buffer not find
            if (continue) { idxStart += len; idx += 1 }
        }
        if (continue) -1 else cursor - from
    }

    private def bytesBefore1ignoreCase(a: Byte, from: Int, to: Int): Int = {
        val (start, offset) = offsetAtOffset(from)
        val end         = if (to == widx) size else { val tp = offsetAtOffset(to); if (tp._2 > 0) tp._1 + 1 else tp._1 }
        var cursor: Int = from
        var continue: Boolean = true
        var idx               = start
        var idxStart          = from - offset
        while (continue && idx < end) {
            val buffer     = apply(idx)
            val len        = buffer.readableBytes
            val byteBuffer = buffer.underlying
            while (continue && cursor < idxStart + len && cursor < to)
                if (ignoreCaseEqual(byteBuffer.get(buffer.readerOffset + cursor - idxStart), a)) continue = false
                else cursor += 1
            // buffer not find
            if (continue) { idxStart += len; idx += 1 }
        }
        if (continue) -1 else cursor - from
    }

    override def bytesBeforeIn(set: Array[Byte]): Int = {
        var cursor: Int       = ridx
        var continue: Boolean = true
        var idx               = 0
        var idxStart          = ridx
        while (continue && idx < size) {
            val buffer     = apply(idx)
            val len        = buffer.readableBytes
            val byteBuffer = buffer.underlying
            while (continue && cursor < idxStart + len)
                if (set.contains(byteBuffer.get(buffer.readerOffset + cursor - idxStart))) continue = false
                else cursor += 1
            if (continue) { // buffer not find
                idxStart += len
                idx += 1
            }
        }
        if (continue) -1 else cursor - ridx
    }

    override def bytesBeforeInRange(lower: Byte, upper: Byte): Int = {
        var cursor: Int       = ridx
        var continue: Boolean = true
        var idx               = 0
        var idxStart          = ridx
        while (continue && idx < size) {
            val buffer     = apply(idx)
            val len        = buffer.readableBytes
            val byteBuffer = buffer.underlying
            while (continue && cursor < idxStart + len) {
                val b = byteBuffer.get(buffer.readerOffset + cursor - idxStart)
                if (b >= lower && b <= upper) continue = false else cursor += 1
            }
            if (continue) { // buffer not find
                idxStart += len
                idx += 1
            }
        }
        if (continue) -1 else cursor - ridx
    }

    override def bytesBefore(needle1: Byte, needle2: Byte): Int = if (readableBytes >= 2) {
        var cursor: Int       = ridx + 1
        var continue: Boolean = true
        var idx               = 0
        var idxStart          = ridx

        var b1: Byte = 0
        var b2: Byte = head.underlying.get(head.readerOffset)
        while (continue && idx < size) {
            val buffer     = apply(idx)
            val len        = buffer.readableBytes
            val byteBuffer = buffer.underlying

            while (continue && cursor < idxStart + len) {
                b1 = b2
                b2 = byteBuffer.get(buffer.readerOffset + cursor - idxStart)
                if (b1 == needle1 && b2 == needle2) continue = false else cursor += 1
            }
            if (continue) {
                idxStart += len
                idx += 1
            }
        }
        if (continue) -1 else cursor - ridx - 1
    } else -1

    private def bytesBefore2(a1: Byte, a2: Byte, from: Int, to: Int): Int = {
        val (start, offset) = offsetAtOffset(from)
        val end         = if (to == widx) size else { val tp = offsetAtOffset(to); if (tp._2 > 0) tp._1 + 1 else tp._1 }
        var cursor: Int = from
        var continue: Boolean = true
        var idx               = start
        var idxStart          = from - offset

        var b1: Byte = 0
        var b2: Byte = 0
        while (continue && idx < end) {
            val buffer     = apply(idx)
            val len        = buffer.readableBytes
            val byteBuffer = buffer.underlying
            while (continue && cursor < idxStart + len && cursor < to) {
                b1 = b2
                b2 = byteBuffer.get(buffer.readerOffset + cursor - idxStart)
                if (cursor > from + 1 && b1 == a1 && b2 == a2) continue = false else cursor += 1
            }
            // buffer not find
            if (continue) { idxStart += len; idx += 1 }
        }
        if (continue) -1 else cursor - 1 - from
    }

    private def bytesBefore2ignoreCase(a1: Byte, a2: Byte, from: Int, to: Int): Int = {
        val (start, offset) = offsetAtOffset(from)
        val end =
            if (to == widx) size
            else {
                val tp = offsetAtOffset(to); if (tp._2 > 0) tp._1 + 1 else tp._1
            }
        var cursor: Int       = from
        var continue: Boolean = true
        var idx               = start
        var idxStart          = from - offset

        var b1: Byte = 0
        var b2: Byte = 0
        while (continue && idx < end) {
            val buffer     = apply(idx)
            val len        = buffer.readableBytes
            val byteBuffer = buffer.underlying
            while (continue && cursor < idxStart + len && cursor < to) {
                b1 = b2
                b2 = byteBuffer.get(buffer.readerOffset + cursor - idxStart)
                if (cursor > from + 1 && ignoreCaseEqual(b1, a1) && ignoreCaseEqual(b2, a2)) continue = false
                else cursor += 1
            }
            // buffer not find
            if (continue) { idxStart += len; idx += 1 }
        }
        if (continue) -1 else cursor - 1 - from
    }

    override def bytesBefore(needle1: Byte, needle2: Byte, needle3: Byte): Int = if (readableBytes >= 3) {
        var cursor: Int       = ridx
        var continue: Boolean = true
        var idx               = 0
        var idxStart          = ridx

        var b1: Byte = 0; var b2: Byte = 0; var b3: Byte = 0
        while (continue && idx < size) {
            val buffer     = apply(idx)
            val len        = buffer.readableBytes
            val byteBuffer = buffer.underlying

            while (continue && cursor < idxStart + len) {
                b1 = b2; b2 = b3
                b3 = byteBuffer.get(buffer.readerOffset + cursor - idxStart)
                if (b1 == needle1 && b2 == needle2 && b3 == needle3 && cursor - ridx > 2) continue = false
                else cursor += 1
            }
            if (continue) {
                idxStart += len
                idx += 1
            }
        }
        if (continue) -1 else cursor - ridx - 2
    } else -1

    private def bytesBefore3(a1: Byte, a2: Byte, a3: Byte, from: Int, to: Int): Int = {
        val (start, offset) = offsetAtOffset(from)
        val end =
            if (to == widx) size
            else {
                val tp = offsetAtOffset(to); if (tp._2 > 0) tp._1 + 1 else tp._1
            }
        var cursor: Int       = from
        var continue: Boolean = true
        var idx               = start
        var idxStart          = from - offset

        var b1: Byte = 0; var b2: Byte = 0; var b3: Byte = 0
        while (continue && idx < end) {
            val buffer     = apply(idx)
            val len        = buffer.readableBytes
            val byteBuffer = buffer.underlying
            while (continue && cursor < idxStart + len && cursor < to) {
                b1 = b2; b2 = b3
                b3 = byteBuffer.get(buffer.readerOffset + cursor - idxStart)
                if (cursor > from + 2 && b1 == a1 && b2 == a2 && b3 == a3) continue = false else cursor += 1
            }
            // buffer not find
            if (continue) { idxStart += len; idx += 1 }
        }
        if (continue) -1 else cursor - 2 - from
    }

    private def bytesBefore3ignoreCase(a1: Byte, a2: Byte, a3: Byte, from: Int, to: Int): Int = {
        val (start, offset) = offsetAtOffset(from)
        val end =
            if (to == widx) size
            else {
                val tp = offsetAtOffset(to);
                if (tp._2 > 0) tp._1 + 1 else tp._1
            }
        var cursor: Int       = from
        var continue: Boolean = true
        var idx               = start
        var idxStart          = from - offset

        var b1: Byte = 0; var b2: Byte = 0; var b3: Byte = 0
        while (continue && idx < end) {
            val buffer     = apply(idx)
            val len        = buffer.readableBytes
            val byteBuffer = buffer.underlying
            while (continue && cursor < idxStart + len && cursor < to) {
                b1 = b2; b2 = b3
                b3 = byteBuffer.get(buffer.readerOffset + cursor - idxStart)
                if (cursor > from + 2 && ignoreCaseEqual(b1, a1) && ignoreCaseEqual(b2, a2) && ignoreCaseEqual(b3, a3))
                    continue = false
                else cursor += 1
            }
            // buffer not find
            if (continue) {
                idxStart += len; idx += 1
            }
        }
        if (continue) -1 else cursor - 2 - from
    }

    override def bytesBefore(needle1: Byte, needle2: Byte, needle3: Byte, needle4: Byte): Int =
        if (readableBytes >= 4) {
            var cursor: Int       = ridx
            var continue: Boolean = true
            var idx               = 0
            var idxStart          = ridx

            var b1: Byte = 0; var b2: Byte = 0; var b3: Byte = 0; var b4: Byte = 0
            while (continue && idx < size) {
                val buffer     = apply(idx)
                val len        = buffer.readableBytes
                val byteBuffer = buffer.underlying

                while (continue && cursor < idxStart + len) {
                    b1 = b2; b2 = b3; b3 = b4
                    b4 = byteBuffer.get(buffer.readerOffset + cursor - idxStart)
                    if (b1 == needle1 && b2 == needle2 && b3 == needle3 && b4 == needle4 && cursor - ridx > 3)
                        continue = false
                    else cursor += 1
                }
                if (continue) {
                    idxStart += len
                    idx += 1
                }
            }
            if (continue) -1 else cursor - ridx - 3
        } else -1

    private def bytesBefore4(a1: Byte, a2: Byte, a3: Byte, a4: Byte, from: Int, to: Int): Int = {
        val (start, offset) = offsetAtOffset(from)
        val end =
            if (to == widx) size
            else {
                val tp = offsetAtOffset(to);
                if (tp._2 > 0) tp._1 + 1 else tp._1
            }
        var cursor: Int       = from
        var continue: Boolean = true
        var idx               = start
        var idxStart          = from - offset

        var b1: Byte = 0; var b2: Byte = 0; var b3: Byte = 0; var b4: Byte = 0
        while (continue && idx < end) {
            val buffer     = apply(idx)
            val len        = buffer.readableBytes
            val byteBuffer = buffer.underlying
            while (continue && cursor < idxStart + len && cursor < to) {
                b1 = b2; b2 = b3; b3 = b4
                b4 = byteBuffer.get(buffer.readerOffset + cursor - idxStart)
                if (cursor > from + 3 && b1 == a1 && b2 == a2 && b3 == a3 && b4 == a4) continue = false else cursor += 1
            }
            // buffer not find
            if (continue) { idxStart += len; idx += 1 }
        }
        if (continue) -1 else cursor - 3 - from
    }

    private def bytesBefore4ignoreCase(a1: Byte, a2: Byte, a3: Byte, a4: Byte, from: Int, to: Int): Int = {
        val (start, offset) = offsetAtOffset(from)
        val end =
            if (to == widx) size
            else {
                val tp = offsetAtOffset(to);
                if (tp._2 > 0) tp._1 + 1 else tp._1
            }
        var cursor: Int       = from
        var continue: Boolean = true
        var idx               = start
        var idxStart          = from - offset

        var b1: Byte = 0; var b2: Byte = 0; var b3: Byte = 0; var b4: Byte = 0
        while (continue && idx < end) {
            val buffer     = apply(idx)
            val len        = buffer.readableBytes
            val byteBuffer = buffer.underlying
            while (continue && cursor < idxStart + len && cursor < to) {
                b1 = b2; b2 = b3; b3 = b4
                b4 = byteBuffer.get(buffer.readerOffset + cursor - idxStart)
                if (
                  cursor > from + 3 && ignoreCaseEqual(b1, a1) && ignoreCaseEqual(b2, a2) && ignoreCaseEqual(b3, a3) &&
                  ignoreCaseEqual(b4, a4)
                ) continue = false
                else cursor += 1
            }
            // buffer not find
            if (continue) { idxStart += len; idx += 1 }
        }
        if (continue) -1 else cursor - 3 - from
    }

    private def bytesBefore5(b1: Byte, b2: Byte, b3: Byte, b4: Byte, b5: Byte): Int =
        if (readableBytes >= 5) {
            var cursor: Int       = ridx
            var continue: Boolean = true
            var idx               = 0
            var idxStart          = ridx

            var a1: Byte = 0; var a2: Byte = 0; var a3: Byte = 0; var a4: Byte = 0; var a5: Byte = 0
            while (continue && idx < size) {
                val buffer     = apply(idx)
                val len        = buffer.readableBytes
                val byteBuffer = buffer.underlying

                while (continue && cursor < idxStart + len) {
                    a1 = a2; a2 = a3; a3 = a4; a4 = a5
                    a5 = byteBuffer.get(buffer.readerOffset + cursor - idxStart)
                    if (b1 == a1 && b2 == a2 && b3 == a3 && b4 == a4 && b5 == a5 && cursor - ridx > 4)
                        continue = false
                    else cursor += 1
                }
                if (continue) {
                    idxStart += len
                    idx += 1
                }
            }
            if (continue) -1 else cursor - ridx - 4
        } else -1

    private def bytesBefore6(b1: Byte, b2: Byte, b3: Byte, b4: Byte, b5: Byte, b6: Byte): Int =
        if (readableBytes >= 6) {
            var cursor: Int       = ridx
            var continue: Boolean = true
            var idx               = 0
            var idxStart          = ridx

            var a1: Byte = 0; var a2: Byte = 0; var a3: Byte = 0; var a4: Byte = 0; var a5: Byte = 0; var a6: Byte = 0
            while (continue && idx < size) {
                val buffer     = apply(idx)
                val len        = buffer.readableBytes
                val byteBuffer = buffer.underlying

                while (continue && cursor < idxStart + len) {
                    a1 = a2; a2 = a3; a3 = a4; a4 = a5; a5 = a6
                    a6 = byteBuffer.get(buffer.readerOffset + cursor - idxStart)
                    if (b1 == a1 && b2 == a2 && b3 == a3 && b4 == a4 && b5 == a5 && b6 == a6 && cursor - ridx > 5)
                        continue = false
                    else cursor += 1
                }
                if (continue) {
                    idxStart += len
                    idx += 1
                }
            }
            if (continue) -1 else cursor - ridx - 5
        } else -1

    private def bytesBefore7(b1: Byte, b2: Byte, b3: Byte, b4: Byte, b5: Byte, b6: Byte, b7: Byte): Int =
        if (readableBytes > 6) {
            var cursor: Int       = ridx
            var continue: Boolean = true
            var idx               = 0
            var idxStart          = ridx

            var a1: Byte = 0; var a2: Byte = 0; var a3: Byte = 0; var a4: Byte = 0; var a5: Byte = 0; var a6: Byte = 0
            var a7: Byte = 0
            while (continue && idx < size) {
                val buffer     = apply(idx)
                val len        = buffer.readableBytes
                val byteBuffer = buffer.underlying

                while (continue && cursor < idxStart + len) {
                    a1 = a2; a2 = a3; a3 = a4; a4 = a5; a5 = a6; a6 = a7
                    a7 = byteBuffer.get(buffer.readerOffset + cursor - idxStart)
                    if (
                      b1 == a1 && b2 == a2 && b3 == a3 && b4 == a4 && b5 == a5 && b6 == a6 && b7 == a7 &&
                      cursor - ridx > 6
                    ) continue = false
                    else cursor += 1
                }
                if (continue) {
                    idxStart += len
                    idx += 1
                }
            }
            if (continue) -1 else cursor - ridx - 6
        } else -1

    private def bytesBefore8(b1: Byte, b2: Byte, b3: Byte, b4: Byte, b5: Byte, b6: Byte, b7: Byte, b8: Byte): Int =
        if (readableBytes > 7) {
            var cursor: Int       = ridx
            var continue: Boolean = true
            var idx               = 0
            var idxStart          = ridx

            var a1: Byte = 0; var a2: Byte = 0; var a3: Byte = 0; var a4: Byte = 0; var a5: Byte = 0; var a6: Byte = 0
            var a7: Byte = 0; var a8: Byte = 0
            while (continue && idx < size) {
                val buffer     = apply(idx)
                val len        = buffer.readableBytes
                val byteBuffer = buffer.underlying

                while (continue && cursor < idxStart + len) {
                    a1 = a2; a2 = a3; a3 = a4; a4 = a5; a5 = a6; a6 = a7; a7 = a8
                    a8 = byteBuffer.get(buffer.readerOffset + cursor - idxStart)
                    if (
                      b1 == a1 && b2 == a2 && b3 == a3 && b4 == a4 && b5 == a5 && b6 == a6 && b7 == a7 &&
                      b8 == a8 && cursor - ridx > 7
                    ) continue = false
                    else cursor += 1
                }
                if (continue) {
                    idxStart += len
                    idx += 1
                }
            }
            if (continue) -1 else cursor - ridx - 7
        } else -1

    // format: off
    private def bytesBefore9(b1: Byte, b2: Byte, b3: Byte, b4: Byte, b5: Byte, b6: Byte, b7: Byte, b8: Byte,
                              b9: Byte): Int =
    // format: on
        if (readableBytes > 8) {
            var cursor: Int       = ridx
            var continue: Boolean = true
            var idx               = 0
            var idxStart          = ridx

            var a1: Byte = 0; var a2: Byte = 0; var a3: Byte = 0; var a4: Byte = 0; var a5: Byte = 0; var a6: Byte = 0
            var a7: Byte = 0; var a8: Byte = 0; var a9: Byte = 0
            while (continue && idx < size) {
                val buffer     = apply(idx)
                val len        = buffer.readableBytes
                val byteBuffer = buffer.underlying

                while (continue && cursor < idxStart + len) {
                    a1 = a2; a2 = a3; a3 = a4; a4 = a5; a5 = a6; a6 = a7; a7 = a8; a8 = a9
                    a9 = byteBuffer.get(buffer.readerOffset + cursor - idxStart)
                    if (
                      b1 == a1 && b2 == a2 && b3 == a3 && b4 == a4 && b5 == a5 && b6 == a6 && b7 == a7 &&
                      b8 == a8 && b9 == a9 && cursor - ridx > 8
                    ) continue = false
                    else cursor += 1
                }
                if (continue) {
                    idxStart += len
                    idx += 1
                }
            }
            if (continue) -1 else cursor - ridx - 8
        } else -1

    // format: off
    private def bytesBefore10(b1: Byte, b2: Byte, b3: Byte, b4: Byte, b5: Byte, b6: Byte, b7: Byte, b8: Byte,
                               b9: Byte, b10: Byte): Int =
    // format: on
        if (readableBytes > 9) {
            var cursor: Int       = ridx
            var continue: Boolean = true
            var idx               = 0
            var idxStart          = ridx

            var a1: Byte = 0; var a2: Byte = 0; var a3: Byte = 0; var a4: Byte  = 0; var a5: Byte = 0; var a6: Byte = 0
            var a7: Byte = 0; var a8: Byte = 0; var a9: Byte = 0; var a10: Byte = 0
            while (continue && idx < size) {
                val buffer     = apply(idx)
                val len        = buffer.readableBytes
                val byteBuffer = buffer.underlying

                while (continue && cursor < idxStart + len) {
                    a1 = a2; a2 = a3; a3 = a4; a4 = a5; a5 = a6; a6 = a7; a7 = a8; a8 = a9; a9 = a10
                    a10 = byteBuffer.get(buffer.readerOffset + cursor - idxStart)
                    if (
                      b1 == a1 && b2 == a2 && b3 == a3 && b4 == a4 && b5 == a5 && b6 == a6 && b7 == a7 &&
                      b8 == a8 && b9 == a9 && b10 == a10 && cursor - ridx > 9
                    ) continue = false
                    else cursor += 1
                }
                if (continue) {
                    idxStart += len
                    idx += 1
                }
            }
            if (continue) -1 else cursor - ridx - 9
        } else -1

    // format: off
    private def bytesBefore11(b1: Byte, b2: Byte, b3: Byte, b4: Byte, b5: Byte, b6: Byte, b7: Byte, b8: Byte,
                               b9: Byte, b10: Byte, b11: Byte): Int =
    // format: on
        if (readableBytes > 10) {
            var cursor: Int       = ridx
            var continue: Boolean = true
            var idx               = 0
            var idxStart          = ridx

            var a1: Byte = 0; var a2: Byte = 0; var a3: Byte = 0; var a4: Byte  = 0; var a5: Byte  = 0; var a6: Byte = 0
            var a7: Byte = 0; var a8: Byte = 0; var a9: Byte = 0; var a10: Byte = 0; var a11: Byte = 0
            while (continue && idx < size) {
                val buffer     = apply(idx)
                val len        = buffer.readableBytes
                val byteBuffer = buffer.underlying

                while (continue && cursor < idxStart + len) {
                    a1 = a2; a2 = a3; a3 = a4; a4 = a5; a5 = a6; a6 = a7; a7 = a8; a8 = a9; a9 = a10; a10 = a11
                    a11 = byteBuffer.get(buffer.readerOffset + cursor - idxStart)
                    if (
                      b1 == a1 && b2 == a2 && b3 == a3 && b4 == a4 && b5 == a5 && b6 == a6 && b7 == a7 &&
                      b8 == a8 && b9 == a9 && b10 == a10 && b11 == a11 && cursor - ridx > 10
                    ) continue = false
                    else cursor += 1
                }
                if (continue) {
                    idxStart += len
                    idx += 1
                }
            }
            if (continue) -1 else cursor - ridx - 10
        } else -1

    // format: off
    private def bytesBefore12(b1: Byte, b2: Byte, b3: Byte, b4: Byte, b5: Byte, b6: Byte, b7: Byte, b8: Byte,
                               b9: Byte, b10: Byte, b11: Byte, b12: Byte): Int =
    // format: on
        if (readableBytes > 11) {
            var cursor: Int       = ridx
            var continue: Boolean = true
            var idx               = 0
            var idxStart          = ridx

            var a1: Byte = 0; var a2: Byte = 0; var a3: Byte = 0; var a4: Byte  = 0; var a5: Byte  = 0; var a6: Byte = 0
            var a7: Byte = 0; var a8: Byte = 0; var a9: Byte = 0; var a10: Byte = 0; var a11: Byte = 0;
            var a12: Byte = 0
            while (continue && idx < size) {
                val buffer     = apply(idx)
                val len        = buffer.readableBytes
                val byteBuffer = buffer.underlying

                while (continue && cursor < idxStart + len) {
                    a1 = a2; a2 = a3; a3 = a4; a4 = a5; a5 = a6; a6 = a7; a7 = a8; a8 = a9; a9 = a10; a10 = a11
                    a11 = a12
                    a12 = byteBuffer.get(buffer.readerOffset + cursor - idxStart)
                    if (
                      b1 == a1 && b2 == a2 && b3 == a3 && b4 == a4 && b5 == a5 && b6 == a6 && b7 == a7 &&
                      b8 == a8 && b9 == a9 && b10 == a10 && b11 == a11 && b12 == a12 && cursor - ridx > 11
                    ) continue = false
                    else cursor += 1
                }
                if (continue) {
                    idxStart += len
                    idx += 1
                }
            }
            if (continue) -1 else cursor - ridx - 11
        } else -1

    // format: off
    private def bytesBefore13(b1: Byte, b2: Byte, b3: Byte, b4: Byte, b5: Byte, b6: Byte, b7: Byte, b8: Byte,
                               b9: Byte, b10: Byte, b11: Byte, b12: Byte, b13: Byte): Int =
    // format: on
        if (readableBytes > 12) {
            var cursor: Int       = ridx
            var continue: Boolean = true
            var idx               = 0
            var idxStart          = ridx

            var a1: Byte = 0; var a2: Byte = 0; var a3: Byte = 0; var a4: Byte  = 0; var a5: Byte  = 0; var a6: Byte = 0
            var a7: Byte = 0; var a8: Byte = 0; var a9: Byte = 0; var a10: Byte = 0; var a11: Byte = 0
            var a12: Byte = 0; var a13: Byte = 0
            while (continue && idx < size) {
                val buffer     = apply(idx)
                val len        = buffer.readableBytes
                val byteBuffer = buffer.underlying

                while (continue && cursor < idxStart + len) {
                    a1 = a2; a2 = a3; a3 = a4; a4 = a5; a5 = a6; a6 = a7; a7 = a8; a8 = a9; a9 = a10; a10 = a11
                    a11 = a12; a12 = a13
                    a13 = byteBuffer.get(buffer.readerOffset + cursor - idxStart)
                    if (
                      b1 == a1 && b2 == a2 && b3 == a3 && b4 == a4 && b5 == a5 && b6 == a6 && b7 == a7 &&
                      b8 == a8 && b9 == a9 && b10 == a10 && b11 == a11 && b12 == a12 && b13 == a13 &&
                      cursor - ridx > 12
                    ) continue = false
                    else cursor += 1
                }
                if (continue) {
                    idxStart += len
                    idx += 1
                }
            }
            if (continue) -1 else cursor - ridx - 12
        } else -1

    // format: off
    private def bytesBefore14(b1: Byte, b2: Byte, b3: Byte, b4: Byte, b5: Byte, b6: Byte, b7: Byte, b8: Byte,
                               b9: Byte, b10: Byte, b11: Byte, b12: Byte, b13: Byte, b14: Byte): Int =
    // format: on
        if (readableBytes > 13) {
            var cursor: Int       = ridx
            var continue: Boolean = true
            var idx               = 0
            var idxStart          = ridx

            var a1: Byte = 0; var a2: Byte = 0; var a3: Byte = 0; var a4: Byte  = 0; var a5: Byte  = 0; var a6: Byte = 0
            var a7: Byte = 0; var a8: Byte = 0; var a9: Byte = 0; var a10: Byte = 0; var a11: Byte = 0
            var a12: Byte = 0; var a13: Byte = 0; var a14: Byte = 0
            while (continue && idx < size) {
                val buffer     = apply(idx)
                val len        = buffer.readableBytes
                val byteBuffer = buffer.underlying

                while (continue && cursor < idxStart + len) {
                    a1 = a2; a2 = a3; a3 = a4; a4 = a5; a5 = a6; a6 = a7; a7 = a8; a8 = a9; a9 = a10; a10 = a11
                    a11 = a12; a12 = a13; a13 = a14
                    a14 = byteBuffer.get(buffer.readerOffset + cursor - idxStart)
                    if (
                      b1 == a1 && b2 == a2 && b3 == a3 && b4 == a4 && b5 == a5 && b6 == a6 && b7 == a7 &&
                      b8 == a8 && b9 == a9 && b10 == a10 && b11 == a11 && b12 == a12 && b13 == a13 &&
                      b14 == a14 && cursor - ridx > 13
                    ) continue = false
                    else cursor += 1
                }
                if (continue) {
                    idxStart += len
                    idx += 1
                }
            }
            if (continue) -1 else cursor - ridx - 13
        } else -1

    // format: off
    private def bytesBefore15(b1: Byte, b2: Byte, b3: Byte, b4: Byte, b5: Byte, b6: Byte, b7: Byte, b8: Byte,
                               b9: Byte, b10: Byte, b11: Byte, b12: Byte, b13: Byte, b14: Byte, b15: Byte): Int =
    // format: on
        if (readableBytes > 14) {
            var cursor: Int       = ridx
            var continue: Boolean = true
            var idx               = 0
            var idxStart          = ridx

            var a1: Byte = 0; var a2: Byte = 0; var a3: Byte = 0; var a4: Byte  = 0; var a5: Byte  = 0; var a6: Byte = 0
            var a7: Byte = 0; var a8: Byte = 0; var a9: Byte = 0; var a10: Byte = 0; var a11: Byte = 0
            var a12: Byte = 0; var a13: Byte = 0; var a14: Byte = 0; var a15: Byte = 0
            while (continue && idx < size) {
                val buffer     = apply(idx)
                val len        = buffer.readableBytes
                val byteBuffer = buffer.underlying

                while (continue && cursor < idxStart + len) {
                    a1 = a2; a2 = a3; a3 = a4; a4 = a5; a5 = a6; a6 = a7; a7 = a8; a8 = a9; a9 = a10; a10 = a11
                    a11 = a12; a12 = a13; a13 = a14; a14 = a15
                    a15 = byteBuffer.get(buffer.readerOffset + cursor - idxStart)
                    if (
                      b1 == a1 && b2 == a2 && b3 == a3 && b4 == a4 && b5 == a5 && b6 == a6 && b7 == a7 &&
                      b8 == a8 && b9 == a9 && b10 == a10 && b11 == a11 && b12 == a12 && b13 == a13 &&
                      b14 == a14 && b15 == a15 && cursor - ridx > 14
                    ) continue = false
                    else cursor += 1
                }
                if (continue) {
                    idxStart += len
                    idx += 1
                }
            }
            if (continue) -1 else cursor - ridx - 14
        } else -1

    // format: off
    private def bytesBefore16(b1: Byte, b2: Byte, b3: Byte, b4: Byte, b5: Byte, b6: Byte, b7: Byte, b8: Byte,
                               b9: Byte, b10: Byte, b11: Byte, b12: Byte, b13: Byte, b14: Byte, b15: Byte, b16: Byte): Int =
    // format: on
        if (readableBytes > 15) {
            var cursor: Int       = ridx
            var continue: Boolean = true
            var idx               = 0
            var idxStart          = ridx

            var a1: Byte = 0; var a2: Byte = 0; var a3: Byte = 0; var a4: Byte  = 0; var a5: Byte  = 0; var a6: Byte = 0
            var a7: Byte = 0; var a8: Byte = 0; var a9: Byte = 0; var a10: Byte = 0; var a11: Byte = 0
            var a12: Byte = 0; var a13: Byte = 0; var a14: Byte = 0; var a15: Byte = 0; var a16: Byte = 0
            while (continue && idx < size) {
                val buffer     = apply(idx)
                val len        = buffer.readableBytes
                val byteBuffer = buffer.underlying

                while (continue && cursor < idxStart + len) {
                    a1 = a2; a2 = a3; a3 = a4; a4 = a5; a5 = a6; a6 = a7; a7 = a8; a8 = a9; a9 = a10; a10 = a11
                    a11 = a12; a12 = a13; a13 = a14; a14 = a15; a15 = a16
                    a16 = byteBuffer.get(buffer.readerOffset + cursor - idxStart)
                    if (
                      b1 == a1 && b2 == a2 && b3 == a3 && b4 == a4 && b5 == a5 && b6 == a6 && b7 == a7 &&
                      b8 == a8 && b9 == a9 && b10 == a10 && b11 == a11 && b12 == a12 && b13 == a13 &&
                      b14 == a14 && b15 == a15 && b16 == a16 && cursor - ridx > 15
                    ) continue = false
                    else cursor += 1
                }
                if (continue) {
                    idxStart += len
                    idx += 1
                }
            }
            if (continue) -1 else cursor - ridx - 15
        } else -1

    override def bytesBefore(needle: Array[Byte]): Int = if (readableBytes >= needle.length) {
        needle.length match
            case 1 => bytesBefore(needle(0))
            case 2 => bytesBefore(needle(0), needle(1))
            case 3 => bytesBefore(needle(0), needle(1), needle(2))
            case 4 => bytesBefore(needle(0), needle(1), needle(2), needle(3))
            case 5 => bytesBefore5(needle(0), needle(1), needle(2), needle(3), needle(4))
            case 6 => bytesBefore6(needle(0), needle(1), needle(2), needle(3), needle(4), needle(5))
            case 7 => bytesBefore7(needle(0), needle(1), needle(2), needle(3), needle(4), needle(5), needle(6))
            // format: off
            case 8 => bytesBefore8(needle(0), needle(1), needle(2), needle(3), needle(4), needle(5), needle(6), needle(7))
            case 9 => bytesBefore9(needle(0), needle(1), needle(2), needle(3), needle(4), needle(5), needle(6), needle(7),
                needle(8))
            case 10 => bytesBefore10(needle(0), needle(1), needle(2), needle(3), needle(4), needle(5), needle(6), needle(7),
                needle(8), needle(9))
            case 11 => bytesBefore11(needle(0), needle(1), needle(2), needle(3), needle(4), needle(5), needle(6), needle(7),
                needle(8), needle(9), needle(10))
            case 12 => bytesBefore12(needle(0), needle(1), needle(2), needle(3), needle(4), needle(5), needle(6), needle(7),
                needle(8), needle(9), needle(10), needle(11))
            case 13 => bytesBefore13(needle(0), needle(1), needle(2), needle(3), needle(4), needle(5), needle(6), needle(7),
                needle(8), needle(9), needle(10), needle(11), needle(12))
            case 14 => bytesBefore14(needle(0), needle(1), needle(2), needle(3), needle(4), needle(5), needle(6), needle(7),
                needle(8), needle(9), needle(10), needle(11), needle(12), needle(13))
            case 15 => bytesBefore15(needle(0), needle(1), needle(2), needle(3), needle(4), needle(5), needle(6), needle(7),
                needle(8), needle(9), needle(10), needle(11), needle(12), needle(13), needle(14))
            case 16 => bytesBefore16(needle(0), needle(1), needle(2), needle(3), needle(4), needle(5), needle(6), needle(7),
                needle(8), needle(9), needle(10), needle(11), needle(12), needle(13), needle(14), needle(15))
            // format: on
            case _ => bytesBeforeBytes(needle, ridx, widx)
    } else -1

    private def bytesBeforeBytes(bts: Array[Byte], from: Int, to: Int): Int = {
        val length = bts.length
        val a1     = bts(0)
        val a      = BytesUtil.bytes4Int(a1, bts(1), bts(2), bts(3))

        val (start, offset) = offsetAtOffset(from)
        val end         = if (to == widx) size else { val tp = offsetAtOffset(to); if (tp._2 > 0) tp._1 + 1 else tp._1 }
        var cursor: Int = from
        var continue: Boolean = true
        var idx               = start
        var idxStart          = from - offset

        var copy: Array[Byte] = null
        while (continue && idx < end) {
            val buffer = apply(idx)
            val len    = buffer.readableBytes
            val bb     = buffer.underlying
            while (continue && cursor < idxStart + len && cursor < to - length) {
                val base = buffer.readerOffset + cursor - idxStart
                if (cursor + 4 < idxStart + len && (bb.getInt(base) != a)) cursor += 1 // four bytes prefix match
                else if (bb.get(base) != a1) cursor += 1                               // one byte prefix match
                else {
                    if (copy == null) copy = new Array[Byte](length)
                    this.copyInto(cursor, copy, 0, length)
                    var same = true
                    var i    = 1
                    while (same && i < length) {
                        same = same && (copy(i) == bts(i))
                        i += 1
                    }
                    if (same) continue = false else cursor += 1
                }
            }
            if (continue) { // buffer not find
                idxStart += len
                idx += 1
            }
        }
        if (continue) -1 else cursor - from
    }

    private def bytesBeforeBytesIgnoreCase(bts: Array[Byte], from: Int, to: Int): Int = {
        val length          = bts.length
        val a1              = bts(0); val a2 = bts(1); val a3 = bts(2); val a4 = bts(3)
        val (start, offset) = offsetAtOffset(from)
        val end         = if (to == widx) size else { val tp = offsetAtOffset(to); if (tp._2 > 0) tp._1 + 1 else tp._1 }
        var cursor: Int = from
        var continue: Boolean = true
        var idx               = start
        var idxStart          = from - offset

        var copy: Array[Byte] = null
        while (continue && idx < end) {
            val buffer = apply(idx)
            val len    = buffer.readableBytes
            val bb     = buffer.underlying
            while (continue && cursor < idxStart + len && cursor < to - length) {
                val base = buffer.readerOffset + cursor - idxStart
                if (
                  cursor + 4 < idxStart + len &&
                  (!ignoreCaseEqual(a1, bb.get(base)) || !ignoreCaseEqual(a2, bb.get(base + 1)) ||
                      !ignoreCaseEqual(a3, bb.get(base + 2)) || !ignoreCaseEqual(a4, bb.get(base + 3)))
                ) cursor += 1                                            // four bytes prefix match
                else if (!ignoreCaseEqual(bb.get(base), a1)) cursor += 1 // one byte prefix match
                else {
                    if (copy == null) copy = new Array[Byte](length)
                    this.copyInto(cursor, copy, 0, length)
                    var same = true
                    var i    = 1
                    while (same && i < length) {
                        same = same && ignoreCaseEqual(copy(i), bts(i))
                        i += 1
                    }
                    if (same) continue = false else cursor += 1
                }
            }
            if (continue) { // buffer not find
                idxStart += len
                idx += 1
            }
        }
        if (continue) -1 else cursor - from

    }

    override def bytesBefore(needle: Array[Byte], from: Int, to: Int, ignoreCase: Boolean): Int =
        if (to - from > needle.length) {
            checkFromTo(from, to)
            if (ignoreCase) {
                needle.length match
                    case 1 => bytesBefore1ignoreCase(needle(0), from, to)
                    case 2 => bytesBefore2ignoreCase(needle(0), needle(1), from, to)
                    case 3 => bytesBefore3ignoreCase(needle(0), needle(1), needle(2), from, to)
                    case 4 => bytesBefore4ignoreCase(needle(0), needle(1), needle(2), needle(3), from, to)
                    case _ => bytesBeforeBytesIgnoreCase(needle, from, to)
            } else {
                needle.length match
                    case 1 => bytesBefore1(needle(0), from, to)
                    case 2 => bytesBefore2(needle(0), needle(1), from, to)
                    case 3 => bytesBefore3(needle(0), needle(1), needle(2), from, to)
                    case 4 => bytesBefore4(needle(0), needle(1), needle(2), needle(3), from, to)
                    case _ => bytesBeforeBytes(needle, from, to)
            }
        } else -1

    override def openCursor(fromOffset: Int, length: Int): ByteCursor = {
        if (closed) throw new BufferClosedException()
        if (length < 0) throw new IndexOutOfBoundsException(s"The length cannot be negative: ${length}")
        if (fromOffset < ridx)
            throw new IndexOutOfBoundsException(
              s"The fromOffset = ${fromOffset} cannot be less than readerOffset = ${ridx}"
            )
        if (widx < fromOffset + length)
            throw new IndexOutOfBoundsException(
              s"The fromOffset + length is beyond the writerOffset of the buffer: fromOffset = ${fromOffset}, length = ${length}"
            )

        new ByteCursor {
            private var value: Byte = _
            private var read: Int   = 0

            private val (fromIndex, fromOff) = offsetAtOffset(fromOffset)

            private var currentComponent: Int = fromIndex
            private var currentBuffer: Buffer = apply(currentComponent)
            private var currentIdx: Int       = currentBuffer.readerOffset + fromOff

            override def readByte: Boolean = if (read <= length) {
                value = currentBuffer.getByte(currentIdx)
                read += 1
                currentIdx += 1
                if (currentIdx == currentBuffer.writerOffset) { // change to next component buffer
                    currentComponent += 1
                    currentBuffer = apply(currentComponent)
                    currentIdx = currentBuffer.readerOffset
                }
                true
            } else false

            override def getByte: Byte = value

            override def currentOffset: Int = ridx + read

            override def bytesLeft: Int = length - read
        }
    }

    override def openReverseCursor(fromOffset: Int, length: Int): ByteCursor = {
        if (closed) throw new BufferClosedException()
        if (length < 0) throw new IndexOutOfBoundsException(s"The length cannot be negative: ${length}")
        if (fromOffset < ridx)
            throw new IndexOutOfBoundsException(
              s"The fromOffset = ${fromOffset} cannot be less than readerOffset = ${ridx}"
            )
        if (fromOffset - length >= ridx)
            new IndexOutOfBoundsException(
              s"The fromOffset - length would underflow the readerOffset: fromOffset - length = ${fromOffset - length}, readerOffset = ${ridx}."
            )

        new ByteCursor {
            private var value: Byte = _
            private var read: Int   = 0

            private val (fromIndex, fromOff) = offsetAtOffset(fromOffset)

            private var currentComponent: Int = fromIndex
            private var currentBuffer: Buffer = apply(currentComponent)
            private var currentIdx: Int       = currentBuffer.readerOffset + fromOff

            override def readByte: Boolean = if (read <= length) {
                value = currentBuffer.getByte(currentIdx)
                read += 1
                currentIdx -= 1
                if (currentIdx == currentBuffer.readerOffset) { // change to pre component buffer
                    currentComponent -= 1
                    currentBuffer = apply(currentComponent)
                    currentIdx = currentBuffer.writerOffset
                }
                true
            } else false

            override def getByte: Byte = value

            override def currentOffset: Int = ridx + read

            override def bytesLeft: Int = length - read
        }
    }

    override def ensureWritable(size: Int, minimumGrowth: Int, allowCompaction: Boolean): Buffer = this

    override def close(): Unit = {
        recycleAll()
        stClosed = true
    }

    override def clean(): this.type = {
        recycleAll(true)
        this
    }

    override def closed: Boolean = stClosed

    override def readByte: Byte = {
        val value = head.readByte
        ridx += JByte.BYTES
        if (head.readableBytes == 0) recycleHead()
        value
    }

    override def getByte(index: Int): Byte = {
        val (idx, off) = offsetAtOffset(index)
        val buffer     = apply(idx)
        val len        = buffer.readableBytes - off
        if (len > 0) buffer.getByte(buffer.readerOffset + off)
        else {
            val next = apply(idx + 1)
            next.getByte(next.readerOffset)
        }
    }

    override def readUnsignedByte: Int = {
        val value = head.readUnsignedByte
        ridx += JByte.BYTES
        if (head.readableBytes == 0) recycleHead()
        value
    }

    override def getUnsignedByte(index: Int): Int = {
        val (idx, off) = offsetAtOffset(index)
        val buffer     = apply(idx)
        val len        = buffer.readableBytes - off
        if (len > 0) buffer.getUnsignedByte(buffer.readerOffset + off)
        else {
            val next = apply(idx + 1)
            next.getUnsignedByte(next.readerOffset)
        }
    }

    override def writeByte(value: Byte): Buffer = {
        if (realWritableBytes >= JByte.BYTES) {
            last.writeByte(value)
            widx += JByte.BYTES
        } else {
            extendBuffer()
            last.writeByte(value)
            widx += JByte.BYTES
        }
        this
    }

    override def setByte(index: Int, value: Byte): Buffer = {
        val (idx, off) = offsetAtOffset(index)
        val buffer     = apply(idx)
        val len        = buffer.readableBytes - off
        if (len > 0) buffer.setByte(buffer.readerOffset + off, value)
        else {
            val next = apply(idx + 1)
            next.setByte(next.readerOffset, value)
        }
        this
    }

    override def writeUnsignedByte(value: Int): Buffer = {
        if (realWritableBytes >= JByte.BYTES) {
            last.writeUnsignedByte(value)
            widx += JByte.BYTES
        } else {
            extendBuffer()
            last.writeUnsignedByte(value)
            widx += JByte.BYTES
        }
        this
    }

    override def setUnsignedByte(index: Int, value: Int): Buffer = {
        val (idx, off) = offsetAtOffset(index)
        val buffer     = apply(idx)
        val len        = buffer.readableBytes - off
        if (len > 0) buffer.setUnsignedByte(buffer.readerOffset + off, value)
        else {
            val next = apply(idx + 1)
            next.setUnsignedByte(next.readerOffset, value)
        }
        this
    }

    override def readChar: Char = {
        val headReadable = head.readableBytes
        if (headReadable > Character.BYTES) {
            val v = head.readChar
            ridx += Character.BYTES
            v
        } else if (headReadable == Character.BYTES) {
            val value = head.readChar
            ridx += Character.BYTES
            recycleHead()
            value
        } else {
            val v = getChar(ridx)
            this.readerOffset(ridx + Character.BYTES)
            v
        }
    }

    override def getChar(index: Int): Char = {
        val (idx, off) = offsetAtOffset(index)
        val buffer     = apply(idx)
        val len        = buffer.readableBytes - off
        if (len >= Character.BYTES) {
            buffer.getChar(buffer.readerOffset + off)
        } else if (len > 0) {
            val b1   = buffer.getByte(buffer.readerOffset + off)
            val next = apply(idx + 1)
            val b2   = next.getByte(next.readerOffset)
            ((b1 << 8) | (b2 & 0xff)).toShort.toChar
        } else {
            val next = apply(idx + 1)
            buffer.getChar(buffer.readerOffset)
        }
    }

    override def writeChar(value: Char): Buffer = {
        if (realWritableBytes >= Character.BYTES) {
            last.writeChar(value)
        } else {
            extendBuffer()
            last.writeChar(value)
        }
        widx += Character.BYTES
        this
    }

    override def setChar(index: Int, value: Char): Buffer = {
        val (idx, off) = offsetAtOffset(index)
        val buffer     = apply(idx)
        val len        = buffer.readableBytes - off
        if (len >= Character.BYTES) {
            buffer.setChar(buffer.readerOffset + off, value)
        } else if (len == 0) {
            val next = apply(idx + 1)
            next.setChar(next.readerOffset, value)
        } else { // len == 1
            val next = apply(idx + 1)
            buffer.setByte(buffer.readerOffset + off, (value.toShort >>> 8).toByte)
            next.setByte(next.readableBytes, value.toByte)
        }
        this
    }

    override def readShort: Short = {
        val headReadable = head.readableBytes
        if (headReadable > JShort.BYTES) {
            val v = head.readShort
            ridx += JShort.BYTES
            v
        } else if (headReadable == JShort.BYTES) {
            val value = head.readShort
            ridx += JShort.BYTES
            recycleHead()
            value
        } else {
            val v = getShort(ridx)
            this.readerOffset(ridx + JShort.BYTES)
            v
        }
    }

    override def writeShort(value: Short): Buffer = {
        if (realWritableBytes >= JShort.BYTES) {
            last.writeShort(value)
        } else {
            extendBuffer()
            last.writeShort(value)
        }
        widx += JShort.BYTES
        this
    }

    override def setShort(index: Int, value: Short): Buffer = {
        val (idx, off) = offsetAtOffset(index)
        val buffer     = apply(idx)
        val len        = buffer.readableBytes - off
        if (len >= JShort.BYTES) {
            buffer.setShort(buffer.readerOffset + off, value)
        } else if (len == 0) {
            val next = apply(idx + 1)
            next.setShort(next.readerOffset, value)
        } else {
            buffer.setByte(buffer.readerOffset + off, (value >>> 8).toByte)
            val next = apply(idx + 1)
            next.setByte(next.readableBytes, value.toByte)
        }
        this
    }

    override def getShort(index: Int): Short = {
        val (idx, off) = offsetAtOffset(index)
        val buffer     = apply(idx)
        val len        = buffer.readableBytes - off
        if (len >= JShort.BYTES) {
            buffer.getShort(buffer.readerOffset + off)
        } else if (len > 0) {
            val b1   = buffer.getByte(buffer.readerOffset + off)
            val next = apply(idx + 1)
            val b2   = next.getByte(next.readerOffset)
            ((b1 << 8) | (b2 & 0xff)).toShort
        } else {
            val next = apply(idx + 1)
            buffer.getShort(buffer.readerOffset)
        }
    }

    override def readUnsignedShort: Int = {
        val headReadable = head.readableBytes
        if (headReadable > JShort.BYTES) {
            val v = head.readUnsignedShort
            ridx += JShort.BYTES
            v
        } else if (headReadable == JShort.BYTES) {
            val value = head.readUnsignedShort
            ridx += JShort.BYTES
            recycleHead()
            value
        } else {
            val v = getUnsignedShort(ridx)
            this.readerOffset(ridx + JShort.BYTES)
            v
        }
    }

    override def getUnsignedShort(index: Int): Int = {
        val (idx, off) = offsetAtOffset(index)
        val buffer     = apply(idx)
        val len        = buffer.readableBytes - off
        if (len >= JShort.BYTES) {
            buffer.getUnsignedShort(buffer.readerOffset + off)
        } else if (len > 0) {
            val next = apply(idx + 1)
            val b1   = buffer.getByte(buffer.readerOffset + off)
            val b2   = next.getByte(next.readerOffset)
            ((b1 << 8) | (b2 & 0xff)) & 0xffff
        } else {
            val next = apply(idx + 1)
            buffer.getUnsignedShort(buffer.readerOffset)
        }
    }

    override def writeUnsignedShort(value: Int): Buffer = {
        if (realWritableBytes >= JShort.BYTES) {
            last.writeUnsignedShort(value)
        } else {
            extendBuffer()
            last.writeUnsignedShort(value)
        }
        widx += JShort.BYTES
        this
    }

    override def setUnsignedShort(index: Int, value: Int): Buffer = {
        val (idx, off) = offsetAtOffset(index)
        val buffer     = apply(idx)
        val len        = buffer.readableBytes - off
        if (len >= JShort.BYTES) {
            buffer.setUnsignedShort(buffer.readerOffset + off, value)
        } else if (len == 0) {
            val next = apply(idx + 1)
            next.setUnsignedShort(next.readerOffset, value)
        } else {
            val next = apply(idx + 1)
            buffer.setByte(buffer.readerOffset + off, (value >>> 8).toByte)
            next.setByte(next.readableBytes, value.toByte)
        }
        this
    }

    override def readMedium: Int = {
        val headReadable = head.readableBytes
        if (headReadable > 3) {
            val v = head.readMedium
            ridx += 3
            v
        } else if (headReadable == 3) {
            val value = head.readMedium
            ridx += 3
            recycleHead()
            value
        } else { // headReadable > 0
            val v = getMedium(ridx)
            this.readerOffset(ridx + 3)
            v
        }
    }

    override def writeMedium(value: Int): Buffer = {
        if (realWritableBytes >= 3) {
            last.writeMedium(value)
        } else {
            extendBuffer()
            last.writeMedium(value)
        }
        widx += 3
        this
    }

    override def getMedium(index: Int): Int = {
        val (idx, off) = offsetAtOffset(index)
        val buffer     = apply(idx)
        val len        = buffer.readableBytes - off
        if (len >= 3) {
            buffer.getMedium(buffer.readerOffset + off)
        } else if (len == 2) {
            val b1   = buffer.getByte(buffer.readerOffset + off)
            val b2   = buffer.getByte(buffer.readerOffset + off + 1)
            val next = apply(idx + 1)
            val b3   = next.getByte(next.readerOffset)
            b1 << 16 | (b2 & 0xff) << 8 | b3 & 0xff
        } else if (len == 1) {
            val b1   = buffer.getByte(buffer.readerOffset + off)
            val next = apply(idx + 1)
            val b2   = next.getByte(next.readerOffset)
            val b3   = next.getByte(next.readerOffset + 1)
            b1 << 16 | (b2 & 0xff) << 8 | b3 & 0xff
        } else {
            val next = apply(idx + 1)
            next.getMedium(next.readerOffset)
        }
    }

    override def setMedium(index: Int, value: Int): Buffer = {
        val (idx, off) = offsetAtOffset(index)
        val buffer     = apply(idx)
        val len        = buffer.readableBytes - off
        if (len >= 3) {
            buffer.setMedium(buffer.readerOffset + off, value)
        } else if (len == 2) {
            buffer.setByte(buffer.readerOffset + off, (value >> 16).toByte)
            buffer.setByte(buffer.readerOffset + off + 1, (value >> 8 & 0xff).toByte)
            val next = apply(idx + 1)
            next.setByte(next.readerOffset, (value & 0xff).toByte)
        } else if (len == 1) {
            buffer.setByte(buffer.readerOffset + off, (value >> 16).toByte)
            val next = apply(idx + 1)
            next.setByte(next.readerOffset, (value >> 8 & 0xff).toByte)
            next.setByte(next.readerOffset + 1, (value & 0xff).toByte)
        } else {
            val next = apply(idx + 1)
            next.setMedium(next.readerOffset, value)
        }
        this
    }

    override def readMediumLE: Int = {
        val headReadable = head.readableBytes
        if (headReadable > 3) {
            val v = head.readMediumLE
            ridx += 3
            v
        } else if (headReadable == 3) {
            val value = head.readMediumLE
            ridx += 3
            recycleHead()
            value
        } else { // headReadable > 0
            val v = getMediumLE(ridx)
            this.readerOffset(ridx + 3)
            v
        }
    }

    override def writeMediumLE(value: Int): Buffer = {
        if (realWritableBytes >= 3) {
            last.writeMediumLE(value)
        } else {
            extendBuffer()
            last.writeMediumLE(value)
        }
        widx += 3
        this
    }

    override def getMediumLE(index: Int): Int = {
        val (idx, off) = offsetAtOffset(index)
        val buffer     = apply(idx)
        val len        = buffer.readableBytes - off
        if (len >= 3) {
            buffer.getMediumLE(buffer.readerOffset + off)
        } else if (len == 2) {
            val b1   = buffer.getByte(buffer.readerOffset + off)
            val b2   = buffer.getByte(buffer.readerOffset + off + 1)
            val next = apply(idx + 1)
            val b3   = next.getByte(next.readerOffset)
            b3 << 16 | (b2 & 0xff) << 8 | b1 & 0xff
        } else if (len == 1) {
            val b1   = buffer.getByte(buffer.readerOffset + off)
            val next = apply(idx + 1)
            val b2   = next.getByte(next.readerOffset)
            val b3   = next.getByte(next.readerOffset + 1)
            b3 << 16 | (b2 & 0xff) << 8 | b1 & 0xff
        } else {
            val next = apply(idx + 1)
            next.getMediumLE(next.readerOffset)
        }
    }

    override def setMediumLE(index: Int, value: Int): Buffer = {
        val (idx, off) = offsetAtOffset(index)
        val buffer     = apply(idx)
        val len        = buffer.readableBytes - off
        if (len >= 3) {
            buffer.setMediumLE(buffer.readerOffset + off, value)
        } else if (len == 2) {
            buffer.setByte(buffer.readerOffset + off, (value & 0xff).toByte)
            buffer.setByte(buffer.readerOffset + off + 1, (value >> 8 & 0xff).toByte)
            val next = apply(idx + 1)
            next.setByte(next.readerOffset, (value >> 16).toByte)
        } else if (len == 1) {
            buffer.setByte(buffer.readerOffset + off, (value & 0xff).toByte)
            val next = apply(idx + 1)
            next.setByte(next.readerOffset, (value >> 8 & 0xff).toByte)
            next.setByte(next.readerOffset + 1, (value >> 16).toByte)
        } else {
            val next = apply(idx + 1)
            next.setMediumLE(next.readerOffset, value)
        }
        this
    }

    override def readUnsignedMedium: Int = {
        val headReadable = head.readableBytes
        if (headReadable > 3) {
            val v = head.readUnsignedMedium
            ridx += 3
            v
        } else if (headReadable == 3) {
            val value = head.readUnsignedMedium
            ridx += 3
            recycleHead()
            value
        } else { // headReadable == 1
            val v = getUnsignedMedium(ridx)
            this.readerOffset(ridx + 3)
            v
        }
    }

    override def writeUnsignedMedium(value: Int): Buffer = {
        if (realWritableBytes >= 3) {
            last.writeUnsignedMedium(value)
        } else {
            extendBuffer()
            last.writeUnsignedMedium(value)
        }
        widx += 3
        this
    }

    override def getUnsignedMedium(index: Int): Int = {
        val (idx, off) = offsetAtOffset(index)
        val buffer     = apply(idx)
        val len        = buffer.readableBytes - off
        if (len >= 3) {
            buffer.getUnsignedMedium(buffer.readerOffset + off)
        } else if (len == 2) {
            val b1   = buffer.getByte(buffer.readerOffset + off)
            val b2   = buffer.getByte(buffer.readerOffset + off + 1)
            val next = apply(idx + 1)
            val b3   = next.getByte(next.readerOffset)
            (b1 << 16 | (b2 & 0xff) << 8 | b3 & 0xff) & 0xffffff
        } else if (len == 1) {
            val b1   = buffer.getByte(buffer.readerOffset + off)
            val next = apply(idx + 1)
            val b2   = next.getByte(next.readerOffset)
            val b3   = next.getByte(next.readerOffset + 1)
            (b1 << 16 | (b2 & 0xff) << 8 | b3 & 0xff) & 0xffffff
        } else {
            val next = apply(idx + 1)
            next.getUnsignedMedium(next.readerOffset)
        }
    }

    override def setUnsignedMedium(index: Int, value: Int): Buffer = {
        val (idx, off) = offsetAtOffset(index)
        val buffer     = apply(idx)
        val len        = buffer.readableBytes - off
        if (len >= 3) {
            buffer.setUnsignedMedium(buffer.readerOffset + off, value)
        } else if (len == 2) {
            buffer.setByte(buffer.readerOffset + off, (value >> 16).toByte)
            buffer.setByte(buffer.readerOffset + off + 1, (value >> 8 & 0xff).toByte)
            val next = apply(idx + 1)
            next.setByte(next.readerOffset, (value & 0xff).toByte)
        } else if (len == 1) {
            buffer.setByte(buffer.readerOffset + off, (value >> 16).toByte)
            val next = apply(idx + 1)
            next.setByte(next.readerOffset, (value >> 8 & 0xff).toByte)
            next.setByte(next.readerOffset + 1, (value & 0xff).toByte)
        } else {
            val next = apply(idx + 1)
            next.setUnsignedMedium(next.readerOffset, value)
        }
        this
    }

    override def readUnsignedMediumLE: Int = {
        val headReadable = head.readableBytes
        if (headReadable > 3) {
            val v = head.readUnsignedMediumLE
            ridx += 3
            v
        } else if (headReadable == 3) {
            val value = head.readUnsignedMediumLE
            ridx += 3
            recycleHead()
            value
        } else { // headReadable == 1
            val v = getUnsignedMediumLE(ridx)
            this.readerOffset(ridx + 3)
            v
        }
    }

    override def getUnsignedMediumLE(index: Int): Int = {
        val (idx, off) = offsetAtOffset(index)
        val buffer     = apply(idx)
        val len        = buffer.readableBytes - off
        if (len >= 3) {
            buffer.getUnsignedMediumLE(buffer.readerOffset + off)
        } else if (len == 2) {
            val b1   = buffer.getByte(buffer.readerOffset + off)
            val b2   = buffer.getByte(buffer.readerOffset + off + 1)
            val next = apply(idx + 1)
            val b3   = next.getByte(next.readerOffset)
            (b3 << 16 | (b2 & 0xff) << 8 | b1 & 0xff) & 0xffffff
        } else if (len == 1) {
            val b1   = buffer.getByte(buffer.readerOffset + off)
            val next = apply(idx + 1)
            val b2   = next.getByte(next.readerOffset)
            val b3   = next.getByte(next.readerOffset + 1)
            (b3 << 16 | (b2 & 0xff) << 8 | b1 & 0xff) & 0xffffff
        } else {
            val next = apply(idx + 1)
            next.getUnsignedMediumLE(next.readerOffset)
        }
    }

    override def writeUnsignedMediumLE(value: Int): Buffer = {
        if (realWritableBytes >= 3) {
            last.writeUnsignedMediumLE(value)
        } else {
            extendBuffer()
            last.writeUnsignedMediumLE(value)
        }
        widx += 3
        this
    }

    override def setUnsignedMediumLE(index: Int, value: Int): Buffer = {
        val (idx, off) = offsetAtOffset(index)
        val buffer     = apply(idx)
        val len        = buffer.readableBytes - off
        if (len >= 3) {
            buffer.setUnsignedMediumLE(buffer.readerOffset + off, value)
        } else if (len == 2) {
            buffer.setByte(buffer.readerOffset + off, (value & 0xff).toByte)
            buffer.setByte(buffer.readerOffset + off + 1, (value >> 8 & 0xff).toByte)
            val next = apply(idx + 1)
            next.setByte(next.readerOffset, (value >> 16).toByte)
        } else if (len == 1) {
            buffer.setByte(buffer.readerOffset + off, (value & 0xff).toByte)
            val next = apply(idx + 1)
            next.setByte(next.readerOffset, (value >> 8 & 0xff).toByte)
            next.setByte(next.readerOffset + 1, (value >> 16).toByte)
        } else {
            val next = apply(idx + 1)
            next.setUnsignedMediumLE(next.readerOffset, value)
        }
        this
    }

    override def readInt: Int = {
        val headReadable = head.readableBytes
        if (headReadable > Integer.BYTES) {
            val v = head.readInt
            ridx += Integer.BYTES
            v
        } else if (headReadable == Integer.BYTES) {
            val value = head.readInt
            ridx += Integer.BYTES
            recycleHead()
            value
        } else {
            val v = getInt(ridx)
            this.readerOffset(ridx + Integer.BYTES)
            v
        }
    }

    override def getInt(index: Int): Int = {
        val startOff = index - ridx
        if (startOff + Integer.BYTES <= head.readableBytes) head.getInt(head.readerOffset + startOff)
        else {
            val (idx, off) = offsetAtOffset(index)
            val buffer     = apply(idx)
            val len        = buffer.readableBytes - off
            if (len >= Integer.BYTES) {
                buffer.getInt(buffer.readerOffset + off)
            } else if (len == 3) {
                val b1   = buffer.getByte(buffer.readerOffset + off)
                val b2   = buffer.getByte(buffer.readerOffset + off + 1)
                val b3   = buffer.getByte(buffer.readerOffset + off + 2)
                val next = apply(idx + 1)
                val b4   = next.getByte(next.readerOffset)
                b1 << 24 | (b2 & 0xff) << 16 | (b3 & 0xff) << 8 | b4 & 0xff
            } else if (len == 2) {
                val b1   = buffer.getByte(buffer.readerOffset + off)
                val b2   = buffer.getByte(buffer.readerOffset + off + 1)
                val next = apply(idx + 1)
                val b3   = next.getByte(next.readerOffset)
                val b4   = next.getByte(next.readerOffset + 1)
                b1 << 24 | (b2 & 0xff) << 16 | (b3 & 0xff) << 8 | b4 & 0xff
            } else if (len == 1) {
                val b1   = buffer.getByte(buffer.readerOffset + off)
                val next = apply(idx + 1)
                val b2   = next.getByte(next.readerOffset)
                val b3   = next.getByte(next.readerOffset + 1)
                val b4   = next.getByte(next.readerOffset + 2)
                b1 << 24 | (b2 & 0xff) << 16 | (b3 & 0xff) << 8 | b4 & 0xff
            } else {
                val next = apply(idx + 1)
                next.getInt(next.readerOffset)
            }
        }
    }

    override def writeInt(value: Int): Buffer = {
        if (realWritableBytes >= Integer.BYTES) {
            last.writeInt(value)
        } else {
            extendBuffer()
            last.writeInt(value)
        }
        widx += Integer.BYTES
        this
    }

    override def setInt(index: Int, value: Int): Buffer = {
        val (idx, off) = offsetAtOffset(index)
        val buffer     = apply(idx)
        val len        = buffer.readableBytes - off
        if (len >= Integer.BYTES) {
            buffer.setInt(buffer.readerOffset + off, value)
        } else if (len == 3) {
            buffer.setByte(buffer.readerOffset + off, (value >>> 0).toByte)
            buffer.setByte(buffer.readerOffset + off + 1, (value >>> 8).toByte)
            buffer.setByte(buffer.readerOffset + off + 2, (value >>> 16).toByte)
            val next = apply(idx + 1)
            next.setByte(next.readerOffset, (value >>> 24).toByte)
        } else if (len == 2) {
            buffer.setByte(buffer.readerOffset + off, (value >>> 0).toByte)
            buffer.setByte(buffer.readerOffset + off + 1, (value >>> 8).toByte)
            val next = apply(idx + 1)
            next.setByte(next.readerOffset, (value >>> 16).toByte)
            next.setByte(next.readerOffset + 1, (value >>> 24).toByte)
        } else if (len == 1) {
            buffer.setByte(buffer.readerOffset + off, (value >>> 0).toByte)
            val next = apply(idx + 1)
            next.setByte(next.readerOffset, (value >>> 8).toByte)
            next.setByte(next.readerOffset + 1, (value >>> 16).toByte)
            next.setByte(next.readerOffset + 2, (value >>> 24).toByte)
        } else {
            val next = apply(idx + 1)
            next.setInt(next.readerOffset, value)
        }
        this
    }

    override def readUnsignedInt: Long = {
        val headReadable = head.readableBytes
        val m = if (headReadable > Integer.BYTES) {
            head.readUnsignedInt
        } else if (headReadable == Integer.BYTES) {
            val value = head.readUnsignedInt
            recycleHead()
            value
        } else if (headReadable == 3) {
            val b1 = head.readByte
            val b2 = head.readByte
            val b3 = head.readByte
            recycleHead()
            val b4 = head.readByte
            (((b1 & 0xff) << 24) | (b2 & 0xff) << 16 | (b3 & 0xff) << 8 | (b4 & 0xff)) & 0xffffffffL
        } else if (headReadable == 2) {
            val b1 = head.readByte
            val b2 = head.readByte
            recycleHead()
            val b3 = head.readByte
            val b4 = head.readByte
            (((b1 & 0xff) << 24) | (b2 & 0xff) << 16 | (b3 & 0xff) << 8 | (b4 & 0xff)) & 0xffffffffL
        } else { // headReadable == 1
            val b1 = head.readByte
            recycleHead()
            val b2 = head.readByte
            val b3 = head.readByte
            val b4 = head.readByte
            (((b1 & 0xff) << 24) | (b2 & 0xff) << 16 | (b3 & 0xff) << 8 | (b4 & 0xff)) & 0xffffffffL
        }
        ridx += Integer.BYTES
        m
    }

    override def getUnsignedInt(index: Int): Long = {
        val (idx, off) = offsetAtOffset(index)
        val buffer     = apply(idx)
        val len        = buffer.readableBytes - off
        if (len >= Integer.BYTES) {
            buffer.getUnsignedInt(buffer.readerOffset + off)
        } else if (len == 3) {
            val b1   = buffer.getByte(buffer.readerOffset + off)
            val b2   = buffer.getByte(buffer.readerOffset + off + 1)
            val b3   = buffer.getByte(buffer.readerOffset + off + 2)
            val next = apply(idx + 1)
            val b4   = next.getByte(next.readerOffset)
            (((b1 & 0xff) << 24) | (b2 & 0xff) << 16 | (b3 & 0xff) << 8 | (b4 & 0xff)) & 0xffffffffL
        } else if (len == 2) {
            val b1   = buffer.getByte(buffer.readerOffset + off)
            val b2   = buffer.getByte(buffer.readerOffset + off + 1)
            val next = apply(idx + 1)
            val b3   = next.getByte(next.readerOffset)
            val b4   = next.getByte(next.readerOffset + 1)
            (((b1 & 0xff) << 24) | (b2 & 0xff) << 16 | (b3 & 0xff) << 8 | (b4 & 0xff)) & 0xffffffffL
        } else if (len == 1) {
            val b1   = buffer.getByte(buffer.readerOffset + off)
            val next = apply(idx + 1)
            val b2   = next.getByte(next.readerOffset)
            val b3   = next.getByte(next.readerOffset + 1)
            val b4   = next.getByte(next.readerOffset + 2)
            (((b1 & 0xff) << 24) | (b2 & 0xff) << 16 | (b3 & 0xff) << 8 | (b4 & 0xff)) & 0xffffffffL
        } else {
            val next = apply(idx + 1)
            next.getUnsignedInt(next.readerOffset)
        }
    }

    override def writeUnsignedInt(value: Long): Buffer = {
        if (realWritableBytes >= 3) {
            last.writeUnsignedInt(value)
        } else {
            extendBuffer()
            last.writeUnsignedInt(value)
        }
        widx += Integer.BYTES
        this
    }

    override def setUnsignedInt(index: Int, value: Long): Buffer = {
        val (idx, off) = offsetAtOffset(index)
        val buffer     = apply(idx)
        val len        = buffer.readableBytes - off
        if (len >= Integer.BYTES) {
            buffer.setUnsignedInt(buffer.readerOffset + off, value)
        } else if (len == 3) {
            val v = (value & 0xffffffffL).toInt
            buffer.setByte(buffer.readerOffset + off, (v >>> 0).toByte)
            buffer.setByte(buffer.readerOffset + off + 1, (v >>> 8).toByte)
            buffer.setByte(buffer.readerOffset + off + 2, (v >>> 16).toByte)
            val next = apply(idx + 1)
            next.setByte(next.readerOffset, (v >>> 24).toByte)
        } else if (len == 2) {
            val v = (value & 0xffffffffL).toInt
            buffer.setByte(buffer.readerOffset + off, (v >>> 0).toByte)
            buffer.setByte(buffer.readerOffset + off + 1, (v >>> 8).toByte)
            val next = apply(idx + 1)
            next.setByte(next.readerOffset, (v >>> 16).toByte)
            next.setByte(next.readerOffset + 1, (v >>> 24).toByte)
        } else if (len == 1) {
            val v = (value & 0xffffffffL).toInt
            buffer.setByte(buffer.readerOffset + off, (v >>> 0).toByte)
            val next = apply(idx + 1)
            next.setByte(next.readerOffset, (v >>> 8).toByte)
            next.setByte(next.readerOffset + 1, (v >>> 16).toByte)
            next.setByte(next.readerOffset + 2, (v >>> 24).toByte)
        } else {
            val next = apply(idx + 1)
            next.setUnsignedInt(next.readerOffset, value)
        }
        this
    }

    override def readFloat: Float = {
        val headReadable = head.readableBytes
        val m = if (headReadable > JFloat.BYTES) {
            head.readFloat
        } else if (headReadable == JFloat.BYTES) {
            val value = head.readFloat
            recycleHead()
            value
        } else if (headReadable == 3) {
            val b1 = head.readByte
            val b2 = head.readByte
            val b3 = head.readByte
            recycleHead()
            val b4 = head.readByte
            JFloat.intBitsToFloat(((b1 & 0xff) << 24) | (b2 & 0xff) << 16 | (b3 & 0xff) << 8 | (b4 & 0xff))
        } else if (headReadable == 2) {
            val b1 = head.readByte
            val b2 = head.readByte
            recycleHead()
            val b3 = head.readByte
            val b4 = head.readByte
            JFloat.intBitsToFloat(((b1 & 0xff) << 24) | (b2 & 0xff) << 16 | (b3 & 0xff) << 8 | (b4 & 0xff))
        } else { // headReadable == 1
            val b1 = head.readByte
            recycleHead()
            val b2 = head.readByte
            val b3 = head.readByte
            val b4 = head.readByte
            JFloat.intBitsToFloat(((b1 & 0xff) << 24) | (b2 & 0xff) << 16 | (b3 & 0xff) << 8 | (b4 & 0xff))
        }
        ridx += JFloat.BYTES
        m
    }

    override def getFloat(index: Int): Float = {
        val (idx, off) = offsetAtOffset(index)
        val buffer     = apply(idx)
        val len        = buffer.readableBytes - off
        if (len >= Integer.BYTES) {
            buffer.getFloat(buffer.readerOffset + off)
        } else if (len == 3) {
            val b1   = buffer.getByte(buffer.readerOffset + off)
            val b2   = buffer.getByte(buffer.readerOffset + off + 1)
            val b3   = buffer.getByte(buffer.readerOffset + off + 2)
            val next = apply(idx + 1)
            val b4   = next.getByte(next.readerOffset)
            JFloat.intBitsToFloat(((b1 & 0xff) << 24) | (b2 & 0xff) << 16 | (b3 & 0xff) << 8 | (b4 & 0xff))
        } else if (len == 2) {
            val b1   = buffer.getByte(buffer.readerOffset + off)
            val b2   = buffer.getByte(buffer.readerOffset + off + 1)
            val next = apply(idx + 1)
            val b3   = next.getByte(next.readerOffset)
            val b4   = next.getByte(next.readerOffset + 1)
            JFloat.intBitsToFloat(((b1 & 0xff) << 24) | (b2 & 0xff) << 16 | (b3 & 0xff) << 8 | (b4 & 0xff))
        } else if (len == 1) {
            val b1   = buffer.getByte(buffer.readerOffset + off)
            val next = apply(idx + 1)
            val b2   = next.getByte(next.readerOffset)
            val b3   = next.getByte(next.readerOffset + 1)
            val b4   = next.getByte(next.readerOffset + 2)
            JFloat.intBitsToFloat(((b1 & 0xff) << 24) | (b2 & 0xff) << 16 | (b3 & 0xff) << 8 | (b4 & 0xff))
        } else {
            val next = apply(idx + 1)
            next.getFloat(next.readerOffset)
        }
    }

    override def writeFloat(value: Float): Buffer = {
        if (realWritableBytes >= JFloat.BYTES) {
            last.writeFloat(value)
        } else {
            extendBuffer()
            last.writeFloat(value)
        }
        widx += JFloat.BYTES
        this
    }

    override def setFloat(index: Int, value: Float): Buffer = {
        val (idx, off) = offsetAtOffset(index)
        val buffer     = apply(idx)
        val len        = buffer.readableBytes - off
        if (len >= JFloat.BYTES) {
            buffer.setFloat(buffer.readerOffset + off, value)
        } else if (len == 3) {
            val v = JFloat.floatToIntBits(value)
            buffer.setByte(buffer.readerOffset + off, (v >>> 0).toByte)
            buffer.setByte(buffer.readerOffset + off + 1, (v >>> 8).toByte)
            buffer.setByte(buffer.readerOffset + off + 2, (v >>> 16).toByte)
            val next = apply(idx + 1)
            next.setByte(next.readerOffset, (v >>> 24).toByte)
        } else if (len == 2) {
            val v = JFloat.floatToIntBits(value)
            buffer.setByte(buffer.readerOffset + off, (v >>> 0).toByte)
            buffer.setByte(buffer.readerOffset + off + 1, (v >>> 8).toByte)
            val next = apply(idx + 1)
            next.setByte(next.readerOffset, (v >>> 16).toByte)
            next.setByte(next.readerOffset + 1, (v >>> 24).toByte)
        } else if (len == 1) {
            val v = JFloat.floatToIntBits(value)
            buffer.setByte(buffer.readerOffset + off, (v >>> 0).toByte)
            val next = apply(idx + 1)
            next.setByte(next.readerOffset, (v >>> 8).toByte)
            next.setByte(next.readerOffset + 1, (v >>> 16).toByte)
            next.setByte(next.readerOffset + 2, (v >>> 24).toByte)
        } else {
            val next = apply(idx + 1)
            next.setFloat(next.readerOffset, value)
        }
        this
    }

    override def readLong: Long = {
        val headReadable = head.readableBytes
        if (headReadable > JLong.BYTES) {
            val v = head.readLong
            ridx += JFloat.BYTES
            v
        } else if (headReadable == JLong.BYTES) {
            val value = head.readLong
            ridx += JFloat.BYTES
            recycleHead()
            value
        } else {
            val v = getLong(ridx)
            this.readerOffset(ridx + JLong.BYTES)
            v
        }
    }

    override def getLong(index: Int): Long = {
        val (idx, off) = offsetAtOffset(index)
        val buffer     = apply(idx)
        val len        = buffer.readableBytes - off
        if (len >= JLong.BYTES) {
            buffer.getLong(buffer.readerOffset + off)
        } else if (len > 0) {
            var offset = off
            var base   = 0L
            var buf    = buffer
            var i      = 0
            while (i < 8) {
                base = base << i * 8 | buf.getByte(buf.readerOffset + offset + i)
                if (i == len - 1) {
                    buf = apply(idx + 1)
                    offset = -len
                }
                i += 1
            }
            base
        } else {
            val next = apply(idx + 1)
            next.getLong(next.readerOffset)
        }
    }

    override def writeLong(value: Long): Buffer = {
        if (realWritableBytes >= JLong.BYTES) {
            last.writeLong(value)
        } else {
            extendBuffer()
            last.writeLong(value)
        }
        widx += JLong.BYTES
        this
    }

    override def setLong(index: Int, value: Long): Buffer = {
        val (idx, off) = offsetAtOffset(index)
        val buffer     = apply(idx)
        val len        = buffer.readableBytes - off
        if (len >= JLong.BYTES) {
            buffer.setLong(buffer.readerOffset + off, value)
        } else if (len > 0) {
            var offset = off
            var buf    = buffer
            var i      = 0
            while (i < 8) {
                buf.setByte(buf.readerOffset + offset + i, (value >>> (7 - i) * 8).toByte)
                if (i == len - 1) {
                    buf = apply(idx + 1)
                    offset = -len
                }
                i += 1
            }
        } else {
            val next = apply(idx + 1)
            next.setLong(next.readerOffset, value)
        }
        this
    }

    override def readDouble: Double = {
        val headReadable = head.readableBytes
        if (headReadable > JDouble.BYTES) {
            val v = head.readDouble
            ridx += JDouble.BYTES
            v
        } else if (headReadable == JDouble.BYTES) {
            val value = head.readDouble
            ridx += JDouble.BYTES
            recycleHead()
            value
        } else {
            val v = getDouble(ridx)
            this.readerOffset(ridx + JDouble.BYTES)
            v
        }
    }

    override def getDouble(index: Int): Double = {
        val v = getLong(index)
        JDouble.longBitsToDouble(v)
    }

    override def writeDouble(value: Double): Buffer = {
        if (realWritableBytes >= JDouble.BYTES) {
            last.writeDouble(value)
        } else {
            extendBuffer()
            last.writeDouble(value)
        }
        widx += JDouble.BYTES
        this
    }

    override def setDouble(index: Int, value: Double): Buffer = {
        setLong(index, JDouble.doubleToRawLongBits(value))
    }

    override def toString(): String = s"AdaptiveBuffer[ridx:$ridx, widx:$widx, cap:$capacity, count:$size]"

    override def readableBytes: Int = widx - ridx

    override def writableBytes: Int = capacity - widx

    private def realWritableBytes: Int = if (nonEmpty) last.writableBytes else 0

    override def allocatedWritableBytes: Int = realWritableBytes

    override def writeCharSequence(source: CharSequence, charset: Charset): Buffer = {
        val array = source.toString.getBytes(charset)
        writeBytes(array)
        this
    }

    override def readCharSequence(length: Int, charset: Charset): CharSequence = {
        val array = new Array[Byte](length)
        readBytes(array)
        new String(array, 0, length, charset)
    }

    override def readStringAsLong(length: Int, radix: Int): Long = if (length > 0) {
        checkReadBounds(ridx + length)
        if (head.readableBytes >= length) {
            val value = head.readStringAsLong(length, radix)
            if (head.readableBytes == 0) recycleHead()
            ridx += length
            value
        } else {
            val str = readCharSequence(length).toString
            str.toLong
        }
    } else throw new NumberFormatException(s"string length must be positive: length = $length")

    override def getStringAsLong(index: Int, length: Int, radix: Int): Long = {
        checkReadBounds(index + length)
        val (idx, off) = offsetAtOffset(index)
        val buffer     = apply(idx)
        if (buffer.readableBytes - off >= length) buffer.getStringAsLong(buffer.readerOffset + off, length, radix)
        else {
            val str = getCharSequence(index, length).toString
            str.toLong
        }
    }

    override def readStringAsDouble(length: Int): Double = if (length > 0) {
        checkReadBounds(ridx + length)
        val v = head.readStringAsDouble(length)
        if (head.readableBytes == 0) recycleHead()
        v
    } else throw new NumberFormatException(s"string length must be positive: length = $length")

    override def getStringAsDouble(index: Int, length: Int): Double = {
        checkReadBounds(index + length)
        val (idx, off) = offsetAtOffset(index)
        val buffer     = apply(idx)
        if (buffer.readableBytes - off >= length) buffer.getStringAsDouble(buffer.readerOffset + off, length)
        else {
            val str = getCharSequence(index, length).toString
            str.toDouble
        }
    }

    override def writeUUIDAsString(uuid: UUID): Unit = {
        if (realWritableBytes < 36) this.extendBuffer()
        last.writeUUIDAsString(uuid)
        widx += 36
    }

    override def setUUIDAsString(index: Int, uuid: UUID): Unit = {
        val (idx, off) = offsetAtOffset(index)
        val buffer     = apply(idx)
        if (buffer.readableBytes - off >= 36) buffer.setUUIDAsString(buffer.readerOffset + off, uuid)
        else {
            val str = uuid.toString
            this.setCharSequence(index, str)
        }
    }

    override def readStringAsUUID(): UUID = {
        checkReadBounds(ridx + 36)
        val uuid =
            if (head.readableBytes >= 36) head.readStringAsUUID()
            else {
                val str = this.getCharSequence(ridx, 36).toString
                UUID.fromString(str)
            }
        if (head.readableBytes == 0) recycleHead()
        ridx += 36
        uuid
    }

    override def getStringAsUUID(index: Int): UUID = {
        val (idx, off) = offsetAtOffset(index)
        val buffer     = apply(idx)
        if (buffer.readableBytes - off >= 36) buffer.getStringAsUUID(buffer.readerOffset + off)
        else {
            val str = this.getCharSequence(index, 36).toString
            UUID.fromString(str)
        }
    }

    override def writeBytes(source: Buffer, length: Int): Buffer = {
        if (closed) throw new BufferClosedException()
        if (source.readableBytes < length)
            throw new IndexOutOfBoundsException(
              s"source buffer readableBytes is less than length: readableBytes = $readableBytes, length = $length"
            )
        val remaining = if (source.readableBytes > length) source.readableBytes - length else 0
        source match
            case buffer: AbstractBuffer =>
                while {
                    last.writeBytes(buffer, buffer.readableBytes - remaining)
                    val continue = buffer.readableBytes > remaining
                    if (continue) extendBuffer()
                    continue
                } do ()
                widx += length
            case buffer: AdaptiveBuffer =>
                if (allocator == buffer.allocator) { // zero copy
                    val pageChain = buffer.splitBefore(buffer.readerOffset + length)
                    var cursor    = pageChain
                    while (cursor != null) {
                        val buffer = cursor
                        cursor = cursor.next
                        buffer.next = null
                        this.extend(buffer)
                    }
                } else {
                    val pageChain = buffer.splitBefore(buffer.readerOffset + length)
                    var cursor    = pageChain
                    while (cursor != null) {
                        val buf = cursor
                        cursor = cursor.next
                        buf.next = null
                        this.writeBytes(buf)
                        buf.close()
                    }
                }
            case _ =>
                while (source.readableBytes > 0) this.writeByte(source.readByte)
        this
    }

    override def writeBytes(source: Array[Byte], srcPos: Int, length: Int): Buffer = {
        if (closed) throw new BufferClosedException()
        if (srcPos + length > source.length)
            throw new IndexOutOfBoundsException(
              s"srcPos + length is underflow the bound of source: srcPos + length = ${srcPos + length}, source.length = ${source.length}"
            )

        var remaining = length
        if (allocatedWritableBytes == 0) extendBuffer()
        while {
            val write = Math.min(remaining, last.writableBytes)
            last.writeBytes(source, srcPos + (length - remaining), write)
            remaining -= write
            if (remaining > 0) this.extendBuffer()
            remaining > 0
        } do ()

        widx += length

        this
    }

    override def writeBytes(length: Int, value: Byte): Buffer = {
        if (closed) throw new BufferClosedException()

        var remaining = length
        if (allocatedWritableBytes == 0) extendBuffer()
        while {
            val write = Math.min(remaining, last.writableBytes)
            last.writeBytes(write, value)
            remaining -= write
            if (remaining > 0) this.extendBuffer()
            remaining > 0
        } do ()

        widx += length

        this
    }

    override def setBytes(index: Int, source: Array[Byte], srcPos: Int, length: Int): Buffer = {
        if (closed) throw new BufferClosedException()
        if (length < 0) throw new IndexOutOfBoundsException(s"length of source can't be negative: length = $length")
        if (srcPos + length > source.length)
            throw new IndexOutOfBoundsException(
              s"srcPos + length is underflow the bound of source: srcPos + length = ${srcPos + length}, source.length = ${source.length}"
            )

        if (length > widx - index)
            throw new IndexOutOfBoundsException(
              s"length can't large than writerOffset - index: writerOffset - index = ${widx - index}, length = $length"
            )

        val (fromIndex, fromOff) = offsetAtOffset(index)
        var remaining            = length
        var cursor               = fromIndex
        var off                  = fromOff
        var sourceRead           = 0
        while {
            val buffer = apply(cursor)
            val write  = Math.min(remaining, buffer.writerOffset - off)
            buffer.byteBuffer.put(buffer.readerOffset + off, source, sourceRead, write)
            remaining -= write
            if (remaining > 0) {
                off = 0
                sourceRead += write
                cursor += 1
            }
            remaining > 0
        } do ()

        this
    }

    override def writeBytes(source: ByteBuffer, length: Int): Buffer = {
        if (closed) throw new BufferClosedException()
        if (source.position() + length > source.capacity())
            throw new IndexOutOfBoundsException(
              s"position + length is underflow the bound of source: position + length = ${source
                      .position() + length}, source.capacity = ${source.capacity()}"
            )

        var remaining = length
        if (allocatedWritableBytes == 0) extendBuffer()
        while {
            val write = Math.min(remaining, last.writableBytes)
            last.writeBytes(source, write)
            remaining -= write
            if (remaining > 0) this.extendBuffer()
            remaining > 0
        } do ()

        widx += length

        this
    }

    override def readBytes(destination: ByteBuffer, length: Int): Buffer = {
        val len = math.min(destination.remaining(), readableBytes)
        if (len <= head.readableBytes) { // read from head buffer
            head.readBytes(destination)
            ridx += len
            if (head.readableBytes == 0) recycleHead()
        } else {
            val chain  = splitBefore(ridx + len)
            var cursor = chain
            while (cursor != null) {
                val buffer = cursor
                cursor = cursor.next
                buffer.readBytes(destination)
                buffer.close()
            }
        }
        this
    }

    override def readBytes(destination: Array[Byte], destPos: Int, length: Int): Buffer = {
        val len = math.min(math.min(length, destination.length - destPos), readableBytes)
        if (len <= head.readableBytes) {
            head.readBytes(destination, destPos, length)
            ridx += len
            if (head.readableBytes == 0) recycleHead()
        } else {
            val chain  = splitBefore(ridx + len)
            var cursor = chain
            var desOff = destPos
            while (cursor != null) {
                val buffer = cursor
                cursor = cursor.next
                val l = buffer.readableBytes
                buffer.readBytes(destination, desOff, l)
                desOff += l
                buffer.close()
            }
        }
        this
    }

    override def readBytes(destination: Buffer, length: Int): Buffer = {
        if (closed) throw new BufferClosedException()
        destination match
            case buffer: AbstractBuffer =>
                if (buffer.writableBytes < length)
                    throw new IndexOutOfBoundsException(
                      s"length is large than the writableBytes of destination: writableBytes = ${buffer.writableBytes}, length = ${length} "
                    )

                if (readableBytes < length)
                    throw new IndexOutOfBoundsException(
                      s"length is large than the readableBytes of this buffer: readableBytes = ${readableBytes}, length = ${length}"
                    )
                val chain  = this.splitBefore(readerOffset + length)
                var cursor = chain
                while (cursor != null) {
                    val buf = cursor
                    cursor = cursor.next
                    buffer.writeBytes(buf)
                    buf.close()
                }
                ridx += length
            case buffer: AdaptiveBuffer =>
                if (allocator == buffer.allocator) { // zero copy
                    var cursor = this.splitBefore(readerOffset + length)
                    while (cursor != null) {
                        val buf = cursor
                        cursor = cursor.next
                        buf.next = null
                        buffer.extend(buf)
                    }
                } else {
                    var cursor = this.splitBefore(readerOffset + length)
                    while (cursor != null) {
                        val buf = cursor
                        cursor = cursor.next
                        buf.next = null
                        buffer.writeBytes(buf)
                        buf.close()
                    }
                }
            case _ =>
                var len = length
                while (len > 0) {
                    destination.writeByte(this.readByte)
                    len -= 1
                }
        this
    }

    override def nextIs(byte: Byte): Boolean = if (nonEmpty) head.nextIs(byte) else false

    override def nextAre(bytes: Array[Byte]): Boolean = if (readableBytes >= bytes.length) {
        if (head.readableBytes > bytes.length) head.nextAre(bytes)
        else {
            val copy = new Array[Byte](bytes.length)
            this.copyInto(ridx, copy, 0, bytes.length)
            copy sameElements bytes
        }
    } else false

    override def indexIs(byte: Byte, index: Int): Boolean = this.getByte(index) == byte

    override def indexAre(bytes: Array[Byte], index: Int): Boolean = if (widx - index >= bytes.length) {
        val (idx, off) = offsetAtOffset(index)
        if (head.readableBytes - off >= bytes.length) head.indexAre(bytes, head.readerOffset + off)
        else {
            val copy = new Array[Byte](bytes.length)
            this.copyInto(index, copy, 0, bytes.length)
            copy sameElements bytes
        }
    } else false

    override def nextIn(bytes: Array[Byte]): Boolean = if (nonEmpty) head.nextIn(bytes) else false

    override def indexIn(bytes: Array[Byte], index: Int): Boolean = {
        var notIn = true
        var i     = 0
        val b     = this.getByte(index)
        while (notIn && i < bytes.length) {
            notIn = b != bytes(i)
            i += 1
        }
        !notIn
    }

    override def nextInRange(lower: Byte, upper: Byte): Boolean =
        if (nonEmpty) head.nextInRange(lower, upper) else false

    override def indexInRange(lower: Byte, upper: Byte, index: Int): Boolean = {
        val b = this.getByte(index)
        b >= lower && b <= upper
    }

    override def skipIfNextIs(byte: Byte): Boolean = if (nonEmpty) {
        val res = head.skipIfNextIs(byte)
        if (res) {
            ridx += 1
            if (head.readableBytes == 0) recycleHead()
        }
        res
    } else false

    override def skipIfNextAre(bytes: Array[Byte]): Boolean = if (nonEmpty && head.readableBytes >= bytes.length) {
        val res = head.skipIfNextAre(bytes)
        if (res) {
            ridx += bytes.length
            if (head.readableBytes == 0) recycleHead()
        }
        res
    } else if (readableBytes >= bytes.length) {
        var skip = true
        var i    = 0
        while (skip && i < bytes.length) {
            skip = getByte(ridx + i) == bytes(i)
            i += 1
        }
        if (skip) readerOffset(ridx + bytes.length)
        skip
    } else false

    override def skipIfNextIgnoreCaseAre(bytes: Array[Byte]): Boolean =
        if (nonEmpty && head.readableBytes >= bytes.length) {
            val res = head.skipIfNextIgnoreCaseAre(bytes)
            if (res) {
                ridx += bytes.length
                if (head.readableBytes == 0) recycleHead()
            }
            res
        } else if (readableBytes >= bytes.length) {
            var skip = true
            var i    = 0
            while (skip && i < bytes.length) {
                skip = ignoreCaseEqual(getByte(ridx + i), bytes(i))
                i += 1
            }
            if (skip) readerOffset(ridx + bytes.length)
            skip
        } else false

    override def skipIfNextIn(set: Array[Byte]): Boolean = if (nonEmpty) {
        val res = head.skipIfNextIn(set)
        if (res) {
            ridx += 1
            if (head.readableBytes == 0) recycleHead()
        }
        res
    } else false

    inline private def checkFromTo(from: Int, to: Int): Unit = {
        if (from < ridx)
            throw new IndexOutOfBoundsException(s"from is less than readerOffset: form = $from, readerOffset = $ridx")
        if (to > widx)
            throw new IndexOutOfBoundsException(s"to is beyond the end of the buffer: to = $to, writerOffset = $widx")
    }

    override def sslunwarp(engine: SSLEngine, packetLength: Int, target: ByteBuffer): SSLEngineResult = {
        if (head.readableBytes >= packetLength) {
            val src = head.underlying
            src.position(head.readerOffset)
            src.limit(head.writerOffset)
            val res = engine.unwrap(src, target)
            this.skipReadableBytes(packetLength)
            res
        } else if (packetLength <= allocator.fixedCapacity) {
            val buffer = allocator.allocate()
            this.readBytes(buffer, packetLength)
            buffer.byteBuffer.position(buffer.readerOffset)
            buffer.byteBuffer.limit(buffer.writerOffset)
            val res = engine.unwrap(buffer.byteBuffer, target)
            buffer.close()
            res
        } else {
            val byteBuffer =
                if (isDirect) ByteBuffer.allocateDirect(packetLength) else ByteBuffer.allocate(packetLength)
            this.readBytes(byteBuffer, packetLength)
            byteBuffer.flip()
            engine.unwrap(byteBuffer, target)
        }
    }

    override def sslwarp(engine: SSLEngine, target: AdaptiveBuffer): SSLEngineResult = ???

    override def sslHandshakeWarp(engine: SSLEngine, emptySource: Array[ByteBuffer]): SSLEngineResult = {
        if (allocatedWritableBytes == 0) this.extendBuffer()
        val buffer     = last
        val byteBuffer = last.underlying
        byteBuffer.position(buffer.writerOffset)
        byteBuffer.limit(buffer.writerOffset + buffer.writableBytes)

        val bf = ByteBuffer.allocate(2048)

        var result = engine.wrap(emptySource, bf)

        val packetBufferSize = engine.getSession.getPacketBufferSize
        println("")
        ???
    }

    override def sslHandshakeUnwarp(engine: SSLEngine, emptyTarget: Array[ByteBuffer]): SSLEngineResult = ???

}
