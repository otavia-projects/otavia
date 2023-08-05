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

import io.otavia.buffer.*
import io.otavia.core.buffer.AdaptiveBuffer.AdaptiveStrategy
import io.otavia.core.cache.{PerThreadObjectPool, Poolable}
import io.otavia.core.util.Chainable

import java.lang.{Byte as JByte, Double as JDouble, Float as JFloat, Long as JLong, Short as JShort}
import java.nio.ByteBuffer
import java.nio.channels.{FileChannel, ReadableByteChannel, WritableByteChannel}
import java.nio.charset.Charset
import scala.collection.mutable
import scala.language.unsafeNulls

/** A Adaptive allocate and release memory [[PageBuffer]]. This type of [[Buffer]]
 *
 *  @param allocator
 *    [[PageBufferAllocator]] of this [[AdaptiveBuffer]]
 */
class AdaptiveBuffer(val allocator: PageBufferAllocator) extends Buffer {

    private val buffers: mutable.ArrayDeque[PageBuffer] = mutable.ArrayDeque.empty[PageBuffer]

    private var ridx = 0
    private var widx = 0

    private var closed = false

    private var strategy: AdaptiveStrategy = AdaptiveBuffer.FullPageStrategy

    def setStrategy(adaptiveStrategy: AdaptiveStrategy): Unit = strategy = adaptiveStrategy

    private def startIndex = ridx - buffers.head.readerOffset

    private def endIndex: Int = widx + buffers.last.writableBytes

    private def offsetAt(offset: Int): Int = if (buffers.nonEmpty) {
        if (offset >= startIndex && offset < endIndex) {
            var cursor = 0
            var len    = offset - ridx
            while (len > 0 && cursor < buffers.size) {
                len -= buffers(cursor).readableBytes
                cursor += 1
            }
            cursor
        } else -1
    } else -1

    private def offsetAtOffset(offset: Int): (Int, Int) = if (buffers.nonEmpty) {
        if (offset >= startIndex && offset < endIndex) {
            var len    = offset - ridx
            var cursor = 0
            while (len > 0 && cursor < buffers.size) {
                len -= buffers(cursor).readableBytes
                cursor += 1
            }
            (cursor, len)
        } else (-1, 0)
    } else (-1, 0)

    private def recycleHead(compact: Boolean = false): Unit = {
        if (buffers.nonEmpty) {
            val buffer = buffers.removeHead()
            buffer.close()
        }
        if (compact) {
            widx = widx - ridx
            ridx = 0
        }
    }

    private def recycleAll(compact: Boolean = false): Unit = {
        while (buffers.nonEmpty) {
            val buffer = buffers.removeHead()
            buffer.close()
        }
        if (compact) {
            ridx = 0
            widx = 0
            buffers.clearAndShrink()
        }
    }

    private def extendBuffer(): Unit = {
        val buffer: PageBuffer = allocator.allocate()
        buffers.addOne(buffer)
        widx += buffer.readableBytes
    }

    /** Append this [[PageBuffer]] to the end of this [[AdaptiveBuffer]]
     *
     *  @param buffer
     *    [[PageBuffer]] allocated by this [[allocator]]
     */
    final private[otavia] def extend(buffer: PageBuffer): Unit = {
        buffers.addOne(buffer)
        widx += buffer.readableBytes
    }

    override def capacity: Int = Int.MaxValue

    override def readerOffset: Int = ridx

    private def checkReadBounds(index: Int): Unit = {
        if (buffers.isEmpty && index > 0) throw new IndexOutOfBoundsException("The buffer is empty")
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
            buffers.head.readerOffset(buffers.head.readerOffset + off)
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
                buffers.last.writerOffset(buffers.last.capacity)
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
        for (buffer <- buffers) {
            buffer.fill(value)
        }
        this
    }

    override def isDirect: Boolean = allocator.isDirect

    /** Writes into this [[AdaptiveBuffer]] from the source [[AdaptiveBuffer]]. This updates the write offset of this
     *  buffer and also the position of the source [[AdaptiveBuffer]].
     *
     *  Note: the behaviour is undefined if the given [[AdaptiveBuffer]] is an alias for the memory in this buffer.
     *
     *  @param source
     *    The [[AdaptiveBuffer]] to read from.
     *  @param length
     *    length of data to write.
     *  @return
     *    This buffer.
     */
    def writeBytes(source: AdaptiveBuffer, length: Int): this.type = {

        ???
    }

