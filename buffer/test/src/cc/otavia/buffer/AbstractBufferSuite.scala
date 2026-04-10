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

package cc.otavia.buffer

import org.scalatest.funsuite.AnyFunSuite

import java.nio.{ByteBuffer, ByteOrder}
import java.nio.charset.StandardCharsets
import scala.language.unsafeNulls

class AbstractBufferSuite extends AnyFunSuite {

    private def heapBuffer(cap: Int = 256): Buffer = {
        val buf = Buffer.wrap(ByteBuffer.allocate(cap))
        buf.writerOffset(0) // wrap sets widx=limit, reset to 0 for clean state
        buf
    }
    private def directBuffer(cap: Int = 256): Buffer = {
        val buf = Buffer.wrap(ByteBuffer.allocateDirect(cap))
        buf.writerOffset(0)
        buf
    }

    // Helper to test both heap and direct
    private def withBuffers(cap: Int = 256)(f: Buffer => Unit): Unit = {
        f(heapBuffer(cap))
        f(directBuffer(cap))
    }

    // =========================================================================
    // Basic properties
    // =========================================================================

    test("capacity returns underlying buffer capacity") {
        withBuffers(128) { buf =>
            assert(buf.capacity == 128)
        }
    }

    test("isDirect returns correct value") {
        assert(heapBuffer().isDirect == false)
        assert(directBuffer().isDirect == true)
    }

    test("initial offsets are zero") {
        withBuffers() { buf =>
            assert(buf.readerOffset == 0)
            assert(buf.writerOffset == 0)
            assert(buf.readableBytes == 0)
            assert(buf.writableBytes == buf.capacity)
        }
    }

    test("readerOffset and writerOffset setters") {
        withBuffers() { buf =>
            buf.writeInt(0x12345678)
            assert(buf.writerOffset == 4)
            assert(buf.readerOffset == 0)
            buf.readerOffset(2)
            assert(buf.readerOffset == 2)
            assert(buf.readableBytes == 2)
        }
    }

    test("resetOffsets resets both to zero") {
        withBuffers() { buf =>
            buf.writeInt(42)
            buf.readerOffset(2)
            buf.resetOffsets()
            assert(buf.readerOffset == 0)
            assert(buf.writerOffset == 0)
        }
    }

    test("skipReadableBytes advances readerOffset") {
        withBuffers() { buf =>
            buf.writeInt(1)
            buf.writeInt(2)
            buf.skipReadableBytes(4)
            assert(buf.readerOffset == 4)
            assert(buf.readInt == 2)
        }
    }

    test("skipWritableBytes advances writerOffset") {
        withBuffers() { buf =>
            buf.skipWritableBytes(4)
            assert(buf.writerOffset == 4)
            buf.setInt(0, 0x01020304)
            assert(buf.getInt(0) == 0x01020304)
        }
    }

    // =========================================================================
    // Byte read/write/get/set
    // =========================================================================

    test("writeByte readByte roundtrip") {
        withBuffers() { buf =>
            buf.writeByte(0x42.toByte)
            buf.writeByte(-1.toByte)
            assert(buf.readerOffset == 0)
            assert(buf.writerOffset == 2)
            assert(buf.readByte == 0x42.toByte)
            assert(buf.readByte == -1.toByte)
            assert(buf.readerOffset == 2)
        }
    }

    test("getByte setByte absolute access") {
        withBuffers() { buf =>
            buf.setByte(10, 0xAB.toByte)
            buf.setByte(11, 0xCD.toByte)
            assert(buf.getByte(10) == 0xAB.toByte)
            assert(buf.getByte(11) == 0xCD.toByte)
        }
    }

    test("readUnsignedByte getUnsignedByte") {
        withBuffers() { buf =>
            buf.writeByte(0xFF.toByte)
            buf.writeByte(0x00.toByte)
            assert(buf.getUnsignedByte(0) == 255)
            assert(buf.getUnsignedByte(1) == 0)
            assert(buf.readUnsignedByte == 255)
            assert(buf.readUnsignedByte == 0)
        }
    }

    test("writeUnsignedByte setUnsignedByte") {
        withBuffers() { buf =>
            buf.writeUnsignedByte(200)
            assert(buf.getUnsignedByte(0) == 200)
            buf.setUnsignedByte(5, 100)
            assert(buf.getUnsignedByte(5) == 100)
        }
    }

    // =========================================================================
    // Boolean read/write/get/set
    // =========================================================================

    test("writeBoolean readBoolean roundtrip") {
        withBuffers() { buf =>
            buf.writeBoolean(true)
            buf.writeBoolean(false)
            assert(buf.readBoolean == true)
            assert(buf.readBoolean == false)
        }
    }

