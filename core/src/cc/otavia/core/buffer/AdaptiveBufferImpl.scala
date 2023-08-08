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

package cc.otavia.core.buffer

import cc.otavia.buffer.{Buffer, ByteCursor, PageBuffer, PageBufferAllocator}
import cc.otavia.core.buffer.AdaptiveBuffer.AdaptiveStrategy

import java.lang.{Byte as JByte, Double as JDouble, Float as JFloat, Long as JLong, Short as JShort}
import java.nio.ByteBuffer
import java.nio.channels.{FileChannel, ReadableByteChannel, WritableByteChannel}
import java.nio.charset.Charset
import scala.collection.mutable

private class AdaptiveBufferImpl(val allocator: PageBufferAllocator)
    extends mutable.ArrayDeque[PageBuffer]
    with AdaptiveBuffer {

    private var ridx: Int = 0
    private var widx: Int = 0

    private var closed: Boolean = false

    private var strategy: AdaptiveStrategy = AdaptiveBuffer.FullPageStrategy

    def setStrategy(adaptiveStrategy: AdaptiveStrategy): Unit = strategy = adaptiveStrategy

    private def startIndex = ridx - head.readerOffset

    private def endIndex: Int = widx + last.writableBytes

    private def offsetAtOffset(offset: Int): (Int, Int) = if (nonEmpty) {
        if (offset >= startIndex && offset < endIndex) {
            var len    = offset - ridx
            var cursor = 0
            while (len > apply(cursor).readableBytes && cursor < size) {
                len -= apply(cursor).readableBytes
                cursor += 1
            }
            (cursor, len)
        } else (-1, 0)
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
        }
    }

    private def extendBuffer(): Unit = {
        val buffer: PageBuffer = allocator.allocate()
        addOne(buffer)
        widx += buffer.readableBytes
    }

    final private[otavia] def extend(buffer: PageBuffer): Unit = {
        addOne(buffer)
        widx += buffer.readableBytes
    }

    override private[otavia] def splitBefore(offset: Int): PageBuffer = {
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
        checkReadBounds(offset)
        if (offset == widx) recycleAll()
        else {
            val (index, off) = offsetAtOffset(offset)
            var cursor       = index
            while (cursor > 0) {
                recycleHead()
                cursor -= 1
            }
            head.readerOffset(head.readerOffset + off)
            ridx = offset
        }
        this
    }

    override def writerOffset: Int = widx

    private def checkWriteBound(index: Int): Unit = {
        if (index < ridx)
            throw new IndexOutOfBoundsException(s"Set writerOffset $index is little than readerOffset $ridx")
    }

    override def writerOffset(offset: Int): Buffer = {
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
            } else {}
        } else {}
        widx = offset
        this
    }

    override def fill(value: Byte): Buffer = {
        for (buffer <- this) {
            buffer.fill(value)
        }
        this
    }

    override def isDirect: Boolean = allocator.isDirect

    override def writeBytes(source: AdaptiveBuffer, length: Int): AdaptiveBuffer = ???

    override def readBytes(destination: AdaptiveBuffer, length: Int): AdaptiveBuffer = ???

    override def copyInto(srcPos: Int, dest: Array[Byte], destPos: Int, length: Int): Unit = ???

    override def copyInto(srcPos: Int, dest: ByteBuffer, destPos: Int, length: Int): Unit = ???

    override def copyInto(srcPos: Int, dest: Buffer, destPos: Int, length: Int): Unit = ???

    override def transferTo(channel: WritableByteChannel, length: Int): Int = ???

    override def transferFrom(channel: FileChannel, position: Long, length: Int): Int = ???

    override def transferFrom(channel: ReadableByteChannel, length: Int): Int = ???

    override def bytesBefore(needle: Byte): Int = ???

    override def bytesBefore(needle1: Byte, needle2: Byte): Int = ???

    override def bytesBefore(needle1: Byte, needle2: Byte, needle3: Byte): Int = ???

    override def bytesBefore(needle1: Byte, needle2: Byte, needle3: Byte, needle4: Byte): Int = ???

    override def bytesBefore(needle: Array[Byte]): Int = ???

    override def openCursor(fromOffset: Int, length: Int): ByteCursor = ???

    override def openReverseCursor(fromOffset: Int, length: Int): ByteCursor = ???

    override def ensureWritable(size: Int, minimumGrowth: Int, allowCompaction: Boolean): Buffer =
        if (writableBytes >= size) this
        else {
            extendBuffer()
            // TODO
            this
        }

    override def close(): Unit = {
        recycleAll()
        closed = true
    }

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

    override def writableBytes: Int = capacity - widx

    private def realWritableBytes: Int = if (nonEmpty) last.writableBytes else 0

    override def writeCharSequence(source: CharSequence, charset: Charset): Buffer = ???

    override def readCharSequence(length: Int, charset: Charset): CharSequence = ???

    override def writeBytes(source: Buffer): Buffer = ???

    override def writeBytes(source: Array[Byte], srcPos: Int, length: Int): Buffer = ???

    override def writeBytes(source: ByteBuffer): Buffer = ???

    override def readBytes(destination: ByteBuffer): Buffer = ???

    override def readBytes(destination: Array[Byte], destPos: Int, length: Int): Buffer = ???

}
