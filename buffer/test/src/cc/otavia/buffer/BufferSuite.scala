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

import org.scalatest.funsuite.AnyFunSuite

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util
import scala.language.unsafeNulls

class BufferSuite extends AnyFunSuite {


    test("heap buffer copy") {
        val buffer = Buffer.wrap(ByteBuffer.allocateDirect(4096))

        buffer.fill('A')

        val array      = new Array[Byte](10)
        val byteBuffer = ByteBuffer.allocate(10)
        val buffer1    = Buffer.wrap(ByteBuffer.allocateDirect(4096))

        buffer.copyInto(0, array, 0, 10)
        buffer.copyInto(0, byteBuffer, 0, 10)
        buffer.copyInto(0, buffer1, 0, 10)

        assert(array.forall(bt => bt == 'A'))
        assert(byteBuffer.get(0) == 'A')
        assert(buffer1.getByte(0) == 'A')

    }

    test("direct buffer copy") {
        val buffer = Buffer.wrap(ByteBuffer.allocateDirect(4096))

        buffer.fill('a')

        val array      = new Array[Byte](10)
        val byteBuffer = ByteBuffer.allocate(10)
        val buffer1    = Buffer.wrap(ByteBuffer.allocateDirect(4096))

        buffer.copyInto(0, array, 0, 10)
        buffer.copyInto(0, byteBuffer, 0, 10)
        buffer.copyInto(0, buffer1, 0, 10)

        assert(array.forall(bt => bt == 'a'))
        assert(byteBuffer.get(0) == 'a')
        assert(buffer1.getByte(1) == 'a')
    }

    test("string write/read in buffer") {
        val buffer    =  Buffer.wrap(ByteBuffer.allocateDirect(4096))

        buffer.writeChar('b')
        assert(buffer.writerOffset == 2)

        val pos1 = buffer.writerOffset
        buffer.writeCharSequence("hello world", StandardCharsets.UTF_8)
        val pos2 = buffer.writerOffset

        assert(buffer.readChar == 'b')
        assert(buffer.readCharSequence(pos2 - pos1, StandardCharsets.UTF_8).toString == "hello world")

        buffer.writeCharSequence("hello 你好！", StandardCharsets.UTF_8)
        val pos3 = buffer.writerOffset

        assert(buffer.readCharSequence(pos3 - pos2, StandardCharsets.UTF_8).toString == "hello 你好！")

        assert(buffer.readableBytes == 0)

        buffer.compact()
        assert(buffer.readableBytes == 0)
        assert(buffer.readerOffset == 0)
        assert(buffer.writerOffset == 0)

        buffer.writeCharLE('B')
        assert(buffer.readChar != 'B')

        buffer.compact()
        buffer.writeCharLE('B')
        assert(buffer.readCharLE == 'B')

        buffer.compact()
        buffer.writeCharLE('B')
        assert(Character.reverseBytes(buffer.readChar) == 'B')

    }

    test("compact buffer") {
        val buffer = Buffer.wrap(ByteBuffer.allocateDirect(4096))

        buffer.writeInt(2)
        assert(buffer.readerOffset == 0)
        assert(buffer.writerOffset == Integer.BYTES)

        buffer.readInt
        assert(buffer.readerOffset == 4)
        assert(buffer.writerOffset == Integer.BYTES)
        assert(buffer.readableBytes == 0)

        buffer.compact()
        assert(buffer.readerOffset == 0)
        assert(buffer.writerOffset == 0)

        buffer.writeMedium(12)
        buffer.writeCharSequence("hello world!", StandardCharsets.US_ASCII)

        assert(buffer.readableBytes == 3 + 12)

        buffer.readMedium
        assert(buffer.readableBytes == 12)

        assert(buffer.readerOffset == 3)

        buffer.compact()
        assert(buffer.readableBytes == 12)

        assert(buffer.readerOffset == 0)

        assert(buffer.readCharSequence(12, StandardCharsets.US_ASCII).toString == "hello world!")

        assert(buffer.readableBytes == 0)
        assert(buffer.readerOffset == 12)

        buffer.compact()

        assert(buffer.readerOffset == 0)

    }

    test("Buffer write/read ByteBuffer") {
        val buffer = Buffer.wrap(ByteBuffer.allocate(4096))

        val byteBuffer = ByteBuffer.allocate(2000)
        byteBuffer.position(300)
        for (idx <- 0 until 277) byteBuffer.putInt(idx)
        byteBuffer.flip() // to reader mode
        byteBuffer.position(300)

        buffer.writeBytes(byteBuffer)

        assert(buffer.readableBytes == 277 * 4)
        assert(buffer.readInt == 0)
        assert(buffer.readInt == 1)

        byteBuffer.clear() // to writer mode

        buffer.readBytes(byteBuffer, 51 * 4)

        assert(buffer.readableBytes == (277 - 2 - 51) * 4)
        assert(byteBuffer.position() == 51 * 4)

        byteBuffer.flip() // to reader mode
        buffer.writeBytes(byteBuffer, 51 * 4)

        assert(buffer.readableBytes == (277 - 2) * 4)
        assert(byteBuffer.limit() == byteBuffer.position())

        byteBuffer.clear()
        for (idx <- 0 until 277) byteBuffer.putInt(idx)
        byteBuffer.flip() // to reader mode

        buffer.writeBytes(byteBuffer, 47 * 4)

        assert(buffer.readableBytes == (277 - 2 + 47) * 4)

    }

