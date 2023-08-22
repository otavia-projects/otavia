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

    override def bytesBefore(needle: Byte): Int = {
        var offset: Int       = ridx
        var continue: Boolean = true
        while (continue && offset < widx) {
            if (underlying.get(offset) == needle) {
                continue = false
            } else offset += 1
        }
        if (continue) -1 else offset - ridx
    }

    override def bytesBefore(needle1: Byte, needle2: Byte): Int = if (readableBytes >= 2) {
        var offset: Int       = ridx
        var continue: Boolean = true
        var b1: Byte          = 0
        var b2: Byte          = underlying.get(offset)
        while (continue && offset < widx - 1) {
            b1 = b2
            b2 = underlying.get(offset + 1)
            if (b1 == needle1 && b2 == needle2) {
                continue = false
            } else offset += 1
        }
        if (continue) -1 else offset - ridx
    } else -1

    override def bytesBefore(needle1: Byte, needle2: Byte, needle3: Byte): Int = if (readableBytes >= 3) {
        var offset: Int       = ridx
        var continue: Boolean = true
        var b1: Byte          = 0
        var b2: Byte          = underlying.get(offset)
        var b3: Byte          = underlying.get(offset + 1)
        while (continue && offset < widx - 2) {
            b1 = b2
            b2 = b3
            b3 = underlying.get(offset + 2)
            if (b1 == needle1 && b2 == needle2 && b3 == needle3) {
                continue = false
            } else offset += 1
        }
        if (continue) -1 else offset - ridx
    } else -1

    override def bytesBefore(needle1: Byte, needle2: Byte, needle3: Byte, needle4: Byte): Int =
        if (readableBytes >= 4) {
            var offset: Int       = ridx
            var continue: Boolean = true
            var b1: Byte          = 0
            var b2: Byte          = underlying.get(offset)
            var b3: Byte          = underlying.get(offset + 1)
            var b4: Byte          = underlying.get(offset + 2)
            while (continue && offset < widx - 3) {
                b1 = b2
                b2 = b3
                b3 = b4
                b4 = underlying.get(offset + 3)
                if (b1 == needle1 && b2 == needle2 && b3 == needle3 && b4 == needle4) {
                    continue = false
                } else offset += 1
            }
            if (continue) -1 else offset - ridx
        } else -1

    override def bytesBefore(needle: Array[Byte]): Int = if (readableBytes >= needle.length) {
        if (needle.length == 1) bytesBefore(needle(0))
        else if (needle.length == 2) bytesBefore(needle(0), needle(1))
        else if (needle.length == 3) bytesBefore(needle(0), needle(1), needle(2))
        else if (needle.length == 4) bytesBefore(needle(0), needle(1), needle(2), needle(3))
        else {
            val length            = needle.length
            val copy              = new Array[Byte](length)
            var offset: Int       = ridx
            var continue: Boolean = true
            while (continue && offset < widx - length) {
                this.copyInto(offset, copy, 0, length)
                if (copy sameElements needle) continue = false else offset += 1
            }
            if (continue) -1 else offset - ridx
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
        underlying.put(widx, (value >> 16).toByte)
        underlying.put(widx + 1, (value >> 8 & 0xff).toByte)
        underlying.put(widx + 2, (value & 0xff).toByte)
        this
    }

    override def setMediumLE(index: Int, value: Int): Buffer = {
        underlying.put(widx, (value & 0xff).toByte)
        underlying.put(widx + 1, (value >> 8 & 0xff).toByte)
        underlying.put(widx + 2, (value >> 16).toByte)
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

    override def skipIfNext(byte: Byte): Boolean = if (underlying.get(ridx) == byte) {
        ridx += 1
        true
    } else false

    override def skipIfNexts(bytes: Array[Byte]): Boolean = {
        var skip = true
        var i    = 0
        while (skip && i < bytes.length) {
            skip = underlying.get(ridx + i) == bytes(i)
            i += 1
        }
        if (skip) ridx += bytes.length
        skip
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

}
