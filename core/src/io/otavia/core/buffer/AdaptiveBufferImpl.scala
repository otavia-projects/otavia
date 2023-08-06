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

package io.otavia.core.buffer

import io.otavia.buffer.{Buffer, ByteCursor, PageBuffer, PageBufferAllocator}
import io.otavia.core.buffer.AdaptiveBuffer.AdaptiveStrategy

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

    override private[otavia] def splitBefore(offset: Int) = ???

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
        ridx += JByte.BYTES
        val value = head.readByte
        if (head.readableBytes == 0) recycleHead()
        value
    }

    override def getByte(index: Int): Byte = {
        val (idx, off) = offsetAtOffset(index)
        val buffer     = apply(idx)
        buffer.getByte(buffer.readerOffset + off)
    }

    override def readUnsignedByte: Int = {
        ridx += JByte.BYTES
        val value = head.readUnsignedByte
        if (head.readableBytes == 0) recycleHead()
        value
    }

    override def getUnsignedByte(index: Int): Int = {
        val (idx, off) = offsetAtOffset(index)
        val buffer     = apply(idx)
        buffer.getUnsignedByte(buffer.readerOffset + off)
    }

    override def writeByte(value: Byte): Buffer = {
        if (realWritableBytes >= JByte.BYTES) {
            widx += JByte.BYTES
            last.writeByte(value)
        } else {
            extendBuffer()
            widx += JByte.BYTES
            last.writeByte(value)
        }
        this
    }

    override def setByte(index: Int, value: Byte): Buffer = {
        val (idx, off) = offsetAtOffset(index)
        val buffer     = apply(idx)
        buffer.setByte(buffer.readerOffset + off, value)
        this
    }

    override def writeUnsignedByte(value: Int): Buffer = {
        if (realWritableBytes >= JByte.BYTES) {
            widx += JByte.BYTES
            last.writeUnsignedByte(value)
        } else {
            extendBuffer()
            widx += JByte.BYTES
            last.writeUnsignedByte(value)
        }
        this
    }

    override def setUnsignedByte(index: Int, value: Int): Buffer = {
        val (idx, off) = offsetAtOffset(index)
        val buffer     = apply(idx)
        buffer.setUnsignedByte(buffer.readerOffset + off, value)
        this
    }

    override def readChar: Char = {
        ridx += Character.BYTES
        val headReadable = head.readableBytes
        if (headReadable > Character.BYTES) {
            head.readChar
        } else if (headReadable == Character.BYTES) {
            val value = head.readChar
            recycleHead()
            value
        } else {
            val b1 = head.readByte
            recycleHead()
            val b2 = head.readByte
            (((b1 & 0xff) << 8) | (b2 & 0xff)).toShort.toChar
        }
    }

    override def getChar(index: Int): Char = {
        val (idx, off) = offsetAtOffset(index)
        val buffer     = apply(idx)
        val len        = buffer.readableBytes - off
        if (len >= Character.BYTES) {
            buffer.getChar(buffer.readerOffset + off)
        } else if (len > 0) {
            val b1         = buffer.getByte(buffer.readerOffset + off)
            val nextBuffer = apply(idx + 1)
            val b2         = nextBuffer.getByte(nextBuffer.readerOffset)
            (((b1 & 0xff) << 8) | (b2 & 0xff)).toShort.toChar
        } else {
            val next = apply(idx + 1)
            buffer.getChar(buffer.readerOffset)
        }
    }

    override def writeChar(value: Char): Buffer = {
        if (realWritableBytes >= Character.BYTES) {
            widx += Character.BYTES
            last.writeChar(value)
        } else {
            extendBuffer()
            widx += Character.BYTES
            last.writeChar(value)
        }
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
        } else {
            val next = apply(idx + 1)
            buffer.setByte(buffer.readerOffset + off, (value.toShort >>> 0).toByte)
            next.setByte(next.readableBytes, (value.toShort >>> 8).toByte)
        }
        this
    }

    override def readShort: Short = {
        ridx += JShort.BYTES
        val headReadable = head.readableBytes
        if (headReadable > JShort.BYTES) {
            head.readShort
        } else if (headReadable == JShort.BYTES) {
            val value = head.readShort
            recycleHead()
            value
        } else {
            val b1 = head.readByte
            recycleHead()
            val b2 = head.readByte
            (((b1 & 0xff) << 8) | (b2 & 0xff)).toShort
        }
    }

    override def writeShort(value: Short): Buffer = {
        if (realWritableBytes >= JShort.BYTES) {
            widx += JShort.BYTES
            last.writeShort(value)
        } else {
            extendBuffer()
            widx += JShort.BYTES
            last.writeShort(value)
        }
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
            val next = apply(idx + 1)
            buffer.setByte(buffer.readerOffset + off, (value >>> 0).toByte)
            next.setByte(next.readableBytes, (value >>> 8).toByte)
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
            val next = apply(idx + 1)
            val b1   = buffer.getByte(buffer.readerOffset + off)
            val b2   = next.getByte(next.readerOffset)
            (((b1 & 0xff) << 8) | (b2 & 0xff)).toShort
        } else {
            val next = apply(idx + 1)
            buffer.getShort(buffer.readerOffset)
        }
    }

    override def readUnsignedShort: Int = {
        ridx += JShort.BYTES
        val headReadable = head.readableBytes
        if (headReadable > JShort.BYTES) {
            head.readUnsignedShort
        } else if (headReadable == JShort.BYTES) {
            val value = head.readUnsignedShort
            recycleHead()
            value
        } else {
            val b1 = head.readByte
            recycleHead()
            val b2 = head.readByte
            (((b1 & 0xff) << 8) | (b2 & 0xff)) & 0xffff
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
            (((b1 & 0xff) << 8) | (b2 & 0xff)) & 0xffff
        } else {
            val next = apply(idx + 1)
            buffer.getUnsignedShort(buffer.readerOffset)
        }
    }

    override def writeUnsignedShort(value: Int): Buffer = {
        if (realWritableBytes >= JShort.BYTES) {
            widx += JShort.BYTES
            last.writeUnsignedShort(value)
        } else {
            extendBuffer()
            widx += JShort.BYTES
            last.writeUnsignedShort(value)
        }
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
            val next     = apply(idx + 1)
            val newValue = (value & 0xffff).toShort
            buffer.setByte(buffer.readerOffset + off, (newValue >>> 0).toByte)
            next.setByte(next.readableBytes, (newValue >>> 8).toByte)
        }
        this
    }

    override def readMedium: Int = {
        ridx += 3
        val headReadable = head.readableBytes
        if (headReadable > 3) {
            head.readMedium
        } else if (headReadable == 3) {
            val value = head.readMedium
            recycleHead()
            value
        } else {
            var value: Int = 0
            var i          = 3
            while (i > 0) {
                val b = head.readByte & 0xff
                value = value | (b << 8)
                i -= 1
                if (head.readableBytes == 0) recycleHead()
            }
            value
        }
    }

    override def readMediumLE: Int = ???

    override def getMedium(index: Int): Int = ???

    override def getMediumLE(index: Int): Int = ???

    override def readUnsignedMedium: Int = ???

    override def readUnsignedMediumLE: Int = ???

    override def getUnsignedMedium(index: Int): Int = ???

    override def getUnsignedMediumLE(index: Int): Int = ???

    override def writeMedium(value: Int): Buffer = ???

    override def writeMediumLE(value: Int): Buffer = ???

    override def setMedium(index: Int, value: Int): Buffer = ???

    override def setMediumLE(index: Int, value: Int): Buffer = ???

    override def writeUnsignedMedium(value: Int): Buffer = ???

    override def writeUnsignedMediumLE(value: Int): Buffer = ???

    override def setUnsignedMedium(index: Int, value: Int): Buffer = ???

    override def setUnsignedMediumLE(index: Int, value: Int): Buffer = ???

    override def readInt: Int = ???

    override def getInt(index: Int): Int = ???

    override def readUnsignedInt: Long = ???

    override def getUnsignedInt(index: Int): Long = ???

    override def writeInt(value: Int): Buffer = ???

    override def setInt(index: Int, value: Int): Buffer = ???

    override def writeUnsignedInt(value: Long): Buffer = ???

    override def setUnsignedInt(index: Int, value: Long): Buffer = ???

    override def readFloat: Float = ???

    override def getFloat(index: Int): Float = ???

    override def writeFloat(value: Float): Buffer = ???

    override def setFloat(index: Int, value: Float): Buffer = ???

    override def readLong: Long = ???

    override def getLong(index: Int): Long = ???

    override def writeLong(value: Long): Buffer = ???

    override def setLong(index: Int, value: Long): Buffer = ???

    override def readDouble: Double = ???

    override def getDouble(index: Int): Double = ???

    override def writeDouble(value: Double): Buffer = ???

    override def setDouble(index: Int, value: Double): Buffer = ???

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