    /** Read from this [[AdaptiveBuffer]], into the destination [[AdaptiveBuffer]] This updates the read offset of this
     *  buffer and also the position of the destination [[AdaptiveBuffer]].
     *
     *  Note: the behaviour is undefined if the given [[AdaptiveBuffer]] is an alias for the memory in this buffer.
     *
     *  @param destination
     *    The [[AdaptiveBuffer]] to write into.
     *  @param length
     *    length of data to read.
     *  @return
     *    This buffer.
     */
    def readBytes(destination: AdaptiveBuffer, length: Int): this.type = ???

    override def copyInto(srcPos: Int, dest: Array[Byte], destPos: Int, length: Int): Unit = ???

    override def copyInto(srcPos: Int, dest: ByteBuffer, destPos: Int, length: Int): Unit = ???

    override def copyInto(srcPos: Int, dest: Buffer, destPos: Int, length: Int): Unit = ???

    override def transferTo(channel: WritableByteChannel, length: Int): Int = ???

    override def transferFrom(channel: FileChannel, position: Long, length: Int): Int = ???

    override def transferFrom(channel: ReadableByteChannel, length: Int): Int = ???

    override def bytesBefore(needle: Byte): Int = ???

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

//    override def forEachComponent[T <: BufferComponent with ComponentIterator.Next](): ComponentIterator[T] = ???

//    override def send(): Send[Buffer] = ???

    override def close(): Unit = {
        recycleAll()
        closed = true
    }

    override def readByte: Byte = {
        ridx += JByte.BYTES
        val value = buffers.head.readByte
        if (buffers.head.readableBytes == 0) recycleHead()
        value
    }

    override def getByte(index: Int): Byte = {
        val (idx, off) = offsetAtOffset(index)
        val buffer     = buffers(idx)
        buffer.getByte(buffer.readerOffset + off)
    }

    override def readUnsignedByte: Int = {
        ridx += JByte.BYTES
        val value = buffers.head.readUnsignedByte
        if (buffers.head.readableBytes == 0) recycleHead()
        value
    }

    override def getUnsignedByte(index: Int): Int = {
        val (idx, off) = offsetAtOffset(index)
        val buffer     = buffers(idx)
        buffer.getUnsignedByte(buffer.readerOffset + off)
    }

    override def writeByte(value: Byte): Buffer = {
        if (realWritableBytes >= JByte.BYTES) {
            widx += JByte.BYTES
            buffers.last.writeByte(value)
        } else {
            extendBuffer()
            widx += JByte.BYTES
            buffers.last.writeByte(value)
        }
        this
    }

    override def setByte(index: Int, value: Byte): Buffer = {
        val (idx, off) = offsetAtOffset(index)
        val buffer     = buffers(idx)
        buffer.setByte(buffer.readerOffset + off, value)
        this
    }

    override def writeUnsignedByte(value: Int): Buffer = {
        if (realWritableBytes >= JByte.BYTES) {
            widx += JByte.BYTES
            buffers.last.writeUnsignedByte(value)
        } else {
            extendBuffer()
            widx += JByte.BYTES
            buffers.last.writeUnsignedByte(value)
        }
        this
    }

    override def setUnsignedByte(index: Int, value: Int): Buffer = {
        val (idx, off) = offsetAtOffset(index)
        val buffer     = buffers(idx)
        buffer.setUnsignedByte(buffer.readerOffset + off, value)
        this
    }

    override def readChar: Char = {
        ridx += Character.BYTES
        val value = buffers.head.readChar
        if (buffers.head.readableBytes == 0) recycleHead()
        value
    }

    override def getChar(index: Int): Char = {
        val (idx, off) = offsetAtOffset(index)
        val buffer     = buffers(idx)
        buffer.getChar(buffer.readerOffset + off)
    }

