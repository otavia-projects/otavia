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

package cc.otavia.buffer.pool

import cc.otavia.buffer.{Buffer, BufferClosedException}
import org.scalatest.funsuite.AnyFunSuite

import java.nio.{ByteBuffer, ByteOrder}
import java.nio.charset.StandardCharsets
import scala.language.unsafeNulls

class AdaptiveBufferComprehensiveSuite extends AnyFunSuite {

    private val pageSize = 64 // small page size to force cross-page boundaries
    private val heapAllocator = new HeapPooledPageAllocator(pageSize, 2, 1024)
    private val directAllocator = new DirectPooledPageAllocator(pageSize, 2, 1024)

    private def heapAdaptive(): AdaptiveBuffer = AdaptiveBuffer(heapAllocator)
    private def directAdaptive(): AdaptiveBuffer = AdaptiveBuffer(directAllocator)

    private def withAdaptive(f: AdaptiveBuffer => Unit): Unit = {
        f(heapAdaptive())
        f(directAdaptive())
    }

    // =========================================================================
    // Basic properties
    // =========================================================================

    test("capacity is Int.MaxValue") {
        withAdaptive { buf =>
            assert(buf.capacity == Int.MaxValue)
        }
    }

    test("initial state") {
        withAdaptive { buf =>
            assert(buf.readerOffset == 0)
            assert(buf.writerOffset == 0)
            assert(buf.readableBytes == 0)
            assert(buf.writableBytes == Int.MaxValue)
        }
    }

    test("isDirect reflects allocator") {
        assert(heapAdaptive().isDirect == false)
        assert(directAdaptive().isDirect == true)
    }

    // =========================================================================
    // Byte operations
    // =========================================================================

    test("writeByte readByte within single page") {
        withAdaptive { buf =>
            for (i <- 0 until 10) buf.writeByte(i.toByte)
            assert(buf.writerOffset == 10)
            for (i <- 0 until 10) assert(buf.readByte == i.toByte)
            assert(buf.readerOffset == 10)
        }
    }

    test("writeByte readByte across page boundary") {
        withAdaptive { buf =>
            // Fill first page (64 bytes) minus 1
            for (i <- 0 until 63) buf.writeByte(i.toByte)
            // Next write crosses boundary
            buf.writeByte(99.toByte)
            buf.writeByte(100.toByte)
            assert(buf.writerOffset == 65)

            for (i <- 0 until 63) assert(buf.readByte == i.toByte)
            assert(buf.readByte == 99.toByte)
            assert(buf.readByte == 100.toByte)
        }
    }

    test("getByte setByte within page") {
        withAdaptive { buf =>
            for (i <- 0 until 10) buf.writeByte(0)
            buf.setByte(5, 42.toByte)
            assert(buf.getByte(5) == 42.toByte)
        }
    }

    test("getByte setByte across page boundary") {
        withAdaptive { buf =>
            // Write enough to have 2+ pages
            for (i <- 0 until 128) buf.writeByte(0)
            buf.setByte(63, 0xAA.toByte)  // last byte of first page
            buf.setByte(64, 0xBB.toByte)  // first byte of second page
            assert(buf.getByte(63) == 0xAA.toByte)
            assert(buf.getByte(64) == 0xBB.toByte)
        }
    }

    test("getByte at page boundary - last byte of page") {
        withAdaptive { buf =>
            for (i <- 0 until 65) buf.writeByte(i.toByte)
            // byte at index 63 is the last byte of page 0
            assert(buf.getByte(63) == 63.toByte)
            // byte at index 64 is first byte of page 1
            assert(buf.getByte(64) == 64.toByte)
        }
    }

    test("readUnsignedByte getUnsignedByte") {
        withAdaptive { buf =>
            buf.writeByte(0xFF.toByte)
            assert(buf.getUnsignedByte(0) == 255)
            assert(buf.readUnsignedByte == 255)
        }
    }

    // =========================================================================
    // Char operations (2 bytes)
    // =========================================================================