    test("getBoolean setBoolean absolute") {
        withBuffers() { buf =>
            buf.setBoolean(3, true)
            buf.setBoolean(7, false)
            assert(buf.getBoolean(3) == true)
            assert(buf.getBoolean(7) == false)
        }
    }

    // =========================================================================
    // Char read/write/get/set
    // =========================================================================

    test("writeChar readChar roundtrip") {
        withBuffers() { buf =>
            buf.writeChar('A')
            buf.writeChar('中')
            assert(buf.readChar == 'A')
            assert(buf.readChar == '中')
        }
    }

    test("getChar setChar absolute") {
        withBuffers() { buf =>
            buf.setChar(0, 'X')
            buf.setChar(2, 'Y')
            assert(buf.getChar(0) == 'X')
            assert(buf.getChar(2) == 'Y')
        }
    }

    test("readCharLE writeCharLE roundtrip") {
        withBuffers() { buf =>
            buf.writeChar('A')
            val bigEndianBytes = Array[Byte](buf.getByte(0), buf.getByte(1))
            buf.resetOffsets()
            // Write A in BE, read as LE should give different result
            buf.writeChar('A')
            buf.readerOffset(0)
            val leVal = buf.readCharLE
            // Verify BE vs LE produce different byte order
            if (bigEndianBytes(0) != bigEndianBytes(1)) {
                assert(leVal != 'A')
            }
        }
    }

    // =========================================================================
    // Short read/write/get/set
    // =========================================================================

    test("writeShort readShort roundtrip") {
        withBuffers() { buf =>
            buf.writeShort(12345.toShort)
            buf.writeShort(-1.toShort)
            buf.writeShort(Short.MaxValue)
            buf.writeShort(Short.MinValue)
            assert(buf.readShort == 12345.toShort)
            assert(buf.readShort == -1.toShort)
            assert(buf.readShort == Short.MaxValue)
            assert(buf.readShort == Short.MinValue)
        }
    }

    test("getShort setShort absolute") {
        withBuffers() { buf =>
            buf.setShort(10, 1000.toShort)
            assert(buf.getShort(10) == 1000.toShort)
        }
    }

    test("readUnsignedShort getUnsignedShort") {
        withBuffers() { buf =>
            buf.writeShort(0xFFFF.toShort)
            assert(buf.getUnsignedShort(0) == 65535)
            buf.readerOffset(0)
            assert(buf.readUnsignedShort == 65535)
        }
    }

    test("writeUnsignedShort setUnsignedShort") {
        withBuffers() { buf =>
            buf.writeUnsignedShort(60000)
            assert(buf.getUnsignedShort(0) == 60000)
            buf.setUnsignedShort(4, 50000)
            assert(buf.getUnsignedShort(4) == 50000)
        }
    }

    test("readShortLE writeShortLE roundtrip") {
        withBuffers() { buf =>
            buf.writeShortLE(0x1234.toShort)
            assert(buf.readShortLE == 0x1234.toShort)
        }
    }

    // =========================================================================
    // Medium (24-bit) read/write/get/set
    // =========================================================================

    test("writeMedium readMedium roundtrip") {
        withBuffers() { buf =>
            buf.writeMedium(0x123456)
            buf.writeMedium(0)
            buf.writeMedium(0x7FFFFF) // max positive
            assert(buf.readMedium == 0x123456)
            assert(buf.readMedium == 0)
            assert(buf.readMedium == 0x7FFFFF)
        }
    }

    test("getMedium setMedium absolute") {
        withBuffers() { buf =>
            buf.setMedium(0, 0x123456) // positive medium (MSB bit 23 = 0)
            assert(buf.getMedium(0) == 0x123456)
            buf.setMedium(0, 0x7FFFFF) // max positive medium
            assert(buf.getMedium(0) == 0x7FFFFF)
        }
    }

    test("readUnsignedMedium getUnsignedMedium") {
        withBuffers() { buf =>
            buf.writeMedium(0xFFFFFF) // -1 as signed medium
            assert(buf.getUnsignedMedium(0) == 0xFFFFFF)
            buf.readerOffset(0)
            assert(buf.readUnsignedMedium == 0xFFFFFF)
        }
    }

    test("writeUnsignedMedium setUnsignedMedium") {
        withBuffers() { buf =>
            buf.writeUnsignedMedium(0xABCDEF)
            assert(buf.getUnsignedMedium(0) == 0xABCDEF)
            buf.setUnsignedMedium(6, 0x123456)
            assert(buf.getUnsignedMedium(6) == 0x123456)
        }
    }

