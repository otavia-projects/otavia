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

package cc.otavia.handler.codec.base64

import cc.otavia.buffer.BufferAllocator.{offHeapAllocator, onHeapAllocator}
import cc.otavia.buffer.{Buffer, BufferAllocator}
import cc.otavia.handler.codec.base64.Base64.Decoder.decode4to3
import cc.otavia.buffer.ByteProcessor
import cc.otavia.buffer.pool.AdaptiveBuffer

import java.util.Objects.requireNonNull
import scala.language.unsafeNulls

/** Utility class for [[Buffer]] that encodes and decodes to and from <a
 *  href="https://en.wikipedia.org/wiki/Base64">Base64</a> notation. <p> The encoding and decoding algorithm in this
 *  class has been derived from <a href="http://iharder.sourceforge.net/current/java/base64/">Robert Harder's Public
 *  Domain Base64 Encoder/Decoder</a>.
 */
object Base64 {

    /** Maximum line length (76) of Base64 output. */
    private val MAX_LINE_LENGTH = 76

    /** The equals sign (=) as a byte. */
    private val EQUALS_SIGN: Byte = '='

    /** The new line character (\n) as a byte. */
    private val NEW_LINE: Byte = '\n'

    private val WHITE_SPACE_ENC = -5 // Indicates white space in encoding

    private val EQUALS_SIGN_ENC = -1 // Indicates equals sign in encoding

    private def alphabet(dialect: Base64Dialect) = dialect.alphabet

    private def decodabet(dialect: Base64Dialect) = dialect.decodabet

    private def breakLines(dialect: Base64Dialect) = dialect.breakLinesByDefault

    def encode(src: Buffer, dialect: Base64Dialect = Base64Dialect.STANDARD): Buffer =
        encode(src, breakLines(dialect), dialect)

    def encode(src: Buffer, breakLines: Boolean): Buffer = encode(src, breakLines, Base64Dialect.STANDARD)

    def encode(src: Buffer, breakLines: Boolean, dialect: Base64Dialect): Buffer = {
        val dest = encode(src, src.readerOffset, src.readableBytes, breakLines, dialect)
        src.readerOffset(src.writerOffset)
        dest
    }

    def encode(src: Buffer, off: Int, len: Int): Buffer = encode(src, off, len, Base64Dialect.STANDARD)

    def encode(src: Buffer, off: Int, len: Int, dialect: Base64Dialect): Buffer =
        encode(src, off, len, breakLines(dialect), dialect)

    def encode(src: Buffer, off: Int, len: Int, breakLines: Boolean): Buffer =
        encode(src, off, len, breakLines, Base64Dialect.STANDARD)

    def encode(src: Buffer, off: Int, len: Int, breakLines: Boolean, dialect: Base64Dialect): Buffer = {
        val allocator: BufferAllocator = if (src.isDirect) offHeapAllocator() else onHeapAllocator()
        encode(src, off, len, breakLines, dialect, allocator)
    }

    def encode(src: Buffer, dest: Buffer): Unit = encode(src, breakLines(Base64Dialect.STANDARD), dest)

    def encode(src: Buffer, breakLines: Boolean, dest: Buffer): Unit = {
        encode(src, breakLines, Base64Dialect.STANDARD, dest)
    }

    def encode(src: Buffer, breakLines: Boolean, dialect: Base64Dialect, dest: Buffer): Unit = {
        encode(src, src.readerOffset, src.readableBytes, breakLines, dialect, dest)
    }

    def encode(
        src: Buffer,
        off: Int,
        len: Int,
        breakLines: Boolean,
        dialect: Base64Dialect,
        allocator: BufferAllocator
    ): Buffer = {
        val dest: Buffer = allocator.allocate(encodedBufferSize(len, breakLines))
        encode(src, off, len, breakLines, dialect, dest)
        dest
    }

    def encode(src: Buffer, off: Int, len: Int, breakLines: Boolean, dialect: Base64Dialect, dest: Buffer): Unit = {
        dest.ensureWritable(encodedBufferSize(len, breakLines))
        val bytes      = this.alphabet(dialect)
        var d          = 0
        var e          = 0
        val len2       = len - 2
        var lineLength = 0

        while (d < len2) {
            encode3to4(src, d + off, 3, dest, e, bytes)
            lineLength += 4
            if (breakLines && lineLength == MAX_LINE_LENGTH) {
                dest.setByte(e + 4, NEW_LINE)
                e += 1
                lineLength = 0
            }
            d += 3
            e += 4
        }

        if (d < len) {
            encode3to4(src, d + off, len - d, dest, e, bytes)
            e += 4
        }

        // Remove last byte if it's a newline// Remove last byte if it's a newline
        if (e > 1 && dest.getByte(e - 1) == NEW_LINE) e -= 1

        dest.writerOffset(e)
    }