    test("Buffer write/read AbstractBuffer") {
        val buffer = Buffer.wrap(ByteBuffer.allocate(4096))
        val buf    = Buffer.wrap(ByteBuffer.allocate(4096))

        for (idx <- 0 until 1000) buf.writeInt(idx)

        assert(buf.readableBytes == 1000 * 4)
        assert(buffer.readableBytes == 0)

        buffer.writeBytes(buf)

        assert(buf.readableBytes == 0)
        assert(buffer.readableBytes == 1000 * 4)

        buf.compact()
        buffer.readBytes(buf, 500 * 4)

        assert(buf.readableBytes == 500 * 4)
        assert(buffer.readableBytes == 500 * 4)

        val writable = buffer.writableBytes
        val len      = 600 * 4
        val readable = buf.readableBytes
        val length   = math.min(writable, math.min(len, readable))
        buffer.writeBytes(buf, len)

        assert(buffer.readableBytes == 500 * 4 + length)
        assert(buf.readableBytes == 500 * 4 - length)

        buffer.readBytes(buf, 500 * 4)

        assert(buffer.readableBytes == 500 * 4 + length - 500 * 4)
        assert(buf.readableBytes == 500 * 4 - length + 500 * 4)

    }

    test("Buffer write/read Array[Byte]") {
        val buffer = Buffer.wrap(ByteBuffer.allocate(4096))

        val array = new Array[Byte](1000)
        util.Arrays.fill(array, 'B'.toByte)

        buffer.writeBytes(array)

        assert(buffer.readableBytes == 1000)

        buffer.writeBytes(array, 100, 200)

        assert(buffer.readableBytes == 1200)

        for (idx <- 0 until 100) buffer.writeChar('C')

        buffer.readerOffset(1200)

        buffer.readBytes(array, 10, 40)

        val byteBuffer = ByteBuffer.wrap(array)

        for (idx <- 0 until 15) assert(byteBuffer.getChar(10 + idx * 2) == 'C')

        assert(true)

    }

    test("Buffer bytesBefore") {
        val buffer = Buffer.wrap(ByteBuffer.allocateDirect(4096))
        buffer.fill('C'.toByte)
        val start = 100
        val end   = 4006
        buffer.writerOffset(end)
        buffer.readerOffset(start)

        buffer.setByte(678, '\n')

        assert(buffer.bytesBefore('\n'.toByte) == 678 - start)

        buffer.setByte(1988, '\r')
        buffer.setByte(1989, '\n')

        assert(buffer.bytesBefore('\r'.toByte, '\n') == 1988 - start)

        buffer.setByte(3987, 'A')
        buffer.setByte(3988, 'S')
        buffer.setByte(3989, 'C')

        assert(buffer.bytesBefore('A'.toByte, 'S', 'C') == 3987 - start)

        buffer.setByte(3996, 'H')
        buffer.setByte(3997, 'T')
        buffer.setByte(3998, 'T')
        buffer.setByte(3999, 'P')

        assert(buffer.bytesBefore('H'.toByte, 'T', 'T', 'P') == 3996 - start)

        buffer.setByte(3999 + 1, ' ')
        buffer.setByte(3999 + 2, '1')
        buffer.setByte(3999 + 3, '.')
        buffer.setByte(3999 + 4, '1')

        assert(buffer.bytesBefore("HTTP 1.1".getBytes) == 3996 - start)

        assert(buffer.bytesBefore('h'.toByte) == -1)
        assert(buffer.bytesBefore('h'.toByte, 'e') == -1)
        assert(buffer.bytesBefore('h'.toByte, 'e', 'l') == -1)
        assert(buffer.bytesBefore('h'.toByte, 'e', 'l', 'l') == -1)

        assert(buffer.bytesBefore("hello".getBytes) == -1)

        buffer.setByte(start - 1, 'N')
        assert(buffer.bytesBefore('N'.toByte) == -1)
        buffer.setByte(start, 'T')
        assert(buffer.bytesBefore('S'.toByte, 'T') == -1)
        assert(buffer.bytesBefore('T'.toByte) == start - start)

        buffer.setByte(end - 1, 'O')
        buffer.setByte(end, 'K')
        assert(buffer.bytesBefore('O'.toByte) == end - start - 1)
        assert(buffer.bytesBefore('K'.toByte) == -1)
        assert(buffer.bytesBefore('O'.toByte, 'K') == -1)

    }

    test("Buffer base write/read ") {
        val buffer = Buffer.wrap(ByteBuffer.allocateDirect(4096))

        buffer.writeByte('A')
        assert(buffer.readerOffset == 0)
        assert(buffer.writerOffset == 1)
        assert(buffer.getByte(buffer.readerOffset) == 'A')
        assert(buffer.readerOffset == 0)
        assert(buffer.writerOffset == 1)
        assert(buffer.readByte == 'A')
        assert(buffer.readerOffset == 1)
        assert(buffer.writerOffset == 1)

    }

}
