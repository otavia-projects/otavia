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

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.{FileChannel, ReadableByteChannel, WritableByteChannel}
import java.nio.charset.Charset
import scala.language.unsafeNulls

/** A wrapper of [[java.nio.ByteBuffer]], with separate reader and writer offsets.
 *
 *  A buffer is a logically sequential stretch of memory with a certain capacity, an offset for writing, and an offset
 *  for reading. Buffers may be composed of multiple components, where each component is a guaranteed contiguous chunk
 *  of memory.
 *
 *  <h3>Creating a buffer</h3> Buffers are created by allocators, and their allocate family of methods. A number of
 *  standard allocators exist, and are available through static methods on the BufferAllocator interface.
 *
 *  <h3>Buffer life cycle</h3> The buffer has a life cycle, where it is allocated, used, and deallocated. When the
 *  buffer is initially allocated, a pairing close() call will deallocate it.
 *
 *  <h3>Thread-safety</h3> Buffer are not thread-safe.
 *
 *  <h3>Accessing data</h3> Data access methods fall into two classes:
 *    1. Access that are based on, and updates, the read or write offset positions.
 *       - These accessor methods are typically called readX or writeX.
 *    1. Access that take offsets as arguments, and do not update read or write offset positions.
 *       - These accessor methods are typically called getX or setX.
 *
 *  A buffer contains two mutable offset positions: one for reading and one for writing. These positions use zero-based
 *  indexing , such that the first byte of data in the buffer is placed at offset 0, and the last byte in the buffer is
 *  at offset capacity - 1. The readerOffset() is the offset into the buffer from which the next read will take place,
 *  and is initially zero. The reader offset must always be less than or equal to the writerOffset(). The writerOffset()
 *  is likewise the offset into the buffer where the next write will take place. The writer offset is also initially
 *  zero, and must be less than or equal to the capacity.
 *
 *  This carves the buffer into three regions, as demonstrated by this diagram: <pre>
 *  | discardable bytes | readable bytes | writable bytes |
 *  |:------------------|:--------------:|:--------------:|
 *  |                   |   (CONTENT)    |                |
 *  |                   |                |                |
 *  0 <= readerOffset <= writerOffset <= capacity </pre>
 *
 *  <h3>Byte Order</h3> Buffers are always big endian, and this cannot be changed. Usages that need to get, set, read,
 *  or write, little-endian values will have to flip the byte order of the values they read and write.
 *
 *  <h3>Splitting buffers</h3> The split() method breaks a buffer into two. The two buffers will share the underlying
 *  memory, but their regions will not overlap, ensuring that the memory is safely shared between the two. Splitting a
 *  buffer is useful for when you want to hand over a region of a buffer to some other, perhaps unknown, piece of code,
 *  and relinquish your ownership of that buffer region in the process. Examples include aggregating messages into an
 *  accumulator buffer, and sending messages down the pipeline for further processing, as split buffer regions, once
 *  their data has been received in its entirety. If you instead wish to temporarily share a region of a buffer, you
 *  will have to pass offset and length along with the buffer, or you will have to make a copy of the region.
 *
 *  <h3>Buffers as constants</h3> Sometimes, the same bit of data will be processed or transmitted over and over again.
 *  In such cases, it can be tempting to allocate and fill a buffer once, and then reuse it. Such reuse must be done
 *  carefully, however, to avoid a number of bugs. The [[BufferAllocator]] has a
 *  BufferAllocator.constBufferSupplier(byte[]) method that solves this, and prevents these bugs from occurring.
 */
trait Buffer {

    /** The capacity of this buffer, that is, the maximum number of bytes it can contain.
     *
     *  @return
     *    The capacity in bytes.
     */
    def capacity: Int

    /** Get the current reader offset. The next read will happen from this byte offset into the buffer.
     *
     *  @return
     *    The current reader offset.
     */
    def readerOffset: Int

    /** Set the reader offset. Make the next read happen from the given offset into the buffer.
     *
     *  @param offset
     *    The reader offset to set.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    if the specified [[offset]] is less than zero or greater than the current [[writerOffset]].
     *  @throws BufferClosedException
     *    if this buffer is closed.
     */
    def readerOffset(offset: Int): Buffer

    /** Move the reader offset forward by the given delta.
     *
     *  @param delta
     *    to accumulate.
     *  @return
     *    This buffer instance.
     *  @throws IndexOutOfBoundsException
     *    if the new reader offset is greater than the current [[writerOffset]].
     *  @throws IllegalArgumentException
     *    if the given delta is negative.
     *  @throws BufferClosedException
     *    if this buffer is closed.
     */
    def skipReadableBytes(delta: Int): Buffer = {
        if (delta < 0) throw IllegalArgumentException("skipReadableBytes delta can't be negative.")
        readerOffset(readerOffset + delta)
        this
    }

    /** Get the current writer offset. The next write will happen at this byte offset into the buffer.
     *
     *  @return
     *    The current writer offset.
     */
    def writerOffset: Int

    /** Set the writer offset. Make the next write happen at the given offset.
     *
     *  @param offset
     *    The writer offset to set.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    if the specified [[offset]] is less than the current [[readerOffset]] or greater than [[capacity]].
     *  @throws BufferClosedException
     *    if this buffer is closed.
     */
    def writerOffset(offset: Int): Buffer

    /** Move the writer offset to ahead by the given delta.
     *
     *  @param delta
     *    to accumulate.
     *  @return
     *    This buffer instance.
     *  @throws IndexOutOfBoundsException
     *    if the new writer offset is greater than [[capacity]].
     *  @throws IllegalArgumentException
     *    if the given delta is negative.
     *  @throws BufferClosedException
     *    if this buffer is closed.
     */
    def skipWritableBytes(delta: Int): Buffer = {
        if (delta < 0) throw IllegalArgumentException("skipWritableBytes delta can't be negative.")
        writerOffset(writerOffset + delta)
        this
    }