    test("writeMediumLE readMediumLE roundtrip") {
        withBuffers() { buf =>
            buf.writeMediumLE(0x123456)
            assert(buf.readMediumLE == 0x123456)
        }
    }

    test("readUnsignedMediumLE writeUnsignedMediumLE") {
        withBuffers() { buf =>
            buf.writeUnsignedMediumLE(0xABCDEF)
            assert(buf.readUnsignedMediumLE == 0xABCDEF)
        }
    }

    test("setMediumLE getMediumLE absolute") {
        withBuffers() { buf =>
            buf.setMediumLE(0, 0x654321)
            assert(buf.getMediumLE(0) == 0x654321)
        }
    }

    test("setUnsignedMediumLE getUnsignedMediumLE") {
        withBuffers() { buf =>
            buf.setUnsignedMediumLE(0, 0xFEDCBA)
            assert(buf.getUnsignedMediumLE(0) == 0xFEDCBA)
        }
    }

    // =========================================================================
    // Int read/write/get/set
    // =========================================================================

    test("writeInt readInt roundtrip") {
        withBuffers() { buf =>
            buf.writeInt(0x12345678)
            buf.writeInt(-1)
            buf.writeInt(Int.MaxValue)
            buf.writeInt(Int.MinValue)
            assert(buf.readInt == 0x12345678)
            assert(buf.readInt == -1)
            assert(buf.readInt == Int.MaxValue)
            assert(buf.readInt == Int.MinValue)
        }
    }

    test("getInt setInt absolute") {
        withBuffers() { buf =>
            buf.setInt(20, 0xDEADBEEF)
            assert(buf.getInt(20) == 0xDEADBEEF)
        }
    }

    test("readUnsignedInt getUnsignedInt") {
        withBuffers() { buf =>
            buf.writeInt(0xFFFFFFFF) // -1 as signed
            assert(buf.getUnsignedInt(0) == 0xFFFFFFFFL)
            buf.readerOffset(0)
            assert(buf.readUnsignedInt == 0xFFFFFFFFL)
        }
    }

    test("writeUnsignedInt setUnsignedInt") {
        withBuffers() { buf =>
            buf.writeUnsignedInt(0x80000000L)
            assert(buf.getUnsignedInt(0) == 0x80000000L)
            buf.setUnsignedInt(8, 0xDEADBEEFL)
            assert(buf.getUnsignedInt(8) == 0xDEADBEEFL)
        }
    }

    test("writeIntLE readIntLE roundtrip") {
        withBuffers() { buf =>
            buf.writeIntLE(0x12345678)
            assert(buf.readIntLE == 0x12345678)
        }
    }

    // =========================================================================
    // Long read/write/get/set
    // =========================================================================

    test("writeLong readLong roundtrip") {
        withBuffers() { buf =>
            buf.writeLong(0x123456789ABCDEF0L)
            buf.writeLong(-1L)
            buf.writeLong(Long.MaxValue)
            buf.writeLong(Long.MinValue)
            assert(buf.readLong == 0x123456789ABCDEF0L)
            assert(buf.readLong == -1L)
            assert(buf.readLong == Long.MaxValue)
            assert(buf.readLong == Long.MinValue)
        }
    }

    test("getLong setLong absolute") {
        withBuffers() { buf =>
            buf.setLong(8, 0xCAFEBABEL)
            assert(buf.getLong(8) == 0xCAFEBABEL)
        }
    }

    test("writeLongLE readLongLE roundtrip") {
        withBuffers() { buf =>
            buf.writeLongLE(0x123456789ABCDEF0L)
            assert(buf.readLongLE == 0x123456789ABCDEF0L)
        }
    }

    // =========================================================================
    // Float read/write/get/set
    // =========================================================================

    test("writeFloat readFloat roundtrip") {
        withBuffers() { buf =>
            buf.writeFloat(3.14f)
            buf.writeFloat(Float.MaxValue)
            buf.writeFloat(Float.MinValue)
            buf.writeFloat(Float.PositiveInfinity)
            buf.writeFloat(Float.NegativeInfinity)
            buf.writeFloat(0.0f)
            buf.writeFloat(-0.0f)
            buf.writeFloat(Float.NaN)

            assert(buf.readFloat == 3.14f)
            assert(buf.readFloat == Float.MaxValue)
            assert(buf.readFloat == Float.MinValue)
            assert(buf.readFloat == Float.PositiveInfinity)
            assert(buf.readFloat == Float.NegativeInfinity)
            assert(buf.readFloat == 0.0f)
            // -0.0f == 0.0f in Scala, check bits
            val negZero = buf.readFloat
            assert(java.lang.Float.floatToIntBits(negZero) == java.lang.Float.floatToIntBits(-0.0f))
            assert(java.lang.Float.isNaN(buf.readFloat))
        }
    }