    test("writeChar readChar within page") {
        withAdaptive { buf =>
            buf.writeChar('A')
            assert(buf.readChar == 'A')
        }
    }

    test("writeChar readChar crossing page boundary") {
        withAdaptive { buf =>
            // Fill to offset 63 (one byte before page boundary)
            for (i <- 0 until 63) buf.writeByte(0)
            // Char at offset 63-64 crosses page boundary
            buf.writeChar('Z')
            assert(buf.writerOffset == 65)

            buf.readerOffset(63)
            assert(buf.readChar == 'Z')
        }
    }

    test("getChar setChar at page boundary") {
        withAdaptive { buf =>
            for (i <- 0 until 128) buf.writeByte(0)
            buf.setChar(63, 'X')  // crosses page boundary
            assert(buf.getChar(63) == 'X')
        }
    }

    // =========================================================================
    // Short operations (2 bytes)
    // =========================================================================

    test("writeShort readShort crossing page boundary") {
        withAdaptive { buf =>
            for (i <- 0 until 63) buf.writeByte(0)
            buf.writeShort(0x1234.toShort)
            buf.readerOffset(63)
            assert(buf.readShort == 0x1234.toShort)
        }
    }

    test("getShort setShort at page boundary") {
        withAdaptive { buf =>
            for (i <- 0 until 128) buf.writeByte(0)
            buf.setShort(63, 0xABCD.toShort)
            assert(buf.getShort(63) == 0xABCD.toShort)
        }
    }

    test("readUnsignedShort crossing page boundary") {
        withAdaptive { buf =>
            for (i <- 0 until 63) buf.writeByte(0)
            buf.writeUnsignedShort(65535)
            buf.readerOffset(63)
            assert(buf.readUnsignedShort == 65535)
        }
    }

    // =========================================================================
    // Medium operations (3 bytes)
    // =========================================================================

    test("writeMedium readMedium crossing page boundary - offset 62") {
        withAdaptive { buf =>
            // 3 bytes at offset 62 crosses from page 0 into page 1
            for (i <- 0 until 62) buf.writeByte(0)
            buf.writeMedium(0x123456)
            buf.readerOffset(62)
            assert(buf.readMedium == 0x123456)
        }
    }

    test("writeMedium readMedium crossing page boundary - offset 63") {
        withAdaptive { buf =>
            for (i <- 0 until 63) buf.writeByte(0)
            buf.writeMedium(0x123456) // positive medium (MSB bit 23 = 0)
            buf.readerOffset(63)
            assert(buf.readMedium == 0x123456)
        }
    }

    test("getMedium setMedium at page boundary") {
        withAdaptive { buf =>
            for (i <- 0 until 128) buf.writeByte(0)
            buf.setMedium(62, 0x123456)
            assert(buf.getMedium(62) == 0x123456)
            buf.setMedium(63, 0x789ABC)
            assert(buf.getMedium(63) == 0x789ABC)
        }
    }

    test("writeMediumLE readMediumLE crossing page boundary") {
        withAdaptive { buf =>
            for (i <- 0 until 63) buf.writeByte(0)
            buf.writeMediumLE(0x123456)
            buf.readerOffset(63)
            assert(buf.readMediumLE == 0x123456)
        }
    }

    test("readUnsignedMedium crossing page boundary") {
        withAdaptive { buf =>
            for (i <- 0 until 63) buf.writeByte(0)
            buf.writeUnsignedMedium(0xABCDEF)
            buf.readerOffset(63)
            assert(buf.readUnsignedMedium == 0xABCDEF)
        }
    }

    // =========================================================================
    // Int operations (4 bytes)
    // =========================================================================

    test("writeInt readInt within page") {
        withAdaptive { buf =>
            buf.writeInt(0x12345678)
            assert(buf.readInt == 0x12345678)
        }
    }