    /** Returns the number of readable bytes which is equal to [[writerOffset]] - [[readerOffset]]. */
    def readableBytes: Int = writerOffset - readerOffset

    /** Returns the number of writable bytes which is equal to [[capacity]] - [[writerOffset]] . */
    def writableBytes: Int = capacity - writerOffset

    /** Fills the buffer with the given byte value. This method does not respect the [[readerOffset]] or
     *  [[writerOffset]], but copies the full capacity of the buffer. The [[readerOffset]] and [[writerOffset]] are not
     *  modified.
     *
     *  @param value
     *    The byte value to write at every offset in the buffer.
     *  @return
     *    This Buffer.
     */
    def fill(value: Byte): Buffer

    /** Queries if this buffer is backed by native memory, or not.
     *
     *  @return
     *    true if this buffer is backed by native, off-heap, memory. Otherwise, false, if this buffer is backed by
     *    on-heap memory.
     */
    def isDirect: Boolean

    /** Copies the given length of data from this buffer into the given destination array, beginning at the given source
     *  position in this buffer, and the given destination position in the destination array. <p> This method does not
     *  read or modify the [[writerOffset]] or the [[readerOffset]].
     *
     *  @param srcPos
     *    The byte offset into this buffer from where the copying should start; the byte at this offset in this buffer
     *    will be copied to the [[destPos]] index in the [[dest]] array.
     *  @param dest
     *    The destination byte array.
     *  @param destPos
     *    The index into the [[dest]] array from where the copying should start.
     *  @param length
     *    The number of bytes to copy.
     *  @throws IndexOutOfBoundsException
     *    if the source or destination positions, or the length, are negative, or if the resulting end positions reaches
     *    beyond the end of either this buffer, or the destination array.
     *  @throws BufferClosedException
     *    if this buffer is closed.
     */
    def copyInto(srcPos: Int, dest: Array[Byte], destPos: Int, length: Int): Unit

    /** Copies the given length of data from this buffer into the given destination byte buffer, beginning at the given
     *  source position in this buffer, and the given destination position in the destination byte buffer.
     *
     *  <p> This method does not read or modify the [[writerOffset]] or the [[readerOffset]], nor is the position of the
     *  destination buffer changed.
     *
     *  <p> The position and limit of the destination byte buffer are also ignored, and do not influence [[destPos]] or
     *  [[length]].
     *
     *  @param srcPos
     *    The byte offset into this buffer from where the copying should start; the byte at this offset in this buffer
     *    will be copied to the [[destPos]] index in the [[dest]] [[ByteBuffer]].
     *  @param dest
     *    The destination byte buffer.
     *  @param destPos
     *    The index into the [[dest]] [[ByteBuffer]] from where the copying should start.
     *  @param length
     *    The number of bytes to copy.
     *  @throws NullPointerException
     *    if the destination buffer is null.
     *  @throws IndexOutOfBoundsException
     *    if the source or destination positions, or the length, are negative, or if the resulting end positions reach
     *    beyond the end of either this buffer or the destination [[ByteBuffer]].
     *  @throws java.nio.ReadOnlyBufferException
     *    if the destination byte buffer is read-only.
     *  @throws BufferClosedException
     *    if this buffer is closed.
     */
    def copyInto(srcPos: Int, dest: ByteBuffer, destPos: Int, length: Int): Unit

    /** Copies the given length of data from this buffer into the given destination buffer, beginning at the given
     *  source position in this buffer, and the given destination position in the destination buffer.
     *
     *  <p> This method does not read or modify the [[writerOffset]] or the [[readerOffset]] on this buffer, nor on the
     *  destination buffer.
     *
     *  <p> The read and write offsets of the destination buffer are also ignored, and do not influence [[destPos]] or
     *  [[length]].
     *
     *  @param srcPos
     *    The byte offset into this buffer from where the copying should start; the byte at this offset in this buffer
     *    will be copied to the [[destPos]] index in the [[dest]] buffer.
     *  @param dest
     *    The destination buffer.
     *  @param destPos
     *    The index into the [[dest]] buffer from where the copying should start.
     *  @param length
     *    The number of bytes to copy.
     *  @throws NullPointerException
     *    if the destination buffer is null.
     *  @throws IndexOutOfBoundsException
     *    if the source or destination positions, or the length, are negative, or if the resulting end positions reaches
     *    beyond the end of either this buffer, or the destination buffer.
     *  @throws BufferReadOnlyException
     *    if the destination buffer is read-only.
     *  @throws BufferClosedException
     *    if this or the destination buffer is closed.
     */
    def copyInto(srcPos: Int, dest: Buffer, destPos: Int, length: Int): Unit

    /** Read from this buffer and write to the given channel. The number of bytes actually written to the channel are
     *  returned. No more than the given [[length]] of bytes, or the number of [[readableBytes]], will be written to the
     *  channel, whichever is smaller. If the channel has a position, then it will be advanced by the number of bytes
     *  written. The [[readerOffset]] of this buffer will likewise be advanced by the number of bytes written.
     *
     *  @param channel
     *    The channel to write to.
     *  @param length
     *    The maximum number of bytes to write.
     *  @return
     *    The actual number of bytes written, possibly zero.
     *  @throws IOException
     *    If the write-operation on the channel failed for some reason.
     */
    @throws[IOException]
    def transferTo(channel: WritableByteChannel, length: Int): Int