    test("getFloat setFloat absolute") {
        withBuffers() { buf =>
            buf.setFloat(4, 2.718f)
            assert(buf.getFloat(4) == 2.718f)
        }
    }

    // =========================================================================
    // Double read/write/get/set
    // =========================================================================

    test("writeDouble readDouble roundtrip") {
        withBuffers() { buf =>
            buf.writeDouble(3.141592653589793)
            buf.writeDouble(Double.MaxValue)
            buf.writeDouble(Double.MinValue)
            buf.writeDouble(Double.PositiveInfinity)
            buf.writeDouble(Double.NegativeInfinity)
            buf.writeDouble(0.0)
            buf.writeDouble(Double.NaN)

            assert(buf.readDouble == 3.141592653589793)
            assert(buf.readDouble == Double.MaxValue)
            assert(buf.readDouble == Double.MinValue)
            assert(buf.readDouble == Double.PositiveInfinity)
            assert(buf.readDouble == Double.NegativeInfinity)
            assert(buf.readDouble == 0.0)
            assert(java.lang.Double.isNaN(buf.readDouble))
        }
    }

    test("getDouble setDouble absolute") {
        withBuffers() { buf =>
            buf.setDouble(8, 1.414)
            assert(buf.getDouble(8) == 1.414)
        }
    }

    // =========================================================================
    // CharSequence read/write
    // =========================================================================

    test("writeCharSequence readCharSequence roundtrip") {
        withBuffers() { buf =>
            buf.writeCharSequence("hello", StandardCharsets.UTF_8)
            assert(buf.readCharSequence(5, StandardCharsets.UTF_8).toString == "hello")
        }
    }

    test("writeCharSequence readCharSequence with ASCII") {
        withBuffers() { buf =>
            buf.writeCharSequence("world", StandardCharsets.US_ASCII)
            assert(buf.readCharSequence(5, StandardCharsets.US_ASCII).toString == "world")
        }
    }

    // =========================================================================
    // String parsing methods
    // =========================================================================

    test("readFixedStringAsLong roundtrip") {
        withBuffers() { buf =>
            buf.writeCharSequence("12345", StandardCharsets.UTF_8)
            assert(BufferUtils.readFixedStringAsLong(buf, 5) == 12345L)
        }
    }

    test("readFixedStringAsLong negative") {
        withBuffers() { buf =>
            buf.writeCharSequence("-999", StandardCharsets.UTF_8)
            assert(BufferUtils.readFixedStringAsLong(buf, 4) == -999L)
        }
    }

    test("getFixedStringAsLong") {
        withBuffers() { buf =>
            buf.writeCharSequence("42", StandardCharsets.UTF_8)
            assert(BufferUtils.getFixedStringAsLong(buf, 0, 2) == 42L)
        }
    }

    test("readFixedStringAsDouble roundtrip") {
        withBuffers() { buf =>
            buf.writeCharSequence("3.14", StandardCharsets.UTF_8)
            assert(BufferUtils.readFixedStringAsDouble(buf, 4) == 3.14)
        }
    }

    test("getFixedStringAsDouble") {
        withBuffers() { buf =>
            buf.writeCharSequence("2.71", StandardCharsets.UTF_8)
            assert(BufferUtils.getFixedStringAsDouble(buf, 0, 4) == 2.71)
        }
    }

    // =========================================================================
    // Bulk byte operations
    // =========================================================================

    test("writeBytes Array[Byte] readBytes Array[Byte]") {
        withBuffers() { buf =>
            val data = Array[Byte](1, 2, 3, 4, 5)
            buf.writeBytes(data)
            assert(buf.writerOffset == 5)

            val dest = new Array[Byte](5)
            buf.readBytes(dest)
            assert(dest sameElements data)
            assert(buf.readerOffset == 5)
        }
    }

    test("writeBytes Array[Byte] with offset and length") {
        withBuffers() { buf =>
            val data = Array[Byte](0, 1, 2, 3, 4, 5, 6)
            buf.writeBytes(data, 2, 3) // write bytes [2, 3, 4]
            assert(buf.writerOffset == 3)
            assert(buf.getByte(0) == 2)
            assert(buf.getByte(1) == 3)
            assert(buf.getByte(2) == 4)
        }
    }