    test("writeInt readInt crossing page boundary - offset 61") {
        withAdaptive { buf =>
            // 4 bytes at offset 61: bytes 61,62,63 on page 0, byte 64 on page 1
            for (i <- 0 until 61) buf.writeByte(0)
            buf.writeInt(0xDEADBEEF)
            buf.readerOffset(61)
            assert(buf.readInt == 0xDEADBEEF)
        }
    }

    test("writeInt readInt crossing page boundary - offset 62") {
        withAdaptive { buf =>
            for (i <- 0 until 62) buf.writeByte(0)
            buf.writeInt(0x11223344)
            buf.readerOffset(62)
            assert(buf.readInt == 0x11223344)
        }
    }

    test("writeInt readInt crossing page boundary - offset 63") {
        withAdaptive { buf =>
            for (i <- 0 until 63) buf.writeByte(0)
            buf.writeInt(0x55667788)
            buf.readerOffset(63)
            assert(buf.readInt == 0x55667788)
        }
    }

    test("writeInt readInt crossing page boundary - offset 64") {
        withAdaptive { buf =>
            for (i <- 0 until 64) buf.writeByte(0)
            buf.writeInt(0x99AABBCC)
            buf.readerOffset(64)
            assert(buf.readInt == 0x99AABBCC)
        }
    }

    test("getInt setInt at page boundary") {
        withAdaptive { buf =>
            for (i <- 0 until 128) buf.writeByte(0)
            buf.setInt(61, 0xAABBCCDD)
            assert(buf.getInt(61) == 0xAABBCCDD)
            buf.setInt(62, 0x11223344)
            assert(buf.getInt(62) == 0x11223344)
            buf.setInt(63, 0x55667788)
            assert(buf.getInt(63) == 0x55667788)
        }
    }

    test("readUnsignedInt crossing page boundary") {
        withAdaptive { buf =>
            for (i <- 0 until 62) buf.writeByte(0)
            buf.writeUnsignedInt(0x80000000L)
            buf.readerOffset(62)
            assert(buf.readUnsignedInt == 0x80000000L)
        }
    }

    test("writeIntLE readIntLE crossing page boundary") {
        withAdaptive { buf =>
            for (i <- 0 until 63) buf.writeByte(0)
            buf.writeIntLE(0x12345678)
            buf.readerOffset(63)
            assert(buf.readIntLE == 0x12345678)
        }
    }

    // =========================================================================
    // Float operations (4 bytes)
    // =========================================================================

    test("writeFloat readFloat crossing page boundary") {
        withAdaptive { buf =>
            for (i <- 0 until 62) buf.writeByte(0)
            buf.writeFloat(3.14f)
            buf.readerOffset(62)
            assert(buf.readFloat == 3.14f)
        }
    }

    test("getFloat setFloat at page boundary") {
        withAdaptive { buf =>
            for (i <- 0 until 128) buf.writeByte(0)
            buf.setFloat(63, 2.718f)
            assert(buf.getFloat(63) == 2.718f)
        }
    }

    // =========================================================================
    // Long operations (8 bytes)
    // =========================================================================

    test("writeLong readLong within page") {
        withAdaptive { buf =>
            buf.writeLong(0x123456789ABCDEF0L)
            assert(buf.readLong == 0x123456789ABCDEF0L)
        }
    }

    test("writeLong readLong crossing page boundary") {
        withAdaptive { buf =>
            // 8 bytes at offset 57: crosses from page 0 into page 1
            for (i <- 0 until 57) buf.writeByte(0)
            buf.writeLong(0xCAFEBABE12345678L)
            buf.readerOffset(57)
            assert(buf.readLong == 0xCAFEBABE12345678L)
        }
    }

    test("writeLong readLong crossing at exact page boundary") {
        withAdaptive { buf =>
            // 8 bytes at offset 60: 4 bytes on page 0, 4 bytes on page 1
            for (i <- 0 until 60) buf.writeByte(0)
            buf.writeLong(0x0102030405060708L)
            buf.readerOffset(60)
            assert(buf.readLong == 0x0102030405060708L)
        }
    }