    /** Read from the given channel starting from the given position and write to this buffer. The number of bytes
     *  actually read from the channel are returned, or -1 is returned if the channel has reached the end-of-stream. No
     *  more than the given [[length]] of bytes, or the number of [[writableBytes]], will be read from the channel,
     *  whichever is smaller. The channel's position is not modified. The [[writerOffset]] of this buffer will likewise
     *  be advanced by the number of bytes read.
     *
     *  @param channel
     *    The channel to read from.
     *  @param position
     *    The file position.
     *  @param length
     *    The maximum number of bytes to read.
     *  @return
     *    The actual number of bytes read, possibly zero, or -1 if the end-of-stream has been reached.
     *  @throws IOException
     *    If the read-operation on the channel failed for some reason.
     */
    @throws[IOException]
    def transferFrom(channel: FileChannel, position: Long, length: Int): Int

    /** Read from the given channel and write to this buffer. The number of bytes actually read from the channel are
     *  returned, or -1 is returned if the channel has reached the end-of-stream. No more than the given [[length]] of
     *  bytes, or the number of [[writableBytes]], will be read from the channel, whichever is smaller. If the channel
     *  has a position, then it will be advanced by the number of bytes read. The [[writerOffset]] of this buffer will
     *  likewise be advanced by the number of bytes read.
     *
     *  @param channel
     *    The channel to read from.
     *  @param length
     *    The maximum number of bytes to read.
     *  @return
     *    The actual number of bytes read, possibly zero, or -1 if the end-of-stream has been reached.
     *  @throws IOException
     *    If the read-operation on the channel failed for some reason.
     */
    @throws[IOException]
    def transferFrom(channel: ReadableByteChannel, length: Int): Int

    /** Writes into this buffer, all the bytes from the given [[source]] using the passed [[charset]]. This updates the
     *  [[writerOffset]] of this buffer.
     *
     *  @param source
     *    [[CharSequence]] to read from.
     *  @param charset
     *    [[Charset]] to use for writing.
     *  @return
     *    This buffer.
     */
    def writeCharSequence(source: CharSequence, charset: Charset): Buffer

    /** Reads a [[CharSequence]] of the passed [[length]] using the passed [[Charset]]. This updates the
     *  [[readerOffset]] of this buffer.
     *
     *  @param length
     *    of [[CharSequence]] to read.
     *  @param charset
     *    of the bytes to be read.
     *  @return
     *    [[CharSequence]] read from this buffer.
     *  @throws IndexOutOfBoundsException
     *    if the passed [[length]] is more than the [[readableBytes]] of this buffer.
     */
    def readCharSequence(length: Int, charset: Charset): CharSequence

    /** Writes into this buffer, all the readable bytes from the given buffer. This updates the [[writerOffset]] of this
     *  buffer, and the [[readerOffset]] of the given buffer.
     *
     *  @param source
     *    The buffer to read from.
     *  @return
     *    This buffer.
     */
    def writeBytes(source: Buffer): Buffer

    /** Writes into this buffer, the given number of bytes from the byte array. This updates the [[writerOffset]] of
     *  this buffer by the length argument.
     *
     *  @param source
     *    The byte array to read from.
     *  @param srcPos
     *    Position in the [[source]] from where bytes should be written to this buffer.
     *  @param length
     *    The number of bytes to copy.
     *  @return
     *    This buffer.
     */
    def writeBytes(source: Array[Byte], srcPos: Int, length: Int): Buffer

    /** Writes into this buffer, all the bytes from the given byte array. This updates the [[writerOffset]] of this
     *  buffer by the length of the array.
     *
     *  @param source
     *    The byte array to read from.
     *  @return
     *    This buffer.
     */
    def writeBytes(source: Array[Byte]): Buffer = writeBytes(source, 0, source.length)

    /** Writes into this buffer from the source [[ByteBuffer]]. This updates the [[writerOffset]] of this buffer and
     *  also the position of the source [[ByteBuffer]].
     *
     *  <p> Note: the behaviour is undefined if the given [[ByteBuffer]] is an alias for the memory in this buffer.
     *
     *  @param source
     *    The [[ByteBuffer]] to read from.
     *  @return
     *    This buffer.
     */
    def writeBytes(source: ByteBuffer): Buffer

    /** Read from this buffer, into the destination [[ByteBuffer]] This updates the [[readerOffset]] of this buffer and
     *  also the position of the destination [[ByteBuffer]].
     *
     *  <p> Note: the behaviour is undefined if the given [[ByteBuffer]] is an alias for the memory in this buffer.
     *
     *  @param destination
     *    The [[ByteBuffer]] to write into.
     *  @return
     *    This buffer.
     */
    def readBytes(destination: ByteBuffer): Buffer

    /** Read from this buffer, into the destination array, the given number of bytes. This updates the [[readerOffset]]
     *  of this buffer by the length argument.
     *
     *  @param destination
     *    The byte array to write into.
     *  @param destPos
     *    Position in the [[destination]] to where bytes should be written from this buffer.
     *  @param length
     *    The number of bytes to copy.
     *  @return
     *    This buffer.
     */
    def readBytes(destination: Array[Byte], destPos: Int, length: Int): Buffer

    /** Resets the [[readerOffset]] and the [[writerOffset]] on this buffer to zero, and return this buffer.
     *
     *  @return
     *    This buffer instance.
     */
    def resetOffsets(): Buffer = {
        readerOffset(0)
        writerOffset(0)
        this
    }

    /** Get the number of [[readableBytes]], until the given [[needle]] is found in this buffer. If the needle is not
     *  found, -1 is returned.
     *
     *  <p> This method does not modify the [[readerOffset]] or the [[writerOffset]].
     *
     *  @param needle
     *    The byte value to search for.
     *  @return
     *    The offset, relative to the current [[readerOffset]], of the found value, or -1 if none was found.
     */
    def bytesBefore(needle: Byte): Int