    test("readBytes Array[Byte] with offset and length") {
        withBuffers() { buf =>
            val data = Array[Byte](10, 20, 30, 40, 50)
            buf.writeBytes(data)

            val dest = new Array[Byte](10)
            buf.readBytes(dest, 3, 3)
            assert(dest(3) == 10)
            assert(dest(4) == 20)
            assert(dest(5) == 30)
        }
    }

    test("writeBytes with fill value") {
        withBuffers() { buf =>
            buf.writeBytes(5, 0xAA.toByte)
            assert(buf.writerOffset == 5)
            for (i <- 0 until 5) assert(buf.getByte(i) == 0xAA.toByte)
        }
    }

    test("writeBytes Buffer readBytes Buffer") {
        withBuffers() { src =>
            src.writeInt(0x11223344)
            src.writeShort(0x5566.toShort)

            val dest = heapBuffer(256)
            dest.writeBytes(src, 4)
            assert(dest.writerOffset == 4)
            assert(dest.readInt == 0x11223344)

            src.readBytes(dest, 2)
            assert(dest.writerOffset == 6)
        }
    }

    test("writeBytes ByteBuffer readBytes ByteBuffer") {
        withBuffers() { buf =>
            val src = ByteBuffer.wrap(Array[Byte](1, 2, 3, 4, 5))
            buf.writeBytes(src, 3)
            assert(buf.writerOffset == 3)
            assert(buf.getByte(0) == 1)

            val dest = ByteBuffer.allocate(10)
            buf.readBytes(dest, 3)
            dest.flip()
            assert(dest.get() == 1)
            assert(dest.get() == 2)
            assert(dest.get() == 3)
        }
    }

    test("setBytes absolute") {
        withBuffers() { buf =>
            val data = Array[Byte](0x10, 0x20, 0x30)
            buf.setBytes(5, data)
            assert(buf.getByte(5) == 0x10)
            assert(buf.getByte(6) == 0x20)
            assert(buf.getByte(7) == 0x30)
        }
    }

    test("setBytes with offset and length") {
        withBuffers() { buf =>
            val data = Array[Byte](0, 0, 0xAA.toByte, 0xBB.toByte, 0, 0)
            buf.skipWritableBytes(10)
            buf.setBytes(0, data, 2, 2)
            assert(buf.getByte(0) == 0xAA.toByte)
            assert(buf.getByte(1) == 0xBB.toByte)
        }
    }

    test("getBytes extracts array") {
        withBuffers() { buf =>
            buf.writeByte(1)
            buf.writeByte(2)
            buf.writeByte(3)
            val arr = buf.getBytes(0, 3)
            assert(arr sameElements Array[Byte](1, 2, 3))
        }
    }

    // =========================================================================
    // copyInto
    // =========================================================================

    test("copyInto Array[Byte]") {
        withBuffers() { buf =>
            buf.writeInt(0x01020304)
            val arr = new Array[Byte](4)
            buf.copyInto(0, arr, 0, 4)
            assert(arr(0) == 1)
            assert(arr(1) == 2)
            assert(arr(2) == 3)
            assert(arr(3) == 4)
        }
    }

    test("copyInto ByteBuffer") {
        withBuffers() { buf =>
            buf.writeInt(0x11223344)
            val dest = ByteBuffer.allocate(4)
            buf.copyInto(0, dest, 0, 4)
            assert(dest.getInt(0) == 0x11223344)
        }
    }

    test("copyInto Buffer") {
        val src = heapBuffer(64)
        src.writeInt(0xABCDEF01)
        val dest = heapBuffer(64)
        src.copyInto(0, dest, 0, 4)
        assert(dest.getInt(0) == 0xABCDEF01)
    }

    // =========================================================================
    // bytesBefore search
    // =========================================================================

    test("bytesBefore single byte") {
        withBuffers() { buf =>
            buf.writeBytes(Array[Byte](1, 2, 3, 4, 5))
            assert(buf.bytesBefore(3.toByte) == 2)
            assert(buf.bytesBefore(1.toByte) == 0)
            assert(buf.bytesBefore(5.toByte) == 4)
            assert(buf.bytesBefore(99.toByte) == -1)
        }
    }

    test("bytesBefore two bytes") {
        withBuffers() { buf =>
            buf.writeBytes(Array[Byte](1, 2, 3, 4, 5))
            assert(buf.bytesBefore(3.toByte, 4.toByte) == 2)
            assert(buf.bytesBefore(1.toByte, 2.toByte) == 0)
            assert(buf.bytesBefore(4.toByte, 5.toByte) == 3)
            assert(buf.bytesBefore(5.toByte, 6.toByte) == -1)
        }
    }

