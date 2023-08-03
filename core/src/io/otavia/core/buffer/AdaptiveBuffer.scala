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

import io.otavia.buffer.{Buffer, BufferAllocator, ByteCursor}
import io.otavia.core.buffer.AdaptiveBuffer.AdaptiveStrategy
import io.otavia.core.cache.{PerThreadObjectPool, Poolable}
import io.otavia.core.util.Chainable

import java.nio.ByteBuffer
import java.nio.channels.{FileChannel, ReadableByteChannel, WritableByteChannel}
import java.nio.charset.Charset
import scala.collection.mutable
import scala.language.unsafeNulls

/** A Adaptive allocate and release memory [[Buffer]]. */
class AdaptiveBuffer(val allocator: BufferAllocator) extends Buffer {

    private var head: AdaptiveBuffer.BufferEntry   = _
    private var tail: AdaptiveBuffer.BufferEntry   = _
    private var woffIn: AdaptiveBuffer.BufferEntry = _
    private var totalEntry: Int                    = 0

    private var cap            = 0
    private var startOffset    = 0
    private var roff           = 0
    private var woff           = 0
    private def endOffset: Int = startOffset + cap

    private var closed = false

    private var strategy: AdaptiveStrategy = AdaptiveBuffer.FullPageStrategy

    def setStrategy(adaptiveStrategy: AdaptiveStrategy): Unit = strategy = adaptiveStrategy

    inline private def currentReaderEntry: AdaptiveBuffer.BufferEntry = head
    inline private def currentWriterEntry: AdaptiveBuffer.BufferEntry = woffIn
    inline private def tailEntry: AdaptiveBuffer.BufferEntry          = tail
    inline private def writerEntryIsNull: Boolean                     = woffIn == null
    inline private def currentReaderBuffer: Buffer                    = currentReaderEntry.buffer
    inline private def currentWriterBuffer: Buffer                    = currentWriterEntry.buffer

    inline private def offsetIn(offset: Int): AdaptiveBuffer.BufferEntry | Null = if (head != null) {
        if (offset < startOffset) null
        else {
            var cursor    = head
            var endOffset = startOffset + currentReaderBuffer.capacity
            while (endOffset <= offset && cursor != null) {
                val entry: AdaptiveBuffer.BufferEntry = cursor.asInstanceOf[AdaptiveBuffer.BufferEntry]
                endOffset += entry.buffer.capacity
                cursor = entry.next
            }
            cursor
        }
    } else null

    private def offsetInOffset(offset: Int): (AdaptiveBuffer.BufferEntry | Null, Int) = if (head != null) {
        if (offset < startOffset) (null, 0)
        else {
            var cursor    = head
            var endOffset = startOffset + currentReaderBuffer.capacity
            while (endOffset <= offset && cursor != null) {
                val entry: AdaptiveBuffer.BufferEntry = cursor.asInstanceOf[AdaptiveBuffer.BufferEntry]
                endOffset += entry.buffer.capacity
                cursor = entry.next
            }
            val off =
                if (cursor == null) 0
                else {
                    val entry: AdaptiveBuffer.BufferEntry = cursor.asInstanceOf[AdaptiveBuffer.BufferEntry]
                    entry.buffer.capacity - (endOffset - offset)
                }
            (cursor, off)
        }
    } else (null, 0)

    private def recycleHead(): Unit = if (totalEntry == 0) {
        // Empty Buffer, do nothing
    } else {
        val oldHead = currentReaderEntry
        head = oldHead.next
        startOffset += oldHead.buffer.capacity
        cap -= oldHead.buffer.capacity
        totalEntry -= 1
        oldHead.buffer.close()
        oldHead.recycle()
        if (totalEntry == 0) {
            tail = null
            woffIn = null
            startOffset = 0
            roff = 0
            woff = 0
        }
    }

    private def recycleAll(): Unit = if (totalEntry == 0) {
        // Empty Buffer, do nothing
    } else {
        var cursor = head
        while (cursor != null) {
            val entry: AdaptiveBuffer.BufferEntry = cursor
            cursor = entry.next
            entry.buffer.close()
            entry.recycle()
        }
        tail = null
        woffIn = null
        cap = 0
        startOffset = 0
        roff = 0
        woff = 0
    }

    private def extendBuffer(): Unit = {
        val buffer: Buffer = allocator.allocate()
        val bufferEntry    = AdaptiveBuffer.BufferEntry(buffer)
        if (totalEntry == 0) {
            head = bufferEntry
            tail = bufferEntry
            woffIn = bufferEntry
            totalEntry += 1
            cap += buffer.capacity
        } else {
            val oldTail = tailEntry
            oldTail.next = bufferEntry
            tail = bufferEntry
            totalEntry += 1
            cap += buffer.capacity
            if (woffIn == null) woffIn = bufferEntry
        }
    }

    override def capacity: Int = cap

    override def readerOffset: Int = roff

    private def checkReadBounds(index: Int): Unit = {
        if (totalEntry == 0 && index != 0) throw new IndexOutOfBoundsException("The buffer is empty")
        if (index > woff) throw new IndexOutOfBoundsException("The new readerOffset is bigger than writerOffset")
        if (index < startOffset)
            throw new IndexOutOfBoundsException(
              s"The memory set by $index has been release already! " +
                  s"Current the minimum readerOffset can be set as $startOffset"
            )
    }

    override def readerOffset(offset: Int): Buffer = {
        checkReadBounds(offset)
        if (offset == woff) recycleAll()
        else {
            val newReaderEntry = offsetIn(offset)
            while ((head != newReaderEntry) && (head != null)) {
                recycleHead()
            }
            roff = offset
        }
        this
    }

    override def writerOffset: Int = woff

    private def checkWriteBound(index: Int): Unit = {
        if (index < roff)
            throw new IndexOutOfBoundsException(s"Set writerOffset $index is little than readerOffset $roff")
    }

    override def writerOffset(offset: Int): Buffer = {
        checkWriteBound(offset)
        if (offset > woff) ensureWritable(offset - woff)
        woff = offset
        woffIn = offsetIn(woff)
        this
    }

    override def fill(value: Byte): Buffer = {
        var cursor = head
        while (cursor != null) {
            val entry: AdaptiveBuffer.BufferEntry = cursor
            entry.buffer.fill(value)
            cursor = entry.next
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
    def writeBytes(source: AdaptiveBuffer, length: Int): this.type = ???

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

    override def readByte: Byte = ???

    override def getByte(index: Int): Byte = ???

    override def readUnsignedByte: Int = ???

    override def getUnsignedByte(index: Int): Int = ???

    override def writeByte(value: Byte): Buffer = ???

    override def setByte(index: Int, value: Byte): Buffer = ???

    override def writeUnsignedByte(value: Int): Buffer = ???

    override def setUnsignedByte(index: Int, value: Int): Buffer = ???

    override def readChar: Char = ???

    override def getChar(index: Int): Char = ???

    override def writeChar(value: Char): Buffer = ???

    override def setChar(index: Int, value: Char): Buffer = ???

    override def readShort: Short = ???

    override def getShort(index: Int): Short = ???

    override def readUnsignedShort: Int = ???

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

    override def toString: String = s"AdaptiveBuffer[roff:$roff, woff:$woff, cap:$cap, count:$totalEntry]"

    override def writableBytes: Int = endOffset - writerOffset

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

}

object AdaptiveBuffer {

    val MAX_BUFFER_SIZE: Int                              = Int.MaxValue - 8
    def apply(allocator: BufferAllocator): AdaptiveBuffer = new AdaptiveBuffer(allocator)

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
        def buffer: Buffer                          = buf.nn

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