    test("writeLong readLong all bytes on second page") {
        withAdaptive { buf =>
            for (i <- 0 until 64) buf.writeByte(0)
            buf.writeLong(0xAABBCCDDEEFF0011L)
            buf.readerOffset(64)
            assert(buf.readLong == 0xAABBCCDDEEFF0011L)
        }
    }

    test("getLong setLong at page boundary") {
        withAdaptive { buf =>
            for (i <- 0 until 128) buf.writeByte(0)
            buf.setLong(57, 0x123456789ABCDEF0L)
            assert(buf.getLong(57) == 0x123456789ABCDEF0L)
            buf.setLong(60, 0xCAFEBABECAFEBABEL)
            assert(buf.getLong(60) == 0xCAFEBABECAFEBABEL)
            buf.setLong(63, 0x0102030405060708L)
            assert(buf.getLong(63) == 0x0102030405060708L)
        }
    }

    test("writeLongLE readLongLE crossing page boundary") {
        withAdaptive { buf =>
            for (i <- 0 until 60) buf.writeByte(0)
            buf.writeLongLE(0x123456789ABCDEF0L)
            buf.readerOffset(60)
            assert(buf.readLongLE == 0x123456789ABCDEF0L)
        }
    }

    // =========================================================================
    // Double operations (8 bytes)
    // =========================================================================

    test("writeDouble readDouble crossing page boundary") {
        withAdaptive { buf =>
            for (i <- 0 until 60) buf.writeByte(0)
            buf.writeDouble(3.141592653589793)
            buf.readerOffset(60)
            assert(buf.readDouble == 3.141592653589793)
        }
    }

    test("getDouble setDouble at page boundary") {
        withAdaptive { buf =>
            for (i <- 0 until 128) buf.writeByte(0)
            buf.setDouble(60, 1.41421356)
            assert(buf.getDouble(60) == 1.41421356)
        }
    }

    // =========================================================================
    // Mixed type operations across pages
    // =========================================================================

    test("multiple types across page boundaries") {
        withAdaptive { buf =>
            buf.writeByte(1.toByte)                    // 1 byte
            buf.writeShort(2.toShort)                  // 2 bytes
            buf.writeMedium(0x030405)                  // 3 bytes
            buf.writeInt(6)                             // 4 bytes
            buf.writeLong(7L)                           // 8 bytes
            buf.writeFloat(8.0f)                        // 4 bytes
            buf.writeDouble(9.0)                        // 8 bytes
            // Total: 1+2+3+4+8+4+8 = 30 bytes

            assert(buf.readByte == 1)
            assert(buf.readShort == 2)
            assert(buf.readMedium == 0x030405)
            assert(buf.readInt == 6)
            assert(buf.readLong == 7L)
            assert(buf.readFloat == 8.0f)
            assert(buf.readDouble == 9.0)
        }
    }

    test("big-endian byte order verified across pages for int") {
        withAdaptive { buf =>
            // Write int at a position that crosses page boundary
            for (i <- 0 until 62) buf.writeByte(0)
            buf.writeInt(0x01020304)
            // Verify byte order
            assert(buf.getByte(62) == 0x01)
            assert(buf.getByte(63) == 0x02)
            assert(buf.getByte(64) == 0x03)
            assert(buf.getByte(65) == 0x04)
        }
    }

    test("big-endian byte order verified across pages for long") {
        withAdaptive { buf =>
            for (i <- 0 until 60) buf.writeByte(0)
            buf.writeLong(0x0102030405060708L)
            assert(buf.getByte(60) == 0x01)
            assert(buf.getByte(61) == 0x02)
            assert(buf.getByte(62) == 0x03)
            assert(buf.getByte(63) == 0x04)
            assert(buf.getByte(64) == 0x05)
            assert(buf.getByte(65) == 0x06)
            assert(buf.getByte(66) == 0x07)
            assert(buf.getByte(67) == 0x08)
        }
    }

    // =========================================================================
    // Bulk operations
    // =========================================================================