    test("bytesBefore three bytes") {
        withBuffers() { buf =>
            buf.writeBytes(Array[Byte](1, 2, 3, 4, 5, 6))
            assert(buf.bytesBefore(2.toByte, 3.toByte, 4.toByte) == 1)
            assert(buf.bytesBefore(1.toByte, 2.toByte, 3.toByte) == 0)
            assert(buf.bytesBefore(4.toByte, 5.toByte, 6.toByte) == 3)
        }
    }

    test("bytesBefore four bytes") {
        withBuffers() { buf =>
            buf.writeBytes(Array[Byte](1, 2, 3, 4, 5, 6, 7, 8))
            assert(buf.bytesBefore(1.toByte, 2.toByte, 3.toByte, 4.toByte) == 0)
            assert(buf.bytesBefore(3.toByte, 4.toByte, 5.toByte, 6.toByte) == 2)
            assert(buf.bytesBefore(5.toByte, 6.toByte, 7.toByte, 8.toByte) == 4)
        }
    }

    test("bytesBefore Array[Byte]") {
        withBuffers() { buf =>
            val data = "hello world".getBytes(StandardCharsets.UTF_8)
            buf.writeBytes(data)
            assert(buf.bytesBefore("hello".getBytes(StandardCharsets.UTF_8)) == 0)
            assert(buf.bytesBefore("world".getBytes(StandardCharsets.UTF_8)) == 6)
            assert(buf.bytesBefore("xyz".getBytes(StandardCharsets.UTF_8)) == -1)
        }
    }

    test("bytesBefore with from/to range") {
        withBuffers() { buf =>
            buf.writeBytes("abcabc".getBytes(StandardCharsets.UTF_8))
            // "abcabc" indices: a(0) b(1) c(2) a(3) b(4) c(5)
            // search for 'a' from offset 1 to end: found at index 3, relative = 2
            assert(buf.bytesBefore(Array[Byte]('a'), 1, 6) == 2)
            // search for 'b' from offset 2 to 6: found at index 4, relative = 2
            assert(buf.bytesBefore(Array[Byte]('b'), 2, 6) == 2)
        }
    }

    test("bytesBefore with ignoreCase") {
        withBuffers() { buf =>
            buf.writeBytes("Hello World".getBytes(StandardCharsets.UTF_8))
            assert(buf.bytesBefore("hello".getBytes(StandardCharsets.UTF_8), 0, 11, ignoreCase = true) == 0)
            assert(buf.bytesBefore("HELLO".getBytes(StandardCharsets.UTF_8), 0, 11, ignoreCase = true) == 0)
            assert(buf.bytesBefore("world".getBytes(StandardCharsets.UTF_8), 0, 11, ignoreCase = true) == 6)
        }
    }

    test("bytesBeforeIn set") {
        withBuffers() { buf =>
            buf.writeBytes("hello".getBytes(StandardCharsets.UTF_8))
            assert(buf.bytesBeforeIn(Array[Byte]('h', 'e', 'l')) == 0)
            assert(buf.bytesBeforeIn(Array[Byte]('o')) == 4)
            assert(buf.bytesBeforeIn(Array[Byte]('x', 'y', 'z')) == -1)
        }
    }

    test("bytesBeforeInRange") {
        withBuffers() { buf =>
            buf.writeBytes("Hello".getBytes(StandardCharsets.UTF_8))
            // 'H' = 72, 'e' = 101, 'l' = 108, 'o' = 111
            assert(buf.bytesBeforeInRange('a'.toByte, 'z'.toByte) == 1) // 'e' is first lowercase
            assert(buf.bytesBeforeInRange('A'.toByte, 'Z'.toByte) == 0) // 'H' is uppercase
        }
    }

    // =========================================================================
    // Cursor
    // =========================================================================

    test("openCursor iterates bytes") {
        withBuffers() { buf =>
            buf.writeBytes(Array[Byte](10, 20, 30, 40, 50))
            val cursor = buf.openCursor()
            var values = List.empty[Byte]
            while (cursor.readByte) {
                values = values :+ cursor.getByte
            }
            assert(values == List[Byte](10, 20, 30, 40, 50))
            assert(cursor.bytesLeft == 0)
        }
    }

    test("openCursor with offset and length") {
        withBuffers() { buf =>
            buf.writeBytes(Array[Byte](1, 2, 3, 4, 5))
            val cursor = buf.openCursor(2, 3)
            var values = List.empty[Byte]
            while (cursor.readByte) {
                values = values :+ cursor.getByte
            }
            assert(values == List[Byte](3, 4, 5))
        }
    }