    private def encode3to4(
        src: Buffer,
        srcOffset: Int,
        numSigBytes: Int,
        dest: Buffer,
        destOffset: Int,
        alphabet: Array[Byte]
    ): Unit = {
        //           1         2         3
        // 01234567890123456789012345678901 Bit position
        // --------000000001111111122222222 Array position from threeBytes
        // --------|    ||    ||    ||    | Six bit groups to index ALPHABET
        //          >>18  >>12  >> 6  >> 0  Right shift necessary
        //                0x3f  0x3f  0x3f  Additional AND
        // Create buffer with zero-padding if there are only one or two
        // significant bytes passed in the array.
        // We have to shift left 24 in order to flush out the 1's that appear
        // when Java treats a value as negative that is cast from a byte to an int.
        var inBuff = 0
        numSigBytes match
            case 1 =>
                inBuff = toInt(src.getByte(srcOffset))
            case 2 =>
                inBuff = toIntBE(src.getShort(srcOffset))
            case _ =>
                inBuff = if (numSigBytes <= 0) 0 else toIntBE(src.getMedium(srcOffset))

        encode3to4BigEndian(inBuff, numSigBytes, dest, destOffset, alphabet)
    }

    // package-private for testing
    private def encodedBufferSize(len: Int, breakLines: Boolean) = {
        // Cast len to long to prevent overflow
        val len43 = (len.toLong << 2) / 3
        // Account for padding
        var ret = (len43 + 3) & ~3
        if (breakLines) ret += len43 / MAX_LINE_LENGTH
        if (ret < Integer.MAX_VALUE) ret.toInt else Integer.MAX_VALUE
    }

    private def toInt(value: Byte) = (value & 0xff) << 16

    private def toIntBE(value: Short) = (value & 0xff00) << 8 | (value & 0xff) << 8

    private def toIntBE(mediumValue: Int) = (mediumValue & 0xff0000) | (mediumValue & 0xff00) | (mediumValue & 0xff)

    private def encode3to4BigEndian(
        inBuff: Int,
        numSigBytes: Int,
        dest: Buffer,
        destOffset: Int,
        alphabet: Array[Byte]
    ): Unit = {
        // Packing bytes into an int to reduce bound and reference count checking.
        numSigBytes match
            case 3 =>
                dest.setInt(
                  destOffset,
                  alphabet(inBuff >>> 18) << 24 | alphabet(inBuff >>> 12 & 0x3f) << 16 |
                      alphabet(inBuff >>> 6 & 0x3f) << 8 | alphabet(inBuff & 0x3f)
                )
            case 2 =>
                dest.setInt(
                  destOffset,
                  alphabet(inBuff >>> 18) << 24 | alphabet(inBuff >>> 12 & 0x3f) << 16 |
                      alphabet(inBuff >>> 6 & 0x3f) << 8 | EQUALS_SIGN
                )
            case 1 =>
                dest.setInt(
                  destOffset,
                  alphabet(inBuff >>> 18) << 24 | alphabet(inBuff >>> 12 & 0x3f) << 16 | EQUALS_SIGN << 8 | EQUALS_SIGN
                )
            case _ => // NOOP
    }

    def decode(src: Buffer): Buffer = decode(src, Base64Dialect.STANDARD)

    def decode(src: Buffer, dialect: Base64Dialect): Buffer = {
        val dest = decode(src, src.readerOffset, src.readableBytes, dialect)
        src.readerOffset(src.writerOffset)
        dest
    }

    def decode(src: Buffer, off: Int, len: Int): Buffer = decode(src, off, len, Base64Dialect.STANDARD)

    def decode(src: Buffer, off: Int, len: Int, dialect: Base64Dialect): Buffer = {
        val allocator: BufferAllocator = if (src.isDirect) offHeapAllocator() else onHeapAllocator()
        decode(src, off, len, dialect, allocator)
    }