    test("writeBytes Array[Byte] crossing pages") {
        withAdaptive { buf =>
            for (i <- 0 until 60) buf.writeByte(0)
            val data = Array.tabulate[Byte](20)(i => i.toByte)
            buf.writeBytes(data)
            assert(buf.writerOffset == 80)

            buf.readerOffset(60)
            val dest = new Array[Byte](20)
            buf.readBytes(dest)
            assert(dest sameElements data)
        }
    }

    test("writeBytes fill crossing pages") {
        withAdaptive { buf =>
            buf.writeBytes(100, 0xAA.toByte)
            assert(buf.writerOffset == 100)
            for (i <- 0 until 100) assert(buf.getByte(i) == 0xAA.toByte)
        }
    }

    test("readBytes Array[Byte] crossing pages") {
        withAdaptive { buf =>
            for (i <- 0 until 100) buf.writeByte(i.toByte)
            val dest = new Array[Byte](50)
            buf.readBytes(dest)
            for (i <- 0 until 50) assert(dest(i) == i.toByte)
        }
    }

    test("writeBytes Buffer crossing pages") {
        withAdaptive { buf =>
            val src = heapAdaptive()
            for (i <- 0 until 100) src.writeByte(i.toByte)
            buf.writeBytes(src, 100)
            assert(buf.writerOffset == 100)
            for (i <- 0 until 100) assert(buf.getByte(i) == i.toByte)
        }
    }

    test("writeBytes Buffer between adaptive buffers - zero copy") {
        val src = heapAdaptive()
        val dest = heapAdaptive()
        for (i <- 0 until 100) src.writeByte(i.toByte)
        dest.writeBytes(src, 50)
        // dest now has 50 bytes from src
        assert(dest.writerOffset == 50)
        for (i <- 0 until 50) assert(dest.getByte(i) == i.toByte)
    }

    // =========================================================================
    // copyInto across pages
    // =========================================================================

    test("copyInto Array[Byte] crossing page boundary") {
        withAdaptive { buf =>
            for (i <- 0 until 128) buf.writeByte(i.toByte)
            val dest = new Array[Byte](8)
            // Copy from offset 60, crossing page boundary at 64
            buf.copyInto(60, dest, 0, 8)
            for (i <- 0 until 8) assert(dest(i) == (60 + i).toByte)
        }
    }

    test("copyInto ByteBuffer crossing page boundary") {
        withAdaptive { buf =>
            for (i <- 0 until 128) buf.writeByte(i.toByte)
            val dest = ByteBuffer.allocate(8)
            buf.copyInto(60, dest, 0, 8)
            for (i <- 0 until 8) assert(dest.get(i) == (60 + i).toByte)
        }
    }

    // =========================================================================
    // setBytes at cross-page boundaries
    // =========================================================================

    test("setBytes crossing page boundary") {
        withAdaptive { buf =>
            for (i <- 0 until 128) buf.writeByte(0)
            val data = Array[Byte](1, 2, 3, 4, 5, 6, 7, 8)
            buf.setBytes(60, data) // crosses page boundary at 64
            for (i <- 0 until 8) assert(buf.getByte(60 + i) == (i + 1).toByte)
        }
    }

    // =========================================================================
    // readerOffset / writerOffset manipulation
    // =========================================================================

    test("readerOffset advances and recycles pages") {
        withAdaptive { buf =>
            for (i <- 0 until 200) buf.writeByte(i.toByte)
            assert(buf.writerOffset == 200)

            buf.readerOffset(100)
            assert(buf.readerOffset == 100)
            // First page should be recycled, readByte at 100
            assert(buf.readByte == 100.toByte)
        }
    }

    test("writerOffset decreases and releases pages") {
        withAdaptive { buf =>
            for (i <- 0 until 200) buf.writeByte(i.toByte)
            assert(buf.writerOffset == 200)

            buf.writerOffset(10)
            assert(buf.writerOffset == 10)
        }
    }

    test("writerOffset expands beyond current capacity") {
        withAdaptive { buf =>
            buf.writerOffset(200) // forces expansion
            assert(buf.writerOffset == 200)
            buf.writeByte(42.toByte)
            assert(buf.getByte(200) == 42.toByte)
        }
    }