    /** Get the number of [[readableBytes]], until the given [[needle1]],[[needle2]] bytes is found in this buffer. If
     *  the needle is not found, -1 is returned.
     *
     *  <p> This method does not modify the [[readerOffset]] or the [[writerOffset]].
     *
     *  @param needle1
     *    The first byte value to search for.
     *  @param needle2
     *    The second byte value to search for.
     *  @return
     *    The offset, relative to the current [[readerOffset]], of the found value, or -1 if none was found.
     */
    def bytesBefore(needle1: Byte, needle2: Byte): Int

    /** Get the number of [[readableBytes]], until the given [[needle1]],[[needle2]],[[needle3]] bytes is found in this
     *  buffer. If the needle is not found, -1 is returned.
     *
     *  <p> This method does not modify the [[readerOffset]] or the [[writerOffset]].
     *
     *  @param needle1
     *    The first byte value to search for.
     *  @param needle2
     *    The second byte value to search for.
     *  @param needle3
     *    The third byte value to search for.
     *  @return
     *    The offset, relative to the current [[readerOffset]], of the found value, or -1 if none was found.
     */
    def bytesBefore(needle1: Byte, needle2: Byte, needle3: Byte): Int

    /** Get the number of [[readableBytes]], until the given [[needle]] is found in this buffer. The found offset will
     *  be the offset into this buffer, relative to its [[readerOffset]], of the first byte of a sequence that matches
     *  all readable bytes in the given [[needle]] buffer. If the needle is not found, -1 is returned.
     *
     *  <p> This method does not modify the [[readerOffset]] or the [[writerOffset]].
     *
     *  @param needle
     *    The buffer value to search for.
     *  @return
     *    The offset, relative to the current [[readerOffset]], of the found value, or -1 if none was found.
     */
    def bytesBefore(needle: Buffer): Int

    /** Opens a cursor to iterate the given number bytes of this buffer, starting at the given offset. The
     *  [[readerOffset]] and [[writerOffset]] are not modified by the cursor. <p> Care should be taken to ensure that
     *  the buffer's lifetime extends beyond the cursor and the iteration, and that the [[readerOffset]] and
     *  [[writerOffset]] are not modified while the iteration takes place. Otherwise, unpredictable behaviour might
     *  result.
     *
     *  @param fromOffset
     *    The offset into the buffer where iteration should start. The first byte read from the iterator will be the
     *    byte at this offset.
     *  @param length
     *    The number of bytes to iterate.
     *  @return
     *    A [[ByteCursor]] for the given stretch of bytes of this buffer.
     *  @throws IllegalArgumentException
     *    if the length is negative, or if the region given by the [[fromOffset]] and the [[length]] reaches outside the
     *    bounds of this buffer.
     */
    def openCursor(fromOffset: Int, length: Int): ByteCursor

    /** Opens a cursor to iterate the readable bytes of this buffer, in reverse. The [[readerOffset]] and
     *  [[writerOffset]] are not modified by the cursor.
     *
     *  <p> Care should be taken to ensure that the buffer's lifetime extends beyond the cursor and the iteration, and
     *  that the [[readerOffset]] and [[writerOffset]] are not modified while the iteration takes place. Otherwise,
     *  unpredictable behaviour might result.
     *
     *  @return
     *    A [[ByteCursor]] for the readable bytes of this buffer.
     */
    def openReverseCursor: ByteCursor = {
        val woff = writerOffset
        openReverseCursor(if (woff == 0) 0 else woff - 1, readableBytes)
    }

    /** Opens a cursor to iterate the given number bytes of this buffer, in reverse, starting at the given offset. The
     *  [[readerOffset]] and [[writerOffset]] are not modified by the cursor.
     *
     *  <p> Care should be taken to ensure that the buffer's lifetime extends beyond the cursor and the iteration, and
     *  that the [[readerOffset]] and [[writerOffset]] are not modified while the iteration takes place. Otherwise,
     *  unpredictable behaviour might result.
     *
     *  @param fromOffset
     *    The offset into the buffer where iteration should start. The first byte read from the iterator will be the
     *    byte at this offset.
     *  @param length
     *    The number of bytes to iterate.
     *  @return
     *    A [[ByteCursor]] for the given stretch of bytes of this buffer.
     *  @throws IllegalArgumentException
     *    if the length is negative, or if the region given by the [[fromOffset]] and the [[length]] reaches outside the
     *    bounds of this buffer.
     */
    def openReverseCursor(fromOffset: Int, length: Int): ByteCursor

    /** Ensures that this buffer has at least the given number of bytes of [[writableBytes]]. If this buffer already has
     *  the necessary space, then this method returns immediately. If this buffer does not already have the necessary
     *  space, then space will be made available in one or all of the following available ways:
     *
     *  <ul> <li> If [[allowCompaction]] is true, and sum of the read and writable bytes would be enough to satisfy the
     *  request, and it (depending on the buffer implementation) seems faster and easier to compact the existing buffer
     *  rather than allocation a new buffer, then the requested bytes will be made available that way. The compaction
     *  will not necessarily work the same way as the [[compact]] method, as the implementation may be able to make the
     *  requested bytes available with less effort than is strictly mandated by the [[compact]] method. </li> <li>
     *  Regardless of the value of the [[allowCompaction]], the implementation may make more space available by just
     *  allocating more or larger buffers. This allocation would use the same [[BufferAllocator]] that this buffer was
     *  created with. </li> <li> If [[allowCompaction]] is true, then the implementation may choose to do a combination
     *  of compaction and allocation. </li> </ul>
     *
     *  @param size
     *    The requested number of bytes of space that should be available for writing.
     *  @return
     *    This buffer instance.
     *  @param minimumGrowth
     *    The minimum number of bytes to grow by. If it is determined that memory should be allocated and copied, make
     *    sure that the new memory allocation is bigger than the old one by at least this many bytes. This way, the
     *    buffer can grow by more than what is immediately necessary, thus amortising the costs of allocating and
     *    copying.
     *  @param allowCompaction
     *    true if the method is allowed to modify the [[readerOffset]] and [[writerOffset]], otherwise false.
     *
     *  @throws IllegalArgumentException
     *    if [[size]] or [[minimumGrowth]] are negative.
     *  @throws IllegalStateException
     *    if this buffer is in a bad state.
     */
    def ensureWritable(size: Int, minimumGrowth: Int, allowCompaction: Boolean): Buffer

