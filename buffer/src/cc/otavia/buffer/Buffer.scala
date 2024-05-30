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

import cc.otavia.buffer.unpool.{UnpoolDirectBuffer, UnpoolHeapBuffer}

import java.io.IOException
import java.lang.{Double as JDouble, Float as JFloat, Long as JLong, Short as JShort}
import java.nio.ByteBuffer
import java.nio.channels.{FileChannel, ReadableByteChannel, WritableByteChannel}
import java.nio.charset.{Charset, StandardCharsets}
import java.util.UUID
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
 *  <h3>Splitting buffers</h3> The split() method is not support.
 *
 *  <h3>Buffers as constants</h3> Constants buffer is not support, use Array[Byte] directly.
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
    def readableBytes: Int

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

    /** Writes into this buffer, all the bytes from the given [[source]] using the [[StandardCharsets.UTF_8]] charset.
     *  This updates the [[writerOffset]] of this buffer.
     *
     *  @param source
     *    [[CharSequence]] to read from.
     *
     *  @return
     *    This buffer.
     */
    final def writeCharSequence(source: CharSequence): Buffer = writeCharSequence(source, StandardCharsets.UTF_8)

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

    /** Sets into this buffer, all the bytes from the given [[source]] using the passed [[charset]]. This not updates
     *  the [[writerOffset]] of this buffer.
     *
     *  @param source
     *    [[CharSequence]] to read from.
     *  @param charset
     *    [[Charset]] to use for writing.
     *  @return
     *    This buffer.
     */
    final def setCharSequence(index: Int, source: CharSequence): Buffer =
        setCharSequence(index, source, StandardCharsets.UTF_8)

    /** Sets into this buffer, all the bytes from the given [[source]] using the passed [[charset]]. This not updates
     *  the [[writerOffset]] of this buffer.
     *
     *  @param source
     *    [[CharSequence]] to read from.
     *  @param charset
     *    [[Charset]] to use for writing.
     *  @return
     *    This buffer.
     */
    final def setCharSequence(index: Int, source: CharSequence, charset: Charset): Buffer = {
        val bytes = source.toString.getBytes(charset)
        setBytes(index, bytes)
    }

    /** Reads a [[CharSequence]] of the passed [[length]] using the [[StandardCharsets.UTF_8]] charset. This updates the
     *  [[readerOffset]] of this buffer.
     *
     *  @param length
     *    of [[CharSequence]] to read.
     *
     *  @return
     *    [[CharSequence]] read from this buffer.
     *  @throws IndexOutOfBoundsException
     *    if the passed [[length]] is more than the [[readableBytes]] of this buffer.
     */
    def readCharSequence(length: Int): CharSequence = readCharSequence(length, StandardCharsets.UTF_8)

    /** Reads a [[CharSequence]] of the passed [[length]] using the passed [[Charset]]. This updates the
     *  [[readerOffset]] of this buffer.
     *
     *  @param length
     *    Length of [[CharSequence]] to read.
     *  @param charset
     *    [[Charset]] to use for reading.
     *  @return
     *    [[CharSequence]] read from this buffer.
     *  @throws IndexOutOfBoundsException
     *    if the passed [[length]] is more than the [[readableBytes]] of this buffer.
     */
    def readCharSequence(length: Int, charset: Charset): CharSequence

    /** Get a [[CharSequence]] at the given reader offset. The [[readerOffset]] is not modified.
     *
     *  @param index
     *    The read offset, an absolute offset into this buffer, to read from.
     *  @param len
     *    Length of bytes to read
     *  @param charset
     *    [[Charset]] to use for reading.
     *  @return
     *    [[CharSequence]] get from this buffer.
     *  @throws IndexOutOfBoundsException
     *    If the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Float.BYTES]].
     */
    def getCharSequence(index: Int, len: Int, charset: Charset = StandardCharsets.UTF_8): CharSequence = {
        val array = getBytes(index, len)
        new String(array, charset)
    }

    /** Parses the string content stored in the buffer as a signed integer in the radix specified by the second
     *  argument. The characters in the string must all be digits of the specified radix (as determined by whether
     *  Character.digit(char, int) returns a non-negative value), except that the first character may be an ASCII minus
     *  sign '-' ('\u002D') to indicate a negative value or an ASCII plus sign '+' ('\u002B') to indicate a positive
     *  value. The resulting integer value is returned.
     *
     *  An exception of type [[NumberFormatException]] is thrown if any of the following situations occurs:
     *
     *  this method fork form JDK [[Integer.parseInt]]
     *
     *  @param length
     *    string number content length.
     *  @param radix
     *    the radix to be used while parsing the string.
     *  @throws NumberFormatException
     *    if the string content does not contain a parsable int.
     *  @return
     *    the integer represented by the string content in the specified radix.
     */
    @throws[NumberFormatException]
    def readStringAsLong(length: Int, radix: Int = 10): Long

    /** Parses the string content stored in the buffer as a signed integer in the radix specified by the third argument.
     *  The characters in the string must all be digits of the specified radix (as determined by whether
     *  Character.digit(char, int) returns a nonnegative value), except that the first character may be an ASCII minus
     *  sign '-' ('\u002D') to indicate a negative value or an ASCII plus sign '+' ('\u002B') to indicate a positive
     *  value. The resulting integer value is returned.
     *
     *  An exception of type [[NumberFormatException]] is thrown if any of the following situations occurs:
     *
     *  this method fork form JDK [[Integer.parseInt]]
     *
     *  @param index
     *    The read offset, an absolute offset into this buffer, to read from.
     *  @param length
     *    string number content length.
     *  @param radix
     *    the radix to be used while parsing the string.
     *  @throws NumberFormatException
     *    if the string content does not contain a parsable int.
     *  @return
     *    the integer represented by the string content in the specified radix.
     */
    @throws[NumberFormatException]
    def getStringAsLong(index: Int, length: Int, radix: Int = 10): Long

    /** Parses the string content stored in the buffer as a signed [[Double]].
     *
     *  An exception of type [[NumberFormatException]] is thrown if any of the following situations occurs:
     *
     *  this method fork form JDK [[JFloat.parseFloat]] and [[JDouble.parseDouble]]
     *
     *  @param length
     *    string number content length.
     *  @return
     *    the float represented by the string content.
     */
    def readStringAsDouble(length: Int): Double

    /** Parses the string content stored in the buffer as a signed [[Double]].
     *
     *  An exception of type [[NumberFormatException]] is thrown if any of the following situations occurs:
     *
     *  this method fork form JDK [[JFloat.parseFloat]] and [[JDouble.parseDouble]]
     *
     *  @param index
     *    The read offset, an absolute offset into this buffer, to read from.
     *  @param length
     *    string number content length.
     *  @return
     *    the float represented by the string content.
     */
    def getStringAsDouble(index: Int, length: Int): Double

    /** Writes into this buffer, all the bytes from the given [[uuid]] string. This updates the [[writerOffset]] of this
     *  buffer.
     *
     *  @param uuid
     *    uuid value.
     */
    def writeUUIDAsString(uuid: UUID): Unit

    /** Writes into this buffer, all the bytes from the given [[uuid]] string. This not updates the [[writerOffset]] of
     *  this buffer.
     *
     *  @param index
     *    The write offset, an absolute offset into this buffer to write to.
     *  @param uuid
     *    uuid value.
     */
    def setUUIDAsString(index: Int, uuid: UUID): Unit

    /** Parses the string content stored in the buffer as a [[UUID]].
     *  @return
     *    The UUID represented by the string content.
     */
    def readStringAsUUID(): UUID

    /** Parses the string content stored in the buffer as a [[UUID]].
     *  @param index
     *    The read offset, an absolute offset into this buffer, to read from.
     *  @return
     *    The UUID represented by the string content.
     */
    def getStringAsUUID(index: Int): UUID

    /** Writes into this buffer, all the readable bytes from the given buffer. This updates the [[writerOffset]] of this
     *  buffer, and the [[readerOffset]] of the given buffer.
     *
     *  @param source
     *    The buffer to read from.
     *  @return
     *    This buffer.
     */
    final def writeBytes(source: Buffer): Buffer = writeBytes(source, source.readableBytes)

    /** Writes into this buffer, the given number of bytes from the [[source]]. This updates the [[writerOffset]] of
     *  this buffer, and the [[readerOffset]] of the given buffer.
     *
     *  @param source
     *    The buffer to read from.
     *  @param length
     *    The number of bytes to copy.
     *  @return
     *    This buffer.
     */
    def writeBytes(source: Buffer, length: Int): Buffer

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
    final def writeBytes(source: Array[Byte]): Buffer = writeBytes(source, 0, source.length)

    /** Fills this buffer with [[value]] starting at the current [[writerOffset]] and increases the [[writerOffset]] by
     *  the specified length. If [[writableBytes]] is less than length, [[ensureWritable]] will be called in an attempt
     *  to expand capacity to accommodate.
     *  @param length
     *    the number of [[value]]s to write to the buffer.
     *  @param value
     *    the value byte to write.
     *  @return
     *    This buffer.
     */
    def writeBytes(length: Int, value: Byte): Buffer

    /** Set the given byte array at the given write offset. The [[writerOffset]] is not modified.
     *
     *  @param index
     *    The write offset, an absolute offset into this buffer to write to.
     *  @param source
     *    The byte array to write.
     *  @param srcPos
     *    start offset of byte array.
     *  @param length
     *    length of byte to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Double.BYTES]].
     */
    def setBytes(index: Int, source: Array[Byte], srcPos: Int, length: Int): Buffer

    /** Set the given byte array at the given write offset. The [[writerOffset]] is not modified.
     *
     *  @param index
     *    The write offset, an absolute offset into this buffer to write to.
     *  @param source
     *    The byte array to write.
     *
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Double.BYTES]].
     */
    final def setBytes(index: Int, source: Array[Byte]): Buffer = setBytes(index, source, 0, source.length)

    /** Get the bytes value at the given reader offset. The [[readerOffset]] is not modified.
     *
     *  @param index
     *    The read offset, an absolute offset into this buffer, to read from.
     *  @param len
     *    Length of bytes to read
     *  @return
     *    The bytes array
     *  @throws IndexOutOfBoundsException
     *    If the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Float.BYTES]].
     */
    final def getBytes(index: Int, len: Int): Array[Byte] = {
        val array = new Array[Byte](len)
        this.copyInto(index, array, 0, len)
        array
    }

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
    final def writeBytes(source: ByteBuffer): Buffer = writeBytes(source, source.remaining())

    /** Writes into this buffer from the source [[ByteBuffer]]. This updates the [[writerOffset]] of this buffer and
     *  also the position of the source [[ByteBuffer]].
     *
     *  <p> Note: the behaviour is undefined if the given [[ByteBuffer]] is an alias for the memory in this buffer.
     *
     *  @param source
     *    The [[ByteBuffer]] to read from.
     *  @param length
     *    The number of bytes to copy.
     *  @return
     *    This buffer.
     */
    def writeBytes(source: ByteBuffer, length: Int): Buffer

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
    final def readBytes(destination: ByteBuffer): Buffer = readBytes(destination, destination.remaining())

    /** Read from this buffer, into the destination [[ByteBuffer]] This updates the [[readerOffset]] of this buffer and
     *  also the position of the destination [[ByteBuffer]].
     *
     *  <p> Note: the behaviour is undefined if the given [[ByteBuffer]] is an alias for the memory in this buffer.
     *
     *  @param destination
     *    The [[ByteBuffer]] to write into.
     *  @param length
     *    The number of bytes to copy. The real length is
     *    {{{math.min(math.min(readableBytes, length), destination.remaining())}}}
     *  @return
     *    This buffer.
     */
    def readBytes(destination: ByteBuffer, length: Int): Buffer

    /** Read from this buffer, into the destination array, the given number of bytes. This updates the [[readerOffset]]
     *  of this buffer by the length argument.
     *
     *  @param destination
     *    The byte array to write into.
     *  @return
     *    This buffer.
     */
    final def readBytes(destination: Array[Byte]): Buffer = readBytes(destination, 0, destination.length)

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

    /** Read from this buffer, into the destination [[Buffer]] This updates the [[readerOffset]] of this buffer and also
     *  the position of the destination [[Buffer]].
     *
     *  <p> Note: the behaviour is undefined if the given [[Buffer]] is an alias for the memory in this buffer.
     *
     *  @param destination
     *    The [[Buffer]] to write into.
     *  @return
     *    This buffer.
     */
    final def readBytes(destination: Buffer): Buffer = readBytes(destination, readableBytes)

    /** Read from this buffer, into the destination [[Buffer]] This updates the [[readerOffset]] of this buffer and also
     *  the position of the destination [[Buffer]].
     *
     *  <p> Note: the behaviour is undefined if the given [[Buffer]] is an alias for the memory in this buffer.
     *
     *  @param destination
     *    The [[Buffer]] to write into.
     *  @param length
     *    The number of bytes to read.
     *  @return
     *    This buffer.
     */
    def readBytes(destination: Buffer, length: Int): Buffer

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

    /** Get the number of [[readableBytes]], until any the given byte in [[set]] is found in this buffer. If the byte is
     *  not found, -1 is returned.
     *
     *  <p> This method does not modify the [[readerOffset]] or the [[writerOffset]].
     *
     *  @param set
     *    any the given byte in [[set]]
     *  @return
     */
    def bytesBeforeIn(set: Array[Byte]): Int

    /** Get the number of [[readableBytes]], until any the given byte which is between [[lower]] and [[upper]] is found
     *  in this buffer. If the byte is not found, -1 is returned.
     *
     *  <p> This method does not modify the [[readerOffset]] or the [[writerOffset]].
     *
     *  @param lower
     *    lower bound byte
     *  @param upper
     *    upper bound byte
     *  @return
     */
    def bytesBeforeInRange(lower: Byte, upper: Byte): Int

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

    /** Get the number of [[readableBytes]], until the given [[needle1]],[[needle2]],[[needle3]],[[needle4]] bytes is
     *  found in this buffer. If the needle is not found, -1 is returned.
     *
     *  <p> This method does not modify the [[readerOffset]] or the [[writerOffset]].
     *
     *  @param needle1
     *    The first byte value to search for.
     *  @param needle2
     *    The second byte value to search for.
     *  @param needle3
     *    The third byte value to search for.
     *  @param needle4
     *    The fourth byte value to search for.
     *  @return
     *    The offset, relative to the current [[readerOffset]], of the found value, or -1 if none was found.
     */
    def bytesBefore(needle1: Byte, needle2: Byte, needle3: Byte, needle4: Byte): Int

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
    def bytesBefore(needle: Array[Byte]): Int

    /** Get the number of [[readableBytes]], until the given [[needle]] is found in this buffer. The found offset will
     *  be the offset into this buffer, relative to [[from]], of the first byte of a sequence that matches all readable
     *  bytes in the given [[needle]] buffer. If the needle is not found, -1 is returned.
     *
     *  <p> This method does not modify the [[readerOffset]] or the [[writerOffset]].
     *
     *  @param needle
     *    The byte seq value to search for.
     *  @param from
     *    start index to search
     *  @param to
     *    end index to search
     *  @param ignoreCase
     *    ignore case
     *  @return
     */
    def bytesBefore(needle: Array[Byte], from: Int, to: Int, ignoreCase: Boolean = false): Int

    /** Opens a cursor to iterate the given number bytes of this buffer, starting at the given offset. The
     *  [[readerOffset]] and [[writerOffset]] are not modified by the cursor. <p> Care should be taken to ensure that
     *  the buffer's lifetime extends beyond the cursor and the iteration, and that the [[readerOffset]] and
     *  [[writerOffset]] are not modified while the iteration takes place. Otherwise, unpredictable behaviour might
     *  result.
     *
     *  @return
     *    A [[ByteCursor]] for the given stretch of bytes of this buffer.
     *  @throws BufferClosedException
     *    If the [[Buffer]] has been closed.
     *  @throws IllegalArgumentException
     *    if the length is negative, or if the region given by the [[fromOffset]] and the [[length]] reaches outside the
     *    bounds of this buffer.
     */
    final def openCursor(): ByteCursor = openCursor(readerOffset, readableBytes)

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
     *  @throws BufferClosedException
     *    If the [[Buffer]] has been closed.
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
     *  @throws BufferClosedException
     *    If the [[Buffer]] has been closed.
     *  @throws IllegalArgumentException
     *    if the length is negative, or if the region given by the [[fromOffset]] and the [[length]] reaches outside the
     *    bounds of this buffer.
     */
    final def openReverseCursor: ByteCursor = {
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

    /** Discards the read bytes, and moves the buffer contents to the beginning of the buffer.
     *
     *  @return
     *    This buffer instance.
     *  @throws IllegalStateException
     *    if this buffer is in a bad state.
     */
    def compact(): Buffer

    /** Close this [[Buffer]] */
    def close(): Unit

    /** Check the [[Buffer]] is closed. */
    def closed: Boolean

    /** Clears this [[Buffer]]. The [[readerOffset]] and [[writerOffset]] are set to zero.
     *
     *  @return
     *    This buffer
     */
    def clean(): this.type

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
     *  @param idx
     *    The read offset, an absolute offset into this buffer, to read from.
     *  @return
     *    The byte value at the given offset.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus 1.
     */
    def getByte(idx: Int): Byte

    /** Read the boolean value at the current [[readerOffset]], and increases the reader offset by
     *  [[java.lang.Byte.BYTES]]. A boolean gets read as a byte from this buffer. All byte values which are not equal to
     *  zero are considered as the boolean value true, zero represents false.
     *
     *  @return
     *    The boolean value at the current reader offset.
     *  @throws IndexOutOfBoundsException
     *    If [[readableBytes]] is less than [[java.lang.Byte.BYTES]].
     */
    final def readBoolean: Boolean = readByte != 0

    /** Get the boolean value at the given reader offset. The [[readerOffset]] is not modified. A boolean gets read as a
     *  byte from this buffer. All byte values which are not equal to zero are considered as the boolean value true,
     *  zero represents false.
     *
     *  @param index
     *    The read offset, an absolute offset into this buffer, to read from.
     *  @return
     *    The boolean value at the given offset.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Byte.BYTES]].
     */
    final def getBoolean(index: Int): Boolean = getByte(index) != 0

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
    final def writeBoolean(value: Boolean): Buffer = writeByte((if (value) 1 else 0).toByte)

    /** Set the given boolean value at the given write offset. The [[writerOffset]] is not modified. A boolean gets
     *  written as a byte to this buffer. All byte values which are not equal to zero are considered as the boolean
     *  value true, zero represents false.
     *
     *  @param index
     *    The write offset, an absolute offset into this buffer to write to.
     *  @param value
     *    The boolean value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Byte.BYTES]].
     */
    final def setBoolean(index: Int, value: Boolean): Buffer = setByte(index, (if (value) 1 else 0).toByte)

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
     *  @param index
     *    The read offset, an absolute offset into this buffer, to read from.
     *  @return
     *    The unsigned byte value at the given offset.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Byte.BYTES]].
     */
    def getUnsignedByte(index: Int): Int

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
     *  @param index
     *    The write offset, an absolute offset into this buffer to write to.
     *  @param value
     *    The byte value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Byte.BYTES]].
     */
    def setByte(index: Int, value: Byte): Buffer

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
     *  @param index
     *    The write offset, an absolute offset into this buffer to write to.
     *  @param value
     *    The int value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Byte.BYTES]].
     */
    def setUnsignedByte(index: Int, value: Int): Buffer

    /** Read the char value at the current [[readerOffset]], and increases the reader offset by 2. The value is read
     *  using a 2-byte UTF-16 encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @return
     *    The char value at the current reader offset.
     *  @throws IndexOutOfBoundsException
     *    If [[readableBytes]] is less than 2.
     */
    def readChar: Char

    /** Read the char value at the current [[readerOffset]], and increases the reader offset by 2. The value is read
     *  using a 2-byte UTF-16 encoding, in [[java.nio.ByteOrder.LITTLE_ENDIAN]] byte order.
     *
     *  @return
     *    The char value at the current reader offset.
     *  @throws IndexOutOfBoundsException
     *    If [[readableBytes]] is less than 2.
     */
    final def readCharLE: Char = Character.reverseBytes(readChar)

    /** Get the char value at the given reader offset. The [[readerOffset]] is not modified. The value is read using a
     *  2-byte UTF-16 encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param index
     *    The read offset, an absolute offset into this buffer, to read from.
     *  @return
     *    The char value at the given offset.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus 2.
     */
    def getChar(index: Int): Char

    /** Get the char value at the given reader offset. The [[readerOffset]] is not modified. The value is read using a
     *  2-byte UTF-16 encoding, in [[java.nio.ByteOrder.LITTLE_ENDIAN]] byte order.
     *
     *  @param index
     *    The read offset, an absolute offset into this buffer, to read from.
     *  @return
     *    The char value at the given offset.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus 2.
     */
    final def getCharLE(index: Int): Char = Character.reverseBytes(getChar(index))

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

    /** Write the given char value at the current [[writerOffset]], and increase the writer offset by 2. The value is
     *  written using a 2-byte UTF-16 encoding, in [[java.nio.ByteOrder.LITTLE_ENDIAN]] byte order.
     *
     *  @param value
     *    The char value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    If [[writableBytes]] is less than 2, and the [[capacity]] buffer capacity cannot be automatically increased.
     */
    final def writeCharLE(value: Char): Buffer = writeChar(Character.reverseBytes(value))

    /** Set the given char value at the given write offset. The [[writerOffset]] is not modified. The value is written
     *  using a 2-byte UTF-16 encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param index
     *    The write offset, an absolute offset into this buffer to write to.
     *  @param value
     *    The char value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus 2.
     */
    def setChar(index: Int, value: Char): Buffer

    /** Set the given char value at the given write offset. The [[writerOffset]] is not modified. The value is written
     *  using a 2-byte UTF-16 encoding, in [[java.nio.ByteOrder.LITTLE_ENDIAN]] byte order.
     *
     *  @param index
     *    The write offset, an absolute offset into this buffer to write to.
     *  @param value
     *    The char value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus 2.
     */
    final def setCharLE(index: Int, value: Char): Buffer = setChar(index, Character.reverseBytes(value))

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

    /** Read the short value at the current [[readerOffset]], and increases the reader offset by
     *  [[java.lang.Short.BYTES]] [[java.lang.Short.BYTES]]. The value is read using a two's complement 16-bit encoding,
     *  in [[java.nio.ByteOrder.LITTLE_ENDIAN]] byte order.
     *
     *  @return
     *    The short value at the current reader offset.
     *  @throws IndexOutOfBoundsException
     *    If [[readableBytes]] is less than [[java.lang.Short.BYTES]].
     */
    final def readShortLE: Short = JShort.reverseBytes(readShort)

    /** Get the short value at the given reader offset. The [[readerOffset]] is not modified. The value is read using a
     *  two's complement 16-bit encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param index
     *    The read offset, an absolute offset into this buffer, to read from.
     *  @return
     *    The short value at the given offset.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Short.BYTES]].
     */
    def getShort(index: Int): Short

    /** Get the short value at the given reader offset. The [[readerOffset]] is not modified. The value is read using a
     *  two's complement 16-bit encoding, in [[java.nio.ByteOrder.LITTLE_ENDIAN]] byte order.
     *
     *  @param index
     *    The read offset, an absolute offset into this buffer, to read from.
     *  @return
     *    The short value at the given offset.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Short.BYTES]].
     */
    final def getShortLE(index: Int): Short = JShort.reverseBytes(getShort(index))

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

    /** Read the unsigned short value at the current [[readerOffset]], and increases the reader offset by
     *  [[java.lang.Short.BYTES]]. The value is read using an unsigned two's complement 16-bit encoding, in
     *  [[java.nio.ByteOrder.LITTLE_ENDIAN]] byte order.
     *
     *  @return
     *    The unsigned short value at the current reader offset.
     *  @throws IndexOutOfBoundsException
     *    If [[readableBytes]] is less than [[java.lang.Short.BYTES]].
     */
    final def readUnsignedShortLE: Int = readShortLE & 0xffff

    /** Get the unsigned short value at the given reader offset. The [[readerOffset]] is not modified. The value is read
     *  using an unsigned two's complement 16-bit encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param index
     *    The read offset, an absolute offset into this buffer, to read from.
     *  @return
     *    The unsigned short value at the given offset.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Short.BYTES]].
     */
    def getUnsignedShort(index: Int): Int

    /** Get the unsigned short value at the given reader offset. The [[readerOffset]] is not modified. The value is read
     *  using an unsigned two's complement 16-bit encoding, in [[java.nio.ByteOrder.LITTLE_ENDIAN]] byte order.
     *
     *  @param index
     *    The read offset, an absolute offset into this buffer, to read from.
     *  @return
     *    The unsigned short value at the given offset.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Short.BYTES]].
     */
    final def getUnsignedShortLE(index: Int): Int = getShortLE(index) & 0xffff

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

    /** Write the given short value at the current [[writerOffset]], and increase the writer offset by
     *  [[java.lang.Short.BYTES]]. The value is written using a two's complement 16-bit encoding, in
     *  [[java.nio.ByteOrder.LITTLE_ENDIAN]] byte order.
     *
     *  @param value
     *    The short value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    If [[writableBytes]] is less than [[java.lang.Short.BYTES]], and the [[capacity]] buffer capacity cannot be
     *    automatically increased.
     */
    final def writeShortLE(value: Short): Buffer = writeShort(JShort.reverseBytes(value))

    /** Set the given short value at the given write offset. The [[writerOffset]] is not modified. The value is written
     *  using a two's complement 16-bit encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param index
     *    The write offset, an absolute offset into this buffer to write to.
     *  @param value
     *    The short value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Short.BYTES]].
     */
    def setShort(index: Int, value: Short): Buffer

    /** Set the given short value at the given write offset. The [[writerOffset]] is not modified. The value is written
     *  using a two's complement 16-bit encoding, in [[java.nio.ByteOrder.LITTLE_ENDIAN]] byte order.
     *
     *  @param index
     *    The write offset, an absolute offset into this buffer to write to.
     *  @param value
     *    The short value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Short.BYTES]].
     */
    final def setShortLE(index: Int, value: Short): Buffer = setShort(index, JShort.reverseBytes(value))

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

    /** Write the given unsigned short value at the current [[writerOffset]], and increase the writer offset by
     *  [[java.lang.Short.BYTES]]. The value is written using an unsigned two's complement 16-bit encoding, in
     *  [[java.nio.ByteOrder.LITTLE_ENDIAN]] byte order.
     *
     *  @param value
     *    The int value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    If [[writableBytes]] is less than [[java.lang.Short.BYTES]], and the [[capacity]] buffer capacity cannot be
     *    automatically increased.
     */
    final def writeUnsignedShortLE(value: Int): Buffer = writeShort(JShort.reverseBytes((value & 0xffff).toShort))

    /** Set the given unsigned short value at the given write offset. The [[writerOffset]] is not modified. The value is
     *  written using an unsigned two's complement 16-bit encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param index
     *    The write offset, an absolute offset into this buffer to write to.
     *  @param value
     *    The int value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Short.BYTES]].
     */
    def setUnsignedShort(index: Int, value: Int): Buffer

    /** Set the given unsigned short value at the given write offset. The [[writerOffset]] is not modified. The value is
     *  written using an unsigned two's complement 16-bit encoding, in [[java.nio.ByteOrder.LITTLE_ENDIAN]] byte order.
     *
     *  @param index
     *    The write offset, an absolute offset into this buffer to write to.
     *  @param value
     *    The int value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Short.BYTES]].
     */
    final def setUnsignedShortLE(index: Int, value: Int): Buffer =
        setShort(index, JShort.reverseBytes((value & 0xffff).toShort))

    /** Read the int value at the current [[readerOffset]], and increases the reader offset by 3. The value is read
     *  using a two's complement 24-bit encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @return
     *    The int value at the current reader offset.
     *  @throws IndexOutOfBoundsException
     *    If [[readableBytes]] is less than 3.
     */
    def readMedium: Int

    /** Read the int value at the current [[readerOffset]], and increases the reader offset by 3. The value is read
     *  using a two's complement 24-bit encoding, in [[java.nio.ByteOrder.LITTLE_ENDIAN]] byte order.
     *
     *  @return
     *    The int value at the current reader offset.
     *  @throws IndexOutOfBoundsException
     *    If [[readableBytes]] is less than 3.
     */
    def readMediumLE: Int

    /** Get the int value at the given reader offset. The [[readerOffset]] is not modified. The value is read using a
     *  two's complement 24-bit encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param index
     *    The read offset, an absolute offset into this buffer, to read from.
     *  @return
     *    The int value at the given offset.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus 3.
     */
    def getMedium(index: Int): Int

    /** Get the int value at the given reader offset. The [[readerOffset]] is not modified. The value is read using a
     *  two's complement 24-bit encoding, in [[java.nio.ByteOrder.LITTLE_ENDIAN]] byte order.
     *
     *  @param index
     *    The read offset, an absolute offset into this buffer, to read from.
     *  @return
     *    The int value at the given offset.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus 3.
     */
    def getMediumLE(index: Int): Int

    /** Read the unsigned int value at the current [[readerOffset]], and increases the reader offset by 3. The value is
     *  read using an unsigned two's complement 24-bit encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @return
     *    The unsigned int value at the current reader offset.
     *  @throws IndexOutOfBoundsException
     *    If [[readableBytes]] is less than 3.
     */
    def readUnsignedMedium: Int

    /** Read the unsigned int value at the current [[readerOffset]], and increases the reader offset by 3. The value is
     *  read using an unsigned two's complement 24-bit encoding, in [[java.nio.ByteOrder.LITTLE_ENDIAN]] byte order.
     *
     *  @return
     *    The unsigned int value at the current reader offset.
     *  @throws IndexOutOfBoundsException
     *    If [[readableBytes]] is less than 3.
     */
    def readUnsignedMediumLE: Int

    /** Get the unsigned int value at the given reader offset. The [[readerOffset]] is not modified. The value is read
     *  using an unsigned two's complement 24-bit encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param index
     *    The read offset, an absolute offset into this buffer, to read from.
     *  @return
     *    The unsigned int value at the given offset.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus 3.
     */
    def getUnsignedMedium(index: Int): Int

    /** Get the unsigned int value at the given reader offset. The [[readerOffset]] is not modified. The value is read
     *  using an unsigned two's complement 24-bit encoding, in [[java.nio.ByteOrder.LITTLE_ENDIAN]] byte order.
     *
     *  @param index
     *    The read offset, an absolute offset into this buffer, to read from.
     *  @return
     *    The unsigned int value at the given offset.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus 3.
     */
    def getUnsignedMediumLE(index: Int): Int

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

    /** Write the given int value at the current [[writerOffset]], and increase the writer offset by 3. The value is
     *  written using a two's complement 24-bit encoding, in [[java.nio.ByteOrder.LITTLE_ENDIAN]] byte order.
     *
     *  @param value
     *    The int value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    If [[writableBytes]] is less than 3, and the [[capacity]] buffer capacity cannot be automatically increased.
     */
    def writeMediumLE(value: Int): Buffer

    /** Set the given int value at the given write offset. The [[writerOffset]] is not modified. The value is written
     *  using a two's complement 24-bit encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param index
     *    The write offset, an absolute offset into this buffer to write to.
     *  @param value
     *    The int value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus 3.
     */
    def setMedium(index: Int, value: Int): Buffer

    /** Set the given int value at the given write offset. The [[writerOffset]] is not modified. The value is written
     *  using a two's complement 24-bit encoding, in [[java.nio.ByteOrder.LITTLE_ENDIAN]] byte order.
     *
     *  @param index
     *    The write offset, an absolute offset into this buffer to write to.
     *  @param value
     *    The int value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus 3.
     */
    def setMediumLE(index: Int, value: Int): Buffer

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

    /** Write the given unsigned int value at the current [[writerOffset]], and increase the writer offset by 3. The
     *  value is written using an unsigned two's complement 24-bit encoding, in [[java.nio.ByteOrder.LITTLE_ENDIAN]]
     *  byte order.
     *
     *  @param value
     *    The int value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    If [[writableBytes]] is less than 3, and the [[capacity]] buffer capacity cannot be automatically increased.
     */
    def writeUnsignedMediumLE(value: Int): Buffer

    /** Set the given unsigned int value at the given write offset. The [[writerOffset]] is not modified. The value is
     *  written using an unsigned two's complement 24-bit encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param index
     *    The write offset, an absolute offset into this buffer to write to.
     *  @param value
     *    The int value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus 3.
     */
    def setUnsignedMedium(index: Int, value: Int): Buffer

    /** Set the given unsigned int value at the given write offset. The [[writerOffset]] is not modified. The value is
     *  written using an unsigned two's complement 24-bit encoding, in [[java.nio.ByteOrder.LITTLE_ENDIAN]] byte order.
     *
     *  @param index
     *    The write offset, an absolute offset into this buffer to write to.
     *  @param value
     *    The int value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus 3.
     */
    def setUnsignedMediumLE(index: Int, value: Int): Buffer

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

    /** Read the int value at the current [[readerOffset]], and increases the reader offset by
     *  [[java.lang.Integer.BYTES]]. The value is read using a two's complement 32-bit encoding, in
     *  [[java.nio.ByteOrder.LITTLE_ENDIAN]] byte order.
     *
     *  @return
     *    The int value at the current reader offset.
     *  @throws IndexOutOfBoundsException
     *    If [[readableBytes]] is less than [[java.lang.Integer.BYTES]].
     */
    final def readIntLE: Int = Integer.reverseBytes(readInt)

    /** Get the int value at the given reader offset. The [[readerOffset]] is not modified. The value is read using a
     *  two's complement 32-bit encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param index
     *    The read offset, an absolute offset into this buffer, to read from.
     *  @return
     *    The int value at the given offset.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Integer.BYTES]].
     */
    def getInt(index: Int): Int

    /** Get the int value at the given reader offset. The [[readerOffset]] is not modified. The value is read using a
     *  two's complement 32-bit encoding, in [[java.nio.ByteOrder.LITTLE_ENDIAN]] byte order.
     *
     *  @param index
     *    The read offset, an absolute offset into this buffer, to read from.
     *  @return
     *    The int value at the given offset.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Integer.BYTES]].
     */
    final def getIntLE(index: Int): Int = Integer.reverseBytes(getInt(index))

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

    /** Read the unsigned int value at the current [[readerOffset]], and increases the reader offset by
     *  [[java.lang.Integer.BYTES]]. The value is read using an unsigned two's complement 32-bit encoding, in
     *  [[java.nio.ByteOrder.LITTLE_ENDIAN]] byte order.
     *
     *  @return
     *    The unsigned int value at the current reader offset.
     *  @throws IndexOutOfBoundsException
     *    If [[readableBytes]] is less than [[java.lang.Integer.BYTES]].
     */
    final def readUnsignedIntLE: Long = readIntLE & 0xffffffffL

    /** Get the unsigned int value at the given reader offset. The [[readerOffset]] is not modified. The value is read
     *  using an unsigned two's complement 32-bit encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param index
     *    The read offset, an absolute offset into this buffer, to read from.
     *  @return
     *    The unsigned int value at the given offset.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Integer.BYTES]].
     */
    def getUnsignedInt(index: Int): Long

    /** Get the unsigned int value at the given reader offset. The [[readerOffset]] is not modified. The value is read
     *  using an unsigned two's complement 32-bit encoding, in [[java.nio.ByteOrder.LITTLE_ENDIAN]] byte order.
     *
     *  @param index
     *    The read offset, an absolute offset into this buffer, to read from.
     *  @return
     *    The unsigned int value at the given offset.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Integer.BYTES]].
     */
    final def getUnsignedIntLE(index: Int): Long = getIntLE(index) & 0xffffffffL

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

    /** Write the given int value at the current [[writerOffset]], and increase the writer offset by
     *  [[java.lang.Integer.BYTES]]. The value is written using a two's complement 32-bit encoding, in
     *  [[java.nio.ByteOrder.LITTLE_ENDIAN]] byte order.
     *
     *  @param value
     *    The int value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    If [[writableBytes]] is less than [[java.lang.Integer.BYTES]], and the [[capacity]] buffer capacity cannot be
     *    automatically increased.
     */
    final def writeIntLE(value: Int): Buffer = writeInt(Integer.reverseBytes(value))

    /** Set the given int value at the given write offset. The [[writerOffset]] is not modified. The value is written
     *  using a two's complement 32-bit encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param index
     *    The write offset, an absolute offset into this buffer to write to.
     *  @param value
     *    The int value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Integer.BYTES]].
     */
    def setInt(index: Int, value: Int): Buffer

    /** Set the given int value at the given write offset. The [[writerOffset]] is not modified. The value is written
     *  using a two's complement 32-bit encoding, in [[java.nio.ByteOrder.LITTLE_ENDIAN]] byte order.
     *
     *  @param index
     *    The write offset, an absolute offset into this buffer to write to.
     *  @param value
     *    The int value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Integer.BYTES]].
     */
    final def setIntLE(index: Int, value: Int): Buffer = setInt(index, Integer.reverseBytes(value))

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

    /** Write the given unsigned int value at the current [[writerOffset]], and increase the writer offset by
     *  [[java.lang.Integer.BYTES]]. The value is written using an unsigned two's complement 32-bit encoding, in
     *  [[java.nio.ByteOrder.LITTLE_ENDIAN]] byte order.
     *
     *  @param value
     *    The long value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    If [[writableBytes]] is less than [[java.lang.Integer.BYTES]], and the [[capacity]] buffer capacity cannot be
     *    automatically increased.
     */
    final def writeUnsignedIntLE(value: Long): Buffer = writeIntLE((value & 0xffffffffL).toInt)

    /** Set the given unsigned int value at the given write offset. The [[writerOffset]] is not modified. The value is
     *  written using an unsigned two's complement 32-bit encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param index
     *    The write offset, an absolute offset into this buffer to write to.
     *  @param value
     *    The long value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Integer.BYTES]].
     */
    def setUnsignedInt(index: Int, value: Long): Buffer

    /** Set the given unsigned int value at the given write offset. The [[writerOffset]] is not modified. The value is
     *  written using an unsigned two's complement 32-bit encoding, in [[java.nio.ByteOrder.LITTLE_ENDIAN]] byte order.
     *
     *  @param index
     *    The write offset, an absolute offset into this buffer to write to.
     *  @param value
     *    The long value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Integer.BYTES]].
     */
    def setUnsignedIntLE(index: Int, value: Long): Buffer = setIntLE(index, (value & 0xffffffffL).toInt)

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

    /** Read the float value at the current [[readerOffset]], and increases the reader offset by
     *  [[java.lang.Float.BYTES]]. The value is read using a 32-bit IEEE floating point encoding, in
     *  [[java.nio.ByteOrder.LITTLE_ENDIAN]] byte order.
     *
     *  @return
     *    The float value at the current reader offset.
     *  @throws IndexOutOfBoundsException
     *    If [[readableBytes]] is less than [[java.lang.Float.BYTES]].
     */
    final def readFloatLE: Float = JFloat.intBitsToFloat(readIntLE)

    /** Get the float value at the given reader offset. The [[readerOffset]] is not modified. The value is read using a
     *  32-bit IEEE floating point encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param index
     *    The read offset, an absolute offset into this buffer, to read from.
     *  @return
     *    The float value at the given offset.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Float.BYTES]].
     */
    def getFloat(index: Int): Float

    /** Get the float value at the given reader offset. The [[readerOffset]] is not modified. The value is read using a
     *  32-bit IEEE floating point encoding, in [[java.nio.ByteOrder.LITTLE_ENDIAN]] byte order.
     *
     *  @param index
     *    The read offset, an absolute offset into this buffer, to read from.
     *  @return
     *    The float value at the given offset.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Float.BYTES]].
     */
    final def getFloatLE(index: Int): Float = JFloat.intBitsToFloat(getIntLE(index))

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

    /** Write the given float value at the current [[writerOffset]], and increase the writer offset by
     *  [[java.lang.Float.BYTES]]. The value is written using a 32-bit IEEE floating point encoding, in
     *  [[java.nio.ByteOrder.LITTLE_ENDIAN]] byte order.
     *
     *  @param value
     *    The float value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    If [[writableBytes]] is less than [[java.lang.Float.BYTES]], and the [[capacity]] buffer capacity cannot be
     *    automatically increased.
     */
    final def writeFloatLE(value: Float): Buffer = writeIntLE(JFloat.floatToIntBits(value))

    /** Set the given float value at the given write offset. The [[writerOffset]] is not modified. The value is written
     *  using a 32-bit IEEE floating point encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param index
     *    The write offset, an absolute offset into this buffer to write to.
     *  @param value
     *    The float value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Float.BYTES]].
     */
    def setFloat(index: Int, value: Float): Buffer

    /** Set the given float value at the given write offset. The [[writerOffset]] is not modified. The value is written
     *  using a 32-bit IEEE floating point encoding, in [[java.nio.ByteOrder.LITTLE_ENDIAN]] byte order.
     *
     *  @param index
     *    The write offset, an absolute offset into this buffer to write to.
     *  @param value
     *    The float value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Float.BYTES]].
     */
    def setFloatLE(index: Int, value: Float): Buffer = setIntLE(index, JFloat.floatToIntBits(value))

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

    /** Read the long value at the current [[readerOffset]], and increases the reader offset by [[java.lang.Long.BYTES]]
     *  [[java.lang.Long.BYTES]]. The value is read using a two's complement 64-bit encoding, in
     *  [[java.nio.ByteOrder.LITTLE_ENDIAN]] byte order.
     *
     *  @return
     *    The long value at the current reader offset.
     *  @throws IndexOutOfBoundsException
     *    If [[readableBytes]] is less than [[java.lang.Long.BYTES]].
     */
    final def readLongLE: Long = JLong.reverseBytes(readLong)

    /** Get the long value at the given reader offset. The [[readerOffset]] is not modified. The value is read using a
     *  two's complement 64-bit encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param index
     *    The read offset, an absolute offset into this buffer, to read from.
     *  @return
     *    The long value at the given offset.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Long.BYTES]].
     */
    def getLong(index: Int): Long

    /** Get the long value at the given reader offset. The [[readerOffset]] is not modified. The value is read using a
     *  two's complement 64-bit encoding, in [[java.nio.ByteOrder.LITTLE_ENDIAN]] byte order.
     *
     *  @param index
     *    The read offset, an absolute offset into this buffer, to read from.
     *  @return
     *    The long value at the given offset.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Long.BYTES]].
     */
    final def getLongLE(index: Int): Long = JLong.reverseBytes(getLong(index))

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

    /** Write the given long value at the current [[writerOffset]], and increase the writer offset by
     *  [[java.lang.Long.BYTES]]. The value is written using a two's complement 64-bit encoding, in
     *  [[java.nio.ByteOrder.LITTLE_ENDIAN]] byte order.
     *
     *  @param value
     *    The long value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    If [[writableBytes]] is less than [[java.lang.Long.BYTES]], and the [[capacity]] buffer capacity cannot be
     *    automatically increased.
     */
    final def writeLongLE(value: Long): Buffer = writeLong(JLong.reverseBytes(value))

    /** Set the given long value at the given write offset. The [[writerOffset]] is not modified. The value is written
     *  using a two's complement 64-bit encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param index
     *    The write offset, an absolute offset into this buffer to write to.
     *  @param value
     *    The long value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Long.BYTES]].
     */
    def setLong(index: Int, value: Long): Buffer

    /** Set the given long value at the given write offset. The [[writerOffset]] is not modified. The value is written
     *  using a two's complement 64-bit encoding, in [[java.nio.ByteOrder.LITTLE_ENDIAN]] byte order.
     *
     *  @param index
     *    The write offset, an absolute offset into this buffer to write to.
     *  @param value
     *    The long value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Long.BYTES]].
     */
    def setLongLE(index: Int, value: Long): Buffer = setLong(index, JLong.reverseBytes(value))

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

    /** Read the double value at the current [[readerOffset]], and increases the reader offset by
     *  [[java.lang.Double.BYTES]]. The value is read using a 64-bit IEEE floating point encoding, in
     *  [[java.nio.ByteOrder.LITTLE_ENDIAN]] byte order.
     *
     *  @return
     *    The double value at the current reader offset.
     *  @throws IndexOutOfBoundsException
     *    If [[readableBytes]] is less than [[java.lang.Double.BYTES]].
     */
    final def readDoubleLE: Double = JDouble.longBitsToDouble(readLongLE)

    /** Get the double value at the given reader offset. The [[readerOffset]] is not modified. The value is read using a
     *  64-bit IEEE floating point encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param index
     *    The read offset, an absolute offset into this buffer, to read from.
     *  @return
     *    The double value at the given offset.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Double.BYTES]].
     */
    def getDouble(index: Int): Double

    /** Get the double value at the given reader offset. The [[readerOffset]] is not modified. The value is read using a
     *  64-bit IEEE floating point encoding, in [[java.nio.ByteOrder.LITTLE_ENDIAN]] byte order.
     *
     *  @param index
     *    The read offset, an absolute offset into this buffer, to read from.
     *  @return
     *    The double value at the given offset.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Double.BYTES]].
     */
    final def getDoubleLE(index: Int): Double = JDouble.longBitsToDouble(getLongLE(index))

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

    /** Write the given double value at the current [[writerOffset]], and increase the writer offset by
     *  [[java.lang.Double.BYTES]]. The value is written using a 64-bit IEEE floating point encoding, in
     *  [[java.nio.ByteOrder.LITTLE_ENDIAN]] byte order.
     *
     *  @param value
     *    The double value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    If [[writableBytes]] is less than [[java.lang.Double.BYTES]], and the [[capacity]] buffer capacity cannot be
     *    automatically increased.
     */
    final def writeDoubleLE(value: Double): Buffer = writeLongLE(JDouble.doubleToRawLongBits(value))

    /** Set the given double value at the given write offset. The [[writerOffset]] is not modified. The value is written
     *  using a 64-bit IEEE floating point encoding, in [[java.nio.ByteOrder.BIG_ENDIAN]] byte order.
     *
     *  @param index
     *    The write offset, an absolute offset into this buffer to write to.
     *  @param value
     *    The double value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Double.BYTES]].
     */
    def setDouble(index: Int, value: Double): Buffer

    /** Set the given double value at the given write offset. The [[writerOffset]] is not modified. The value is written
     *  using a 64-bit IEEE floating point encoding, in [[java.nio.ByteOrder.LITTLE_ENDIAN]] byte order.
     *
     *  @param index
     *    The write offset, an absolute offset into this buffer to write to.
     *  @param value
     *    The double value to write.
     *  @return
     *    This Buffer.
     *  @throws IndexOutOfBoundsException
     *    if the given offset is out of bounds of the buffer, that is, less than 0 or greater than [[capacity]] minus
     *    [[java.lang.Double.BYTES]].
     */
    final def setDoubleLE(index: Int, value: Double): Buffer = setLongLE(index, JDouble.doubleToRawLongBits(value))

    /** Check the next readable byte is the gaven byte */
    def nextIs(byte: Byte): Boolean

    /** Check the next readable bytes is the gaven bytes */
    def nextAre(bytes: Array[Byte]): Boolean

    /** Check the byte at [[index]] is the gaven byte */
    def indexIs(byte: Byte, index: Int): Boolean

    /** Check the bytes at [[index]] is the gaven bytes. */
    def indexAre(bytes: Array[Byte], index: Int): Boolean

    /** Check the next readable byte is in the gaven bytes */
    def nextIn(bytes: Array[Byte]): Boolean

    /** Check the byte at index is in the gaven bytes */
    def indexIn(bytes: Array[Byte], index: Int): Boolean

    /** Check the next readable byte is in the gaven byte range */
    def nextInRange(lower: Byte, upper: Byte): Boolean

    /** Check the byte at index is in the gaven byte range */
    def indexInRange(lower: Byte, upper: Byte, index: Int): Boolean

    /** increase the [[readerOffset]] by one if the next readable byte is the gaven byte */
    def skipIfNextIs(byte: Byte): Boolean

    /** increase the [[readerOffset]] by length of [[bytes]] if the next readable bytes is the gaven bytes */
    def skipIfNextAre(bytes: Array[Byte]): Boolean

    /** increase the [[readerOffset]] by length of [[bytes]] if the next readable bytes is the gaven bytes
     *  @param bytes
     *    the compared bytes.
     *  @param ignoreCase
     *    whether ignore char case of bytes.
     */
    def skipIfNextIgnoreCaseAre(bytes: Array[Byte]): Boolean

    /** increase the [[readerOffset]] by one if the next readable byte is in the gaven bytes. */
    def skipIfNextIn(set: Array[Byte]): Boolean

}

object Buffer {

    /** Wraps a [[ByteBuffer]] into a [[Buffer]].
     *
     *  <p> The new buffer will be backed by the given [[ByteBuffer]]; that is, modifications to the buffer will cause
     *  the [[ByteBuffer]] to be modified and vice versa. The new buffer's capacity will be [[ByteBuffer#capacity]], its
     *  readerOffset is set to position of [[ByteBuffer]] and writerOffset is set to limit of [[ByteBuffer]], and its
     *  byte order will be
     *
     *  [[ByteOrder#BIG_ENDIAN]].
     *
     *  @param byteBuffer
     *    The [[ByteBuffer]] that will back this buffer
     *  @return
     *    The new byte buffer
     */
    def wrap(byteBuffer: ByteBuffer): Buffer =
        if (byteBuffer.isDirect) UnpoolDirectBuffer(byteBuffer) else UnpoolHeapBuffer(byteBuffer)

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
    def wrap(array: Array[Byte]): Buffer = UnpoolHeapBuffer(ByteBuffer.wrap(array))

}