    // =========================================================================
    // compact
    // =========================================================================

    test("compact shifts readable data") {
        withAdaptive { buf =>
            for (i <- 0 until 100) buf.writeByte(i.toByte)
            buf.readerOffset(50)
            buf.compact()
            assert(buf.readerOffset == 0)
            // writerOffset should be reduced by readerOffset amount
            assert(buf.readByte == 50.toByte)
        }
    }

    // =========================================================================
    // CharSequence operations across pages
    // =========================================================================

    test("writeCharSequence readCharSequence crossing pages") {
        withAdaptive { buf =>
            for (i <- 0 until 60) buf.writeByte(0)
            buf.writeCharSequence("hello", StandardCharsets.UTF_8)
            assert(buf.writerOffset == 65)
            buf.readerOffset(60)
            assert(buf.readCharSequence(5, StandardCharsets.UTF_8).toString == "hello")
        }
    }

    // =========================================================================
    // BytesBefore across pages
    // =========================================================================

    test("bytesBefore single byte across pages") {
        withAdaptive { buf =>
            // Write 100 zeros, with specific bytes set at known positions
            for (i <- 0 until 100) {
                if (i == 63) buf.writeByte('X') // last byte of page 0
                else buf.writeByte(0)
            }
            assert(buf.bytesBefore('X'.toByte) == 63)

            buf.readerOffset(0)
            // Byte at offset 64 (first of page 1) should also be findable
            buf.setByte(64, 'Y')
            assert(buf.bytesBefore('Y'.toByte) == 64)
        }
    }

    test("bytesBefore two bytes across page boundary") {
        withAdaptive { buf =>
            for (i <- 0 until 128) buf.writeByte(0)
            buf.setByte(63, 'A')
            buf.setByte(64, 'B')
            assert(buf.bytesBefore('A'.toByte, 'B'.toByte) == 63)
        }
    }

    test("bytesBefore array across pages") {
        withAdaptive { buf =>
            for (i <- 0 until 128) buf.writeByte(0)
            buf.setCharSequence(62, "ABCD")
            assert(buf.bytesBefore("ABCD".getBytes(StandardCharsets.UTF_8)) == 62)
        }
    }

    // =========================================================================
    // Cursor across pages
    // =========================================================================

    test("openCursor across page boundary") {
        withAdaptive { buf =>
            for (i <- 0 until 100) buf.writeByte(i.toByte)
            val cursor = buf.openCursor(60, 20) // starts on page 0, crosses into page 1
            var count = 0
            var values = List.empty[Byte]
            while (cursor.readByte) {
                values = values :+ cursor.getByte
                count += 1
            }
            assert(count == 20)
            assert(values(0) == 60.toByte)
            assert(values(4) == 64.toByte) // first byte of page 1
            assert(values(19) == 79.toByte)
        }
    }

    test("openReverseCursor across page boundary") {
        withAdaptive { buf =>
            for (i <- 0 until 100) buf.writeByte(i.toByte)
            val cursor = buf.openReverseCursor(65, 4) // from offset 65 backwards across page boundary
            var values = List.empty[Byte]
            while (cursor.readByte) {
                values = values :+ cursor.getByte
            }
            assert(values.length == 4)
            assert(values(0) == 65.toByte)
            assert(values(1) == 64.toByte)
            assert(values(2) == 63.toByte)
            assert(values(3) == 62.toByte)
        }
    }

    // =========================================================================
    // Peek/check methods
    // =========================================================================

    test("nextIs across pages") {
        withAdaptive { buf =>
            for (i <- 0 until 65) buf.writeByte(0)
            buf.setByte(64, 42.toByte)
            buf.readerOffset(64)
            assert(buf.nextIs(42.toByte))
        }
    }