    /** Ensures that this buffer has at least the given number of bytes of [[writableBytes]] available space for
     *  writing. If this buffer already has the necessary space, then this method returns immediately. If this buffer
     *  does not already have the necessary space, then it will be expanded using the [[BufferAllocator]] the buffer was
     *  created with. This method is the same as calling [[ensureWritable]] where [[allowCompaction]] is true.
     *
     *  @param size
     *    The requested number of bytes of space that should be available for writing.
     *  @return
     *    This buffer instance.
     *  @throws IllegalStateException
     *    if this buffer is in a bad state.
     *  @throws BufferClosedException
     *    if this buffer is closed.
     */
    def ensureWritable(size: Int): Buffer = {
        ensureWritable(size, capacity, true)
        this
    }

    /** Close this [[Buffer]] */
    def close(): Unit

    // data accessor

    /** Read the byte value at the current [[readerOffset]], and increases the reader offset by 1. The value is read
     *  using a two's complement 8-bit encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @return
     *    The byte value at the current reader offset.
     *  @throws IndexOutOfBoundsException
     *    If [[readableBytes]] is less than 1.
     */
    def readByte: Byte

    /** Get the byte value at the given reader offset. The [[readerOffset]] is not modified. The value is read using a
     *  two's complement 8-bit encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param ridx
     *    The read offset, an absolute offset into this buffer, to read from.
     *  @return
     *    The byte value at the given offset.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus 1.
     */
    def getByte(ridx: Int): Byte

    /** Read the boolean value at the current [[readerOffset]], and increases the reader offset by
     *  [[java.lang.Byte.BYTES]]. A boolean gets read as a byte from this buffer. All byte values which are not equal to
     *  zero are considered as the boolean value true, zero represents false.
     *
     *  @return
     *    The boolean value at the current reader offset.
     *  @throws IndexOutOfBoundsException
     *    If [[readableBytes]] is less than [[java.lang.Byte.BYTES]].
     */
    def readBoolean: Boolean = readByte != 0

    /** Get the boolean value at the given reader offset. The [[readerOffset]] is not modified. A boolean gets read as a
     *  byte from this buffer. All byte values which are not equal to zero are considered as the boolean value true,
     *  zero represents false.
     *
     *  @param roff
     *    The read offset, an absolute offset into this buffer, to read from.
     *  @return
     *    The boolean value at the given offset.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Byte.BYTES]].
     */
    def getBoolean(roff: Int): Boolean = getByte(roff) != 0

    /** Write the given boolean value at the current [[writerOffset]], and increase the writer offset by
     *  [[java.lang.Byte.BYTES]]. A boolean gets written as a byte to this buffer. All byte values which are not equal
     *  to zero are considered as the boolean value true, zero represents false.
     *
     *  @param value
     *    The boolean value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    If [[writableBytes]] is less than [[java.lang.Byte.BYTES]], and the [[capacity]] buffer capacity cannot be
     *    automatically increased.
     */
    def writeBoolean(value: Boolean): Buffer = writeByte((if (value) 1 else 0).toByte)

    /** Set the given boolean value at the given write offset. The [[writerOffset]] is not modified. A boolean gets
     *  written as a byte to this buffer. All byte values which are not equal to zero are considered as the boolean
     *  value true, zero represents false.
     *
     *  @param woff
     *    The write offset, an absolute offset into this buffer to write to.
     *  @param value
     *    The boolean value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Byte.BYTES]].
     */
    def setBoolean(woff: Int, value: Boolean): Buffer = setByte(woff, (if (value) 1 else 0).toByte)

    /** Read the unsigned byte value at the current [[readerOffset]], and increases the reader offset by
     *  [[java.lang.Byte.BYTES]]. The value is read using an unsigned two's complement 8-bit encoding, in
     *  [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @return
     *    The unsigned byte value at the current reader offset.
     *  @throws IndexOutOfBoundsException
     *    If [[readableBytes]] is less than [[java.lang.Byte.BYTES]].
     */
    def readUnsignedByte: Int

    /** Get the unsigned byte value at the given reader offset. The [[readerOffset]] is not modified. The value is read
     *  using an unsigned two's complement 8-bit encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param roff
     *    The read offset, an absolute offset into this buffer, to read from.
     *  @return
     *    The unsigned byte value at the given offset.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Byte.BYTES]].
     */
    def getUnsignedByte(roff: Int): Int

    /** Write the given byte value at the current [[writerOffset]], and increase the writer offset by
     *  [[java.lang.Byte.BYTES]]. The value is written using a two's complement 8-bit encoding, in
     *  [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param value
     *    The byte value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    If [[writableBytes]] is less than [[java.lang.Byte.BYTES]], and the [[capacity]] buffer capacity cannot be
     *    automatically increased.
     */
    def writeByte(value: Byte): Buffer

    /** Set the given byte value at the given write offset. The [[writerOffset]] is not modified. The value is written
     *  using a two's complement 8-bit encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param woff
     *    The write offset, an absolute offset into this buffer to write to.
     *  @param value
     *    The byte value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Byte.BYTES]].
     */
    def setByte(woff: Int, value: Byte): Buffer

