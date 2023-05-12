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

}