    test("nextAre across page boundary") {
        withAdaptive { buf =>
            for (i <- 0 until 66) buf.writeByte(0)
            buf.setByte(63, 1)
            buf.setByte(64, 2)
            buf.readerOffset(63)
            assert(buf.nextAre(Array[Byte](1, 2)))
        }
    }

    test("skipIfNextAre across page boundary") {
        withAdaptive { buf =>
            for (i <- 0 until 66) buf.writeByte(0)
            buf.setByte(63, 'A')
            buf.setByte(64, 'B')
            buf.readerOffset(63)
            assert(buf.skipIfNextAre(Array[Byte]('A', 'B')))
            assert(buf.readerOffset == 65)
        }
    }

    // =========================================================================
    // splitBefore and splitLast
    // =========================================================================

    test("splitBefore at page boundary") {
        withAdaptive { buf =>
            for (i <- 0 until 200) buf.writeByte(i.toByte)
            val chain = buf.splitBefore(64) // split at exact page boundary
            assert(buf.readerOffset == 64)
            assert(buf.readByte == 64.toByte)
        }
    }

    test("splitLast removes last page") {
        withAdaptive { buf =>
            for (i <- 0 until 100) buf.writeByte(i.toByte)
            val last = buf.splitLast()
            assert(buf.writerOffset < 100)
            last.close()
        }
    }

    // =========================================================================
    // extend
    // =========================================================================

    test("extend adds a pre-allocated buffer") {
        withAdaptive { buf =>
            val page = heapAllocator.allocate()
            page.writeByte(42)
            page.writerOffset(1)
            buf.extend(page)
            assert(buf.writerOffset == 1)
            assert(buf.readByte == 42)
        }
    }

    // =========================================================================
    // Clean and close
    // =========================================================================

    test("clean resets to empty state") {
        withAdaptive { buf =>
            for (i <- 0 until 200) buf.writeByte(i.toByte)
            buf.readerOffset(50)
            buf.clean()
            assert(buf.readerOffset == 0)
            assert(buf.writerOffset == 0)
            assert(buf.readableBytes == 0)
        }
    }

    test("close marks buffer as closed") {
        withAdaptive { buf =>
            assert(!buf.closed)
            buf.close()
            assert(buf.closed)
        }
    }

    test("operations on closed buffer throw") {
        withAdaptive { buf =>
            buf.close()
            assertThrows[BufferClosedException] {
                buf.openCursor(0, 0)
            }
        }
    }

    // =========================================================================
    // Boolean operations
    // =========================================================================

    test("writeBoolean readBoolean") {
        withAdaptive { buf =>
            buf.writeBoolean(true)
            buf.writeBoolean(false)
            assert(buf.readBoolean == true)
            assert(buf.readBoolean == false)
        }
    }

    // =========================================================================
    // String parsing methods
    // =========================================================================

    test("readStringAsLong within single page") {
        withAdaptive { buf =>
            buf.writeCharSequence("12345", StandardCharsets.UTF_8)
            assert(buf.readStringAsLong(5) == 12345L)
        }
    }

    test("getStringAsDouble within single page") {
        withAdaptive { buf =>
            buf.writeCharSequence("3.14", StandardCharsets.UTF_8)
            assert(buf.getStringAsDouble(0, 4) == 3.14)
        }
    }

    // =========================================================================
    // Large write spanning many pages
    // =========================================================================

    test("write and read large amount spanning multiple pages") {
        withAdaptive { buf =>
            val count = 1000
            for (i <- 0 until count) buf.writeInt(i)
            assert(buf.writerOffset == count * 4)
            for (i <- 0 until count) {
                val v = buf.readInt
                assert(v == i, s"Expected $i but got $v at index ${i * 4}")
            }
        }
    }

    test("writeBytes large array spanning multiple pages") {
        withAdaptive { buf =>
            val data = Array.tabulate[Byte](500)(i => (i % 127).toByte)
            buf.writeBytes(data)
            assert(buf.writerOffset == 500)

            val dest = new Array[Byte](500)
            buf.readBytes(dest)
            assert(dest sameElements data)
        }
    }

}
