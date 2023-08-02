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

package io.otavia.buffer

import java.nio.ByteBuffer
import java.nio.channels.{FileChannel, ReadableByteChannel, WritableByteChannel}
import java.nio.charset.{Charset, StandardCharsets}
import scala.language.unsafeNulls

abstract class AbstractBuffer(val underlying: ByteBuffer) extends Buffer {

    protected var ridx: Int = 0
    protected var widx: Int = 0

    underlying.limit(underlying.capacity())

    override def toString: String = s"Buffer[ridx:${ridx}, widx:${widx}, cap:${capacity}]"

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

    override def writeCharSequence(source: CharSequence, charset: Charset): Buffer = {
        val array = source.toString.getBytes(charset)
        writeBytes(array)
    }

    override def readCharSequence(length: Int, charset: Charset): CharSequence = {
        val array = new Array[Byte](length)
        copyInto(readerOffset, array, 0, length)
        skipReadableBytes(length)
        new String(array, 0, length, charset)
    }

    override def writeBytes(source: Buffer): Buffer = {
        underlying.position(widx)
        val length = source.readableBytes
        source.readBytes(underlying)
        widx += length
        this
    }

    override def writeBytes(source: Array[Byte], srcPos: Int, length: Int): Buffer = {
        underlying.position(widx)
        underlying.put(source, srcPos, length)
        widx += length
        this
    }

    override def writeBytes(source: ByteBuffer): Buffer = {
        val len = source.remaining()
        underlying.put(source)
        widx += len
        this
    }

    override def readBytes(destination: ByteBuffer): Buffer = {
        val length = readableBytes
        underlying.position(ridx)
        underlying.limit(ridx + length)
        destination.put(underlying)
        ridx += length
        this
    }

    override def readBytes(destination: Array[Byte], destPos: Int, length: Int): Buffer = {
        underlying.get(ridx, destination, destPos, length)
        ridx += length
        this
    }

    override def bytesBefore(needle: Byte): Int = {
        var offset: Int       = ridx
        var continue: Boolean = true
        underlying.position(ridx)
        underlying.limit(widx)
        while (continue && offset < widx) {
            if (underlying.get(offset) == needle) {
                continue = false
            } else offset += 1
        }
        if (continue) -1 else offset - ridx
    }

    override def bytesBefore(needle1: Byte, needle2: Byte): Int = {
        if (readableBytes >= 2) {
            var offset: Int       = ridx
            var continue: Boolean = true
            underlying.position(ridx)
            underlying.limit(widx)
            var b1: Byte = 0
            var b2: Byte = underlying.get(offset)
            while (continue && offset < widx - 1) {
                b1 = b2
                b2 = underlying.get(offset + 1)
                if (b1 == needle1 && b2 == needle2) {
                    continue = false
                } else offset += 1
            }
            if (continue) -1 else offset - ridx
        } else -1

    }