    def decode(src: Buffer, off: Int, len: Int, dialect: Base64Dialect, allocator: BufferAllocator): Buffer = {
        // Using a ByteProcessor to reduce bound and reference count checking.
        new Decoder().decode(src, off, len, allocator, dialect)
    }

    def decode(src: Buffer, off: Int, len: Int, dialect: Base64Dialect, dst: Buffer): Unit = {
        // Using a ByteProcessor to reduce bound and reference count checking.
        new Decoder().decode(src, off, len, dst, dialect)
    }

    // package-private for testing
    private def decodedBufferSize(len: Int) = len - (len >>> 2)

    class Decoder extends ByteProcessor {

        private val b4                     = new Array[Byte](4)
        private var b4Posn                 = 0
        private var decodabet: Array[Byte] = _
        private var outBuffPosn            = 0
        private var dest: Buffer           = _

        def decode(src: Buffer, off: Int, len: Int, allocator: BufferAllocator, dialect: Base64Dialect): Buffer = {
            val dst: Buffer = allocator.allocate(decodedBufferSize(len))
            this.decode(src: Buffer, off: Int, len: Int, dst, dialect)
            dest
        }

        def decode(src: Buffer, off: Int, len: Int, dst: Buffer, dialect: Base64Dialect): Unit = {
            dst.ensureWritable(decodedBufferSize(len))
            dest = dst
            try {
                decodabet = Base64.decodabet(dialect)
                src.openCursor(off, len).process(this)
                dest.writerOffset(outBuffPosn)
            } catch {
                case cause: Throwable =>
                    if (!dest.isInstanceOf[AdaptiveBuffer]) dest.close()
                    throw cause
            }
        }

        override def process(value: Byte): Boolean = {
            if (value > 0) {
                val sbiDecode = decodabet(value)
                if (sbiDecode >= WHITE_SPACE_ENC) {     // White space, Equals sign or better
                    if (sbiDecode >= EQUALS_SIGN_ENC) { // Equals sign or better
                        b4(b4Posn) = value
                        b4Posn += 1
                        if (b4Posn > 3) { // Quartet built
                            outBuffPosn += Decoder.decode4to3(b4, dest, outBuffPosn, decodabet)
                            b4Posn = 0
                            // If that was the equals sign, break out of 'for' loop
                            return value != EQUALS_SIGN
                        }
                    }
                    return true
                }
            }
            throw new IllegalArgumentException(
              "invalid Base64 input character: " + (value & 0xff).toShort + " (decimal)"
            )
        }

    }

    object Decoder {
        private def decode4to3(src: Array[Byte], dest: Buffer, destOffset: Int, decodabet: Array[Byte]): Int = {
            val src0              = src(0)
            val src1              = src(1)
            val src2              = src(2)
            var decodedValue: Int = 0
            if (src2 == EQUALS_SIGN) {
                // Example: Dk==
                try decodedValue = (decodabet(src0) & 0xff) << 2 | (decodabet(src1) & 0xff) >>> 4
                catch {
                    case _: IndexOutOfBoundsException => throw new IllegalArgumentException("not encoded in Base64")
                }
                dest.setUnsignedByte(destOffset, decodedValue)
                1
            } else {
                val src3 = src(3)
                if (src3 == EQUALS_SIGN) {
                    // Example: DkL=
                    val b1 = decodabet(src1)
                    // Packing bytes into a short to reduce bound and reference count checking.
                    try {
                        // The decodabet bytes are meant to straddle byte boundaries and so we must carefully mask out
                        // the bits we care about.
                        decodedValue = ((decodabet(src0) & 0x3f) << 2 | (b1 & 0xf0) >> 4) << 8 | (b1 & 0xf) << 4 |
                            (decodabet(src2) & 0xfc) >>> 2
                    } catch {
                        case _: IndexOutOfBoundsException => throw new IllegalArgumentException("not encoded in Base64")
                    }
                    dest.setUnsignedShort(destOffset, decodedValue)
                    2
                } else {
                    // Example: DkLE
                    try {
                        decodedValue = (decodabet(src0) & 0x3f) << 18 | (decodabet(src1) & 0xff) << 12 |
                            (decodabet(src2) & 0xff) << 6 | decodabet(src3) & 0xff
                    } catch {
                        case _: IndexOutOfBoundsException => throw new IllegalArgumentException("not encoded in Base64")
                    }
                    dest.setMedium(destOffset, decodedValue)
                    3
                }
            }
        }
    }

}