    /** Write the given unsigned byte value at the current [[writerOffset]], and increase the writer offset by
     *  [[java.lang.Byte.BYTES]]. The value is written using an unsigned two's complement 8-bit encoding, in
     *  [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param value
     *    The int value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    If [[writableBytes]] is less than [[java.lang.Byte.BYTES]], and the [[capacity]] buffer capacity cannot be
     *    automatically increased.
     */
    def writeUnsignedByte(value: Int): Buffer

    /** Set the given unsigned byte value at the given write offset. The [[writerOffset]] is not modified. The value is
     *  written using an unsigned two's complement 8-bit encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param woff
     *    The write offset, an absolute offset into this buffer to write to.
     *  @param value
     *    The int value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Byte.BYTES]].
     */
    def setUnsignedByte(woff: Int, value: Int): Buffer

    /** Read the char value at the current [[readerOffset]], and increases the reader offset by 2. The value is read
     *  using a 2-byte UTF-16 encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @return
     *    The char value at the current reader offset.
     *  @throws IndexOutOfBoundsException
     *    If [[readableBytes]] is less than 2.
     */
    def readChar: Char

    /** Get the char value at the given reader offset. The [[readerOffset]] is not modified. The value is read using a
     *  2-byte UTF-16 encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param roff
     *    The read offset, an absolute offset into this buffer, to read from.
     *  @return
     *    The char value at the given offset.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus 2.
     */
    def getChar(roff: Int): Char

    /** Write the given char value at the current [[writerOffset]], and increase the writer offset by 2. The value is
     *  written using a 2-byte UTF-16 encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param value
     *    The char value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    If [[writableBytes]] is less than 2, and the [[capacity]] buffer capacity cannot be automatically increased.
     */
    def writeChar(value: Char): Buffer

    /** Set the given char value at the given write offset. The [[writerOffset]] is not modified. The value is written
     *  using a 2-byte UTF-16 encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param woff
     *    The write offset, an absolute offset into this buffer to write to.
     *  @param value
     *    The char value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus 2.
     */
    def setChar(woff: Int, value: Char): Buffer

    /** Read the short value at the current [[readerOffset]], and increases the reader offset by
     *  [[java.lang.Short.BYTES]] [[java.lang.Short.BYTES]]. The value is read using a two's complement 16-bit encoding,
     *  in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @return
     *    The short value at the current reader offset.
     *  @throws IndexOutOfBoundsException
     *    If [[readableBytes]] is less than [[java.lang.Short.BYTES]].
     */
    def readShort: Short

    /** Get the short value at the given reader offset. The [[readerOffset]] is not modified. The value is read using a
     *  two's complement 16-bit encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param roff
     *    The read offset, an absolute offset into this buffer, to read from.
     *  @return
     *    The short value at the given offset.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Short.BYTES]].
     */
    def getShort(roff: Int): Short

    /** Read the unsigned short value at the current [[readerOffset]], and increases the reader offset by
     *  [[java.lang.Short.BYTES]]. The value is read using an unsigned two's complement 16-bit encoding, in
     *  [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @return
     *    The unsigned short value at the current reader offset.
     *  @throws IndexOutOfBoundsException
     *    If [[readableBytes]] is less than [[java.lang.Short.BYTES]].
     */
    def readUnsignedShort: Int

    /** Get the unsigned short value at the given reader offset. The [[readerOffset]] is not modified. The value is read
     *  using an unsigned two's complement 16-bit encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param roff
     *    The read offset, an absolute offset into this buffer, to read from.
     *  @return
     *    The unsigned short value at the given offset.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Short.BYTES]].
     */
    def getUnsignedShort(roff: Int): Int

    /** Write the given short value at the current [[writerOffset]], and increase the writer offset by
     *  [[java.lang.Short.BYTES]]. The value is written using a two's complement 16-bit encoding, in
     *  [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param value
     *    The short value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    If [[writableBytes]] is less than [[java.lang.Short.BYTES]], and the [[capacity]] buffer capacity cannot be
     *    automatically increased.
     */
    def writeShort(value: Short): Buffer

    /** Set the given short value at the given write offset. The [[writerOffset]] is not modified. The value is written
     *  using a two's complement 16-bit encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param woff
     *    The write offset, an absolute offset into this buffer to write to.
     *  @param value
     *    The short value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Short.BYTES]].
     */
    def setShort(woff: Int, value: Short): Buffer

    /** Write the given unsigned short value at the current [[writerOffset]], and increase the writer offset by
     *  [[java.lang.Short.BYTES]]. The value is written using an unsigned two's complement 16-bit encoding, in
     *  [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param value
     *    The int value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    If [[writableBytes]] is less than [[java.lang.Short.BYTES]], and the [[capacity]] buffer capacity cannot be
     *    automatically increased.
     */
    def writeUnsignedShort(value: Int): Buffer

    /** Set the given unsigned short value at the given write offset. The [[writerOffset]] is not modified. The value is
     *  written using an unsigned two's complement 16-bit encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param woff
     *    The write offset, an absolute offset into this buffer to write to.
     *  @param value
     *    The int value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Short.BYTES]].
     */
    def setUnsignedShort(woff: Int, value: Int): Buffer

    /** Read the int value at the current [[readerOffset]], and increases the reader offset by 3. The value is read
     *  using a two's complement 24-bit encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @return
     *    The int value at the current reader offset.
     *  @throws IndexOutOfBoundsException
     *    If [[readableBytes]] is less than 3.
     */
    def readMedium: Int