    override def bytesBefore(needle1: Byte, needle2: Byte, needle3: Byte): Int = {
        if (readableBytes >= 3) {
            var offset: Int       = ridx
            var continue: Boolean = true
            underlying.position(ridx)
            underlying.limit(widx)
            var b1: Byte = 0
            var b2: Byte = underlying.get(offset)
            var b3: Byte = underlying.get(offset + 1)
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
    }

    override def bytesBefore(needle1: Byte, needle2: Byte, needle3: Byte, needle4: Byte): Int =
        if (readableBytes >= 4) {
            var offset: Int       = ridx
            var continue: Boolean = true
            underlying.position(ridx)
            underlying.limit(widx)
            var b1: Byte = 0
            var b2: Byte = underlying.get(offset)
            var b3: Byte = underlying.get(offset + 1)
            var b4: Byte = underlying.get(offset + 2)
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

    override def bytesBefore(needle: Array[Byte]): Int = {

        ???
    }

    override def openCursor(fromOffset: Int, length: Int): ByteCursor = ???

    override def openReverseCursor(fromOffset: Int, length: Int): ByteCursor = ???

    override def ensureWritable(size: Int, minimumGrowth: Int, allowCompaction: Boolean): Buffer = ???

    override def readByte: Byte = {
        val b = underlying.get(ridx)
        ridx += 1
        b
    }

    override def getByte(idx: Int): Byte = {
        underlying.get(idx)
    }

    override def readUnsignedByte: Int = ???

    override def getUnsignedByte(roff: Int): Int = ???

    override def writeByte(value: Byte): Buffer = ???

    override def setByte(woff: Int, value: Byte): Buffer = ???

    override def writeUnsignedByte(value: Int): Buffer = ???

    override def setUnsignedByte(woff: Int, value: Int): Buffer = ???

    override def readChar: Char = ???

    override def getChar(roff: Int): Char = ???

    override def writeChar(value: Char): Buffer = ???

    override def setChar(woff: Int, value: Char): Buffer = ???

    override def readShort: Short = ???

    override def getShort(roff: Int): Short = ???

    override def readUnsignedShort: Int = ???

    override def getUnsignedShort(roff: Int): Int = ???

    override def writeShort(value: Short): Buffer = ???

    override def setShort(woff: Int, value: Short): Buffer = ???

    override def writeUnsignedShort(value: Int): Buffer = ???

    override def setUnsignedShort(woff: Int, value: Int): Buffer = ???

    override def readMedium: Int = ???

    override def getMedium(roff: Int): Int = ???

    override def readUnsignedMedium: Int = ???

    override def getUnsignedMedium(roff: Int): Int = ???

    override def writeMedium(value: Int): Buffer = ???

    override def setMedium(woff: Int, value: Int): Buffer = ???

    override def writeUnsignedMedium(value: Int): Buffer = ???

    override def setUnsignedMedium(woff: Int, value: Int): Buffer = ???

    override def readInt: Int = ???

    override def getInt(roff: Int): Int = ???

    override def readUnsignedInt: Long = ???

    override def getUnsignedInt(roff: Int): Long = ???

    override def writeInt(value: Int): Buffer = ???

    override def setInt(woff: Int, value: Int): Buffer = ???

    override def writeUnsignedInt(value: Long): Buffer = ???

    override def setUnsignedInt(woff: Int, value: Long): Buffer = ???

    override def readFloat: Float = ???

    override def getFloat(roff: Int): Float = ???

    override def writeFloat(value: Float): Buffer = ???

    override def setFloat(woff: Int, value: Float): Buffer = ???

    override def readLong: Long = ???

    override def getLong(roff: Int): Long = ???

    override def writeLong(value: Long): Buffer = ???

    override def setLong(woff: Int, value: Long): Buffer = ???

    override def readDouble: Double = ???

    override def getDouble(roff: Int): Double = ???

    override def writeDouble(value: Double): Buffer = ???

    override def setDouble(woff: Int, value: Double): Buffer = ???

    override def close(): Unit = {
        ridx = 0
        widx = 0
        underlying.clear()
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
            val write = channel.write(underlying)
            if (write > 0) skipReadableBytes(write)
            write
        } else 0
    }

    override def transferFrom(channel: FileChannel, position: Long, length: Int): Int = {
        if (length > 0) {
            underlying.position(widx)
            if (length > writableBytes) underlying.limit(widx + writableBytes) else underlying.limit(widx + length)
            val read = channel.read(underlying, position)
            if (read > 0) skipWritableBytes(read)
            read
        } else 0
    }

    override def transferFrom(channel: ReadableByteChannel, length: Int): Int = {
        if (length > 0) {
            underlying.position(widx)
            if (length > writableBytes) underlying.limit(widx + writableBytes) else underlying.limit(widx + length)
            val read = channel.read(underlying)
            if (read > 0) skipWritableBytes(read)
            read
        } else 0
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