    override def writeChar(value: Char): Buffer = {
        if (realWritableBytes >= Character.BYTES) {
            widx += Character.BYTES
            buffers.last.writeChar(value)
        } else {
            extendBuffer()
            widx += Character.BYTES
            buffers.last.writeChar(value)
        }
        this
    }

    override def setChar(index: Int, value: Char): Buffer = {
        val (idx, off) = offsetAtOffset(index)
        val buffer     = buffers(idx)
        buffer.setChar(buffer.readerOffset + off, value)
        this
    }

    override def readShort: Short = {
        ridx += JShort.BYTES
        val value = buffers.head.readShort
        if (buffers.head.readableBytes == 0) recycleHead()
        value
    }

    override def getShort(index: Int): Short = {
        val (idx, off) = offsetAtOffset(index)
        val buffer     = buffers(idx)
        buffer.getShort(buffer.readerOffset + off)
    }

    override def readUnsignedShort: Int = {
        ridx += JShort.BYTES
        val value = buffers.head.readUnsignedShort
        if (buffers.head.readableBytes == 0) recycleHead()
        value
    }

    override def getUnsignedShort(index: Int): Int = ???

    override def writeShort(value: Short): Buffer = ???

    override def setShort(index: Int, value: Short): Buffer = ???

    override def writeUnsignedShort(value: Int): Buffer = ???

    override def setUnsignedShort(index: Int, value: Int): Buffer = ???

    override def readMedium: Int = ???

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

    override def toString: String = s"AdaptiveBuffer[ridx:$ridx, widx:$widx, cap:$capacity, count:${buffers.size}]"

    override def writableBytes: Int = capacity - widx

    private def realWritableBytes: Int = if (buffers.nonEmpty) buffers.last.writableBytes else 0

    override def writeCharSequence(source: CharSequence, charset: Charset): Buffer = ???

    override def readCharSequence(length: Int, charset: Charset): CharSequence = ???

    override def writeBytes(source: Buffer): Buffer = ???

    override def writeBytes(source: Array[Byte], srcPos: Int, length: Int): Buffer = ???

    override def writeBytes(source: ByteBuffer): Buffer = ???

    override def readBytes(destination: ByteBuffer): Buffer = ???

    override def readBytes(destination: Array[Byte], destPos: Int, length: Int): Buffer = ???

    override def bytesBefore(needle1: Byte, needle2: Byte): Int = ???

    override def bytesBefore(needle1: Byte, needle2: Byte, needle3: Byte): Int = ???

    override def bytesBefore(needle1: Byte, needle2: Byte, needle3: Byte, needle4: Byte): Int = ???

    /** Split the [[PageBuffer]] chain from this [[AdaptiveBuffer]]
     *  @param offset
     *    split offset
     *  @return
     */
    private[otavia] def splitBefore(offset: Int): PageBuffer = ???

}

object AdaptiveBuffer {

    val MAX_BUFFER_SIZE: Int = Int.MaxValue - 8

    def apply(allocator: PageBufferAllocator): AdaptiveBuffer = new AdaptiveBuffer(allocator)

    private object BufferEntry {

        private val recycler = new PerThreadObjectPool[BufferEntry] {
            override protected def newObject(): BufferEntry = new BufferEntry
        }

        def apply(buffer: Buffer): BufferEntry = {
            val bufferEntry = recycler.get()
            bufferEntry.setBuffer(buffer)
            bufferEntry
        }

    }

    class BufferEntry extends Poolable {

        private var buf: Buffer | Null = _

        private def setBuffer(buffer: Buffer): Unit = buf = buffer
        def buffer: Buffer                          = buf

        override def recycle(): Unit = BufferEntry.recycler.recycle(this)

        override protected def cleanInstance(): Unit = buf = null

        override def next: BufferEntry | Null = super.next.asInstanceOf[BufferEntry | Null]

    }

    trait AdaptiveStrategy {
        def nextSize: Int
    }

    class FixedIncreaseStrategy(override val nextSize: Int) extends AdaptiveStrategy

    object QuarterPageStrategy extends FixedIncreaseStrategy(1024)

    object FixedHalfPageStrategy extends FixedIncreaseStrategy(2048)

    object FullPageStrategy extends FixedIncreaseStrategy(4096)

}