    /** Get the int value at the given reader offset. The [[readerOffset]] is not modified. The value is read using a
     *  two's complement 24-bit encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param roff
     *    The read offset, an absolute offset into this buffer, to read from.
     *  @return
     *    The int value at the given offset.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus 3.
     */
    def getMedium(roff: Int): Int

    /** Read the unsigned int value at the current [[readerOffset]], and increases the reader offset by 3. The value is
     *  read using an unsigned two's complement 24-bit encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @return
     *    The unsigned int value at the current reader offset.
     *  @throws IndexOutOfBoundsException
     *    If [[readableBytes]] is less than 3.
     */
    def readUnsignedMedium: Int

    /** Get the unsigned int value at the given reader offset. The [[readerOffset]] is not modified. The value is read
     *  using an unsigned two's complement 24-bit encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param roff
     *    The read offset, an absolute offset into this buffer, to read from.
     *  @return
     *    The unsigned int value at the given offset.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus 3.
     */
    def getUnsignedMedium(roff: Int): Int

    /** Write the given int value at the current [[writerOffset]], and increase the writer offset by 3. The value is
     *  written using a two's complement 24-bit encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param value
     *    The int value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    If [[writableBytes]] is less than 3, and the [[capacity]] buffer capacity cannot be automatically increased.
     */
    def writeMedium(value: Int): Buffer

    /** Set the given int value at the given write offset. The [[writerOffset]] is not modified. The value is written
     *  using a two's complement 24-bit encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param woff
     *    The write offset, an absolute offset into this buffer to write to.
     *  @param value
     *    The int value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus 3.
     */
    def setMedium(woff: Int, value: Int): Buffer

    /** Write the given unsigned int value at the current [[writerOffset]], and increase the writer offset by 3. The
     *  value is written using an unsigned two's complement 24-bit encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte
     *  order.
     *
     *  @param value
     *    The int value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    If [[writableBytes]] is less than 3, and the [[capacity]] buffer capacity cannot be automatically increased.
     */
    def writeUnsignedMedium(value: Int): Buffer

    /** Set the given unsigned int value at the given write offset. The [[writerOffset]] is not modified. The value is
     *  written using an unsigned two's complement 24-bit encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param woff
     *    The write offset, an absolute offset into this buffer to write to.
     *  @param value
     *    The int value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus 3.
     */
    def setUnsignedMedium(woff: Int, value: Int): Buffer

    /** Read the int value at the current [[readerOffset]], and increases the reader offset by
     *  [[java.lang.Integer.BYTES]]. The value is read using a two's complement 32-bit encoding, in
     *  [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @return
     *    The int value at the current reader offset.
     *  @throws IndexOutOfBoundsException
     *    If [[readableBytes]] is less than [[java.lang.Integer.BYTES]].
     */
    def readInt: Int

    /** Get the int value at the given reader offset. The [[readerOffset]] is not modified. The value is read using a
     *  two's complement 32-bit encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param roff
     *    The read offset, an absolute offset into this buffer, to read from.
     *  @return
     *    The int value at the given offset.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Integer.BYTES]].
     */
    def getInt(roff: Int): Int

    /** Read the unsigned int value at the current [[readerOffset]], and increases the reader offset by
     *  [[java.lang.Integer.BYTES]]. The value is read using an unsigned two's complement 32-bit encoding, in
     *  [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @return
     *    The unsigned int value at the current reader offset.
     *  @throws IndexOutOfBoundsException
     *    If [[readableBytes]] is less than [[java.lang.Integer.BYTES]].
     */
    def readUnsignedInt: Long

    /** Get the unsigned int value at the given reader offset. The [[readerOffset]] is not modified. The value is read
     *  using an unsigned two's complement 32-bit encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param roff
     *    The read offset, an absolute offset into this buffer, to read from.
     *  @return
     *    The unsigned int value at the given offset.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Integer.BYTES]].
     */
    def getUnsignedInt(roff: Int): Long

    /** Write the given int value at the current [[writerOffset]], and increase the writer offset by
     *  [[java.lang.Integer.BYTES]]. The value is written using a two's complement 32-bit encoding, in
     *  [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param value
     *    The int value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    If [[writableBytes]] is less than [[java.lang.Integer.BYTES]], and the [[capacity]] buffer capacity cannot be
     *    automatically increased.
     */
    def writeInt(value: Int): Buffer

    /** Set the given int value at the given write offset. The [[writerOffset]] is not modified. The value is written
     *  using a two's complement 32-bit encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param woff
     *    The write offset, an absolute offset into this buffer to write to.
     *  @param value
     *    The int value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Integer.BYTES]].
     */
    def setInt(woff: Int, value: Int): Buffer

    /** Write the given unsigned int value at the current [[writerOffset]], and increase the writer offset by
     *  [[java.lang.Integer.BYTES]]. The value is written using an unsigned two's complement 32-bit encoding, in
     *  [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param value
     *    The long value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    If [[writableBytes]] is less than [[java.lang.Integer.BYTES]], and the [[capacity]] buffer capacity cannot be
     *    automatically increased.
     */
    def writeUnsignedInt(value: Long): Buffer

    /** Set the given unsigned int value at the given write offset. The [[writerOffset]] is not modified. The value is
     *  written using an unsigned two's complement 32-bit encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param woff
     *    The write offset, an absolute offset into this buffer to write to.
     *  @param value
     *    The long value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Integer.BYTES]].
     */
    def setUnsignedInt(woff: Int, value: Long): Buffer

    /** Read the float value at the current [[readerOffset]], and increases the reader offset by
     *  [[java.lang.Float.BYTES]]. The value is read using a 32-bit IEEE floating point encoding, in
     *  [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @return
     *    The float value at the current reader offset.
     *  @throws IndexOutOfBoundsException
     *    If [[readableBytes]] is less than [[java.lang.Float.BYTES]].
     */
    def readFloat: Float