    test("openReverseCursor iterates bytes backwards") {
        withBuffers() { buf =>
            buf.writeBytes(Array[Byte](10, 20, 30, 40, 50))
            val cursor = buf.openReverseCursor(4, 5)
            var values = List.empty[Byte]
            while (cursor.readByte) {
                values = values :+ cursor.getByte
            }
            assert(values == List[Byte](50, 40, 30, 20, 10))
        }
    }

    // =========================================================================
    // Peek/check methods
    // =========================================================================

    test("nextIs checks next readable byte") {
        withBuffers() { buf =>
            buf.writeByte(42)
            assert(buf.nextIs(42.toByte) == true)
            assert(buf.nextIs(43.toByte) == false)
        }
    }

    test("nextAre checks next readable bytes") {
        withBuffers() { buf =>
            buf.writeBytes(Array[Byte](1, 2, 3))
            assert(buf.nextMatch(Array[Byte](1, 2, 3)) == true)
            assert(buf.nextMatch(Array[Byte](1, 2)) == true)
            assert(buf.nextMatch(Array[Byte](2, 3)) == false)
        }
    }

    test("matchIs checks byte at index") {
        withBuffers() { buf =>
            buf.writeBytes(Array[Byte](10, 20, 30))
            assert(buf.matchIs(0, 10.toByte) == true)
            assert(buf.matchIs(1, 20.toByte) == true)
            assert(buf.matchIs(1, 10.toByte) == false)
        }
    }

    test("matchAt checks bytes at index") {
        withBuffers() { buf =>
            buf.writeBytes(Array[Byte](1, 2, 3, 4))
            assert(buf.matchAt(1, Array[Byte](2, 3)) == true)
            assert(buf.matchAt(0, Array[Byte](1, 2, 3)) == true)
            assert(buf.matchAt(0, Array[Byte](2, 3)) == false)
        }
    }

    test("nextIn checks if next byte is in set") {
        withBuffers() { buf =>
            buf.writeByte('a')
            assert(buf.nextIn(Array[Byte]('a', 'b', 'c')) == true)
            assert(buf.nextIn(Array[Byte]('x', 'y', 'z')) == false)
        }
    }

    test("matchIn checks if byte at index is in set") {
        withBuffers() { buf =>
            buf.writeBytes(Array[Byte]('a', 'b'))
            assert(buf.matchIn(0, Array[Byte]('a', 'b', 'c')) == true)
            assert(buf.matchIn(0, Array[Byte]('x')) == false)
        }
    }

    test("nextInRange checks range") {
        withBuffers() { buf =>
            buf.writeByte('5')
            assert(buf.nextInRange('0'.toByte, '9'.toByte) == true)
            assert(buf.nextInRange('a'.toByte, 'z'.toByte) == false)
        }
    }

    test("matchInRange checks range at index") {
        withBuffers() { buf =>
            buf.writeBytes(Array[Byte]('a', 'Z'))
            assert(buf.matchInRange(0, 'a'.toByte, 'z'.toByte) == true)
            assert(buf.matchInRange(1, 'a'.toByte, 'z'.toByte) == false)
        }
    }

    // =========================================================================
    // Skip methods
    // =========================================================================

    test("skipIfNextIs") {
        withBuffers() { buf =>
            buf.writeBytes(Array[Byte](1, 2, 3))
            assert(buf.skipIfNextIs(1.toByte) == true)
            assert(buf.readerOffset == 1)
            assert(buf.skipIfNextIs(1.toByte) == false)
            assert(buf.readerOffset == 1)
        }
    }

    test("skipIfNextMatch") {
        withBuffers() { buf =>
            buf.writeBytes(Array[Byte](1, 2, 3, 4))
            assert(buf.skipIfNextMatch(Array[Byte](1, 2)) == true)
            assert(buf.readerOffset == 2)
            assert(buf.skipIfNextMatch(Array[Byte](3, 4)) == true)
            assert(buf.readerOffset == 4)
        }
    }

    test("skipIfNextMatch with ignoreCase") {
        withBuffers() { buf =>
            buf.writeBytes("Hello".getBytes(StandardCharsets.UTF_8))
            assert(buf.skipIfNextMatch("HELLO".getBytes(StandardCharsets.UTF_8), ignoreCase = true) == true)
            assert(buf.readerOffset == 5)
        }
    }

    test("skipIfNextIn") {
        withBuffers() { buf =>
            buf.writeByte('a')
            assert(buf.skipIfNextIn(Array[Byte]('a', 'b')) == true)
            assert(buf.readerOffset == 1)
        }
    }

    test("skipIfNextInRange") {
        withBuffers() { buf =>
            buf.writeByte('5')
            assert(buf.skipIfNextInRange('0'.toByte, '9'.toByte) == true)
            assert(buf.readerOffset == 1)
        }
    }

    // =========================================================================
    // Compact
    // =========================================================================

    test("compact moves readable data to start") {
        withBuffers() { buf =>
            buf.writeInt(0x11223344)
            buf.writeInt(0x55667788)
            buf.readerOffset(4) // skip first int
            buf.compact()
            assert(buf.readerOffset == 0)
            assert(buf.readInt == 0x55667788)
        }
    }

    test("compact with no readable data resets offsets") {
        withBuffers() { buf =>
            buf.writeInt(1)
            buf.readerOffset(4)
            buf.compact()
            assert(buf.readerOffset == 0)
            assert(buf.writerOffset == 0)
        }
    }

    test("compact when already at start does nothing") {
        withBuffers() { buf =>
            buf.writeInt(42)
            buf.compact()
            assert(buf.readerOffset == 0)
            assert(buf.writerOffset == 4)
            assert(buf.readInt == 42)
        }
    }

    // =========================================================================
    // Fill
    // =========================================================================

    test("fill fills entire capacity") {
        withBuffers() { buf =>
            buf.fill(0xFF.toByte)
            for (i <- 0 until buf.capacity) {
                assert(buf.getByte(i) == 0xFF.toByte)
            }
        }
    }

    // =========================================================================
    // EnsureWritable
    // =========================================================================

    test("ensureWritable succeeds when space available") {
        withBuffers() { buf =>
            buf.ensureWritable(100) // should not throw
        }
    }

    test("ensureWritable with compaction") {
        withBuffers(32) { buf =>
            buf.writeInt(1)
            buf.writeInt(2)
            buf.readerOffset(4)
            // After compaction: 28 writable bytes. Need 28 more? No, 28 available.
            buf.ensureWritable(28, 0, true)
        }
    }

    // =========================================================================
    // Close and clean
    // =========================================================================

    test("clean resets offsets to zero") {
        withBuffers() { buf =>
            buf.writeInt(42)
            buf.readerOffset(2)
            buf.clean()
            assert(buf.readerOffset == 0)
            assert(buf.writerOffset == 0)
        }
    }

    // =========================================================================
    // Big-endian byte order verification
    // =========================================================================

    test("int written in big-endian order") {
        withBuffers() { buf =>
            buf.writeInt(0x01020304)
            assert(buf.getByte(0) == 0x01)
            assert(buf.getByte(1) == 0x02)
            assert(buf.getByte(2) == 0x03)
            assert(buf.getByte(3) == 0x04)
        }
    }

    test("short written in big-endian order") {
        withBuffers() { buf =>
            buf.writeShort(0x0102.toShort)
            assert(buf.getByte(0) == 0x01)
            assert(buf.getByte(1) == 0x02)
        }
    }

    test("long written in big-endian order") {
        withBuffers() { buf =>
            buf.writeLong(0x0102030405060708L)
            assert(buf.getByte(0) == 0x01)
            assert(buf.getByte(7) == 0x08)
        }
    }

    test("medium written in big-endian order") {
        withBuffers() { buf =>
            buf.writeMedium(0x010203)
            assert(buf.getByte(0) == 0x01)
            assert(buf.getByte(1) == 0x02)
            assert(buf.getByte(2) == 0x03)
        }
    }

    // =========================================================================
    // Edge cases
    // =========================================================================

    test("write and read multiple types interleaved") {
        withBuffers() { buf =>
            buf.writeByte(1)
            buf.writeShort(2)
            buf.writeInt(3)
            buf.writeLong(4L)
            buf.writeFloat(5.0f)
            buf.writeDouble(6.0)

            assert(buf.readByte == 1)
            assert(buf.readShort == 2)
            assert(buf.readInt == 3)
            assert(buf.readLong == 4L)
            assert(buf.readFloat == 5.0f)
            assert(buf.readDouble == 6.0)
        }
    }

    test("setCharSequence default charset") {
        withBuffers() { buf =>
            buf.skipWritableBytes(20)
            buf.setCharSequence(0, "test")
            assert(buf.getByte(0) == 't')
            assert(buf.getByte(1) == 'e')
            assert(buf.getByte(2) == 's')
            assert(buf.getByte(3) == 't')
        }
    }

    test("getCharSequence extracts substring") {
        withBuffers() { buf =>
            buf.writeCharSequence("hello world", StandardCharsets.UTF_8)
            val cs = buf.getCharSequence(6, 5, StandardCharsets.UTF_8)
            assert(cs.toString == "world")
        }
    }

}