    /** Get the float value at the given reader offset. The [[readerOffset]] is not modified. The value is read using a
     *  32-bit IEEE floating point encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param roff
     *    The read offset, an absolute offset into this buffer, to read from.
     *  @return
     *    The float value at the given offset.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Float.BYTES]].
     */
    def getFloat(roff: Int): Float

    /** Write the given float value at the current [[writerOffset]], and increase the writer offset by
     *  [[java.lang.Float.BYTES]]. The value is written using a 32-bit IEEE floating point encoding, in
     *  [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param value
     *    The float value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    If [[writableBytes]] is less than [[java.lang.Float.BYTES]], and the [[capacity]] buffer capacity cannot be
     *    automatically increased.
     */
    def writeFloat(value: Float): Buffer

    /** Set the given float value at the given write offset. The [[writerOffset]] is not modified. The value is written
     *  using a 32-bit IEEE floating point encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param woff
     *    The write offset, an absolute offset into this buffer to write to.
     *  @param value
     *    The float value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Float.BYTES]].
     */
    def setFloat(woff: Int, value: Float): Buffer

    /** Read the long value at the current [[readerOffset]], and increases the reader offset by [[java.lang.Long.BYTES]]
     *  [[java.lang.Long.BYTES]]. The value is read using a two's complement 64-bit encoding, in
     *  [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @return
     *    The long value at the current reader offset.
     *  @throws IndexOutOfBoundsException
     *    If [[readableBytes]] is less than [[java.lang.Long.BYTES]].
     */
    def readLong: Long

    /** Get the long value at the given reader offset. The [[readerOffset]] is not modified. The value is read using a
     *  two's complement 64-bit encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param roff
     *    The read offset, an absolute offset into this buffer, to read from.
     *  @return
     *    The long value at the given offset.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Long.BYTES]].
     */
    def getLong(roff: Int): Long

    /** Write the given long value at the current [[writerOffset]], and increase the writer offset by
     *  [[java.lang.Long.BYTES]]. The value is written using a two's complement 64-bit encoding, in
     *  [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param value
     *    The long value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    If [[writableBytes]] is less than [[java.lang.Long.BYTES]], and the [[capacity]] buffer capacity cannot be
     *    automatically increased.
     */
    def writeLong(value: Long): Buffer

    /** Set the given long value at the given write offset. The [[writerOffset]] is not modified. The value is written
     *  using a two's complement 64-bit encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param woff
     *    The write offset, an absolute offset into this buffer to write to.
     *  @param value
     *    The long value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Long.BYTES]].
     */
    def setLong(woff: Int, value: Long): Buffer

    /** Read the double value at the current [[readerOffset]], and increases the reader offset by
     *  [[java.lang.Double.BYTES]]. The value is read using a 64-bit IEEE floating point encoding, in
     *  [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @return
     *    The double value at the current reader offset.
     *  @throws IndexOutOfBoundsException
     *    If [[readableBytes]] is less than [[java.lang.Double.BYTES]].
     */
    def readDouble: Double

    /** Get the double value at the given reader offset. The [[readerOffset]] is not modified. The value is read using a
     *  64-bit IEEE floating point encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param roff
     *    The read offset, an absolute offset into this buffer, to read from.
     *  @return
     *    The double value at the given offset.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Double.BYTES]].
     */
    def getDouble(roff: Int): Double

    /** Write the given double value at the current [[writerOffset]], and increase the writer offset by
     *  [[java.lang.Double.BYTES]]. The value is written using a 64-bit IEEE floating point encoding, in
     *  [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param value
     *    The double value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    If [[writableBytes]] is less than [[java.lang.Double.BYTES]], and the [[capacity]] buffer capacity cannot be
     *    automatically increased.
     */
    def writeDouble(value: Double): Buffer

    /** Set the given double value at the given write offset. The [[writerOffset]] is not modified. The value is written
     *  using a 64-bit IEEE floating point encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param woff
     *    The write offset, an absolute offset into this buffer to write to.
     *  @param value
     *    The double value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Double.BYTES]].
     */
    def setDouble(woff: Int, value: Double): Buffer

}

object Buffer {

    /** Wraps a [[ByteBuffer]] into a [[Buffer]].
     *
     *  <p> The new buffer will be backed by the given [[ByteBuffer]]; that is, modifications to the buffer will cause
     *  the [[ByteBuffer]] to be modified and vice versa. The new buffer's capacity will be [[ByteBuffer#capacity]], its
     *  readerOffset and writerOffset will be zero, and its byte order will be
     *
     *  [[ByteOrder# BIG_ENDIAN]].
     *
     *  @param byteBuffer
     *    The [[ByteBuffer]] that will back this buffer
     *  @return
     *    The new byte buffer
     */
    def wrap(byteBuffer: ByteBuffer): Buffer =
        if (byteBuffer.isDirect) DirectWrapBuffer(byteBuffer) else HeapWrapBuffer(byteBuffer)

    /** Wraps a byte array into a [[Buffer]].
     *
     *  <p> The new buffer will be backed by the given byte array; that is, modifications to the buffer will cause the
     *  array to be modified and vice versa. The new buffer's capacity will be [[array.length]], its readerOffset and
     *  writerOffset will be zero, and its byte order will be
     *
     *  [[ByteOrder# BIG_ENDIAN]].
     *
     *  @param array
     *    The array that will back this buffer
     *  @return
     *    The new byte buffer
     */
    def wrap(array: Array[Byte]): Buffer = HeapWrapBuffer(ByteBuffer.wrap(array))

}
