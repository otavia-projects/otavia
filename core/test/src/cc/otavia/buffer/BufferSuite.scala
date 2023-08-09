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

import cc.otavia.core.buffer.AdaptiveBuffer
import org.scalatest.funsuite.AnyFunSuite

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import scala.language.unsafeNulls

class BufferSuite extends AnyFunSuite {

    private val heapPageAllocator: PageBufferAllocator   = BufferAllocator.heapPageAllocator
    private val directPageAllocator: PageBufferAllocator = BufferAllocator.directPageAllocator

    private val adaptiveBuffer: AdaptiveBuffer = AdaptiveBuffer(heapPageAllocator)

    test("heap buffer copy") {
        val allocator = BufferAllocator.heapPageAllocator
        val buffer    = allocator.allocate()

        buffer.fill('A')

        val array      = new Array[Byte](10)
        val byteBuffer = ByteBuffer.allocate(10)
        val buffer1    = allocator.allocate()

        buffer.copyInto(0, array, 0, 10)
        buffer.copyInto(0, byteBuffer, 0, 10)
        buffer.copyInto(0, buffer1, 0, 10)

        assert(array.forall(bt => bt == 'A'))
        assert(byteBuffer.get(0) == 'A')
        assert(buffer1.getByte(0) == 'A')

    }

    test("direct buffer copy") {
        val allocator = BufferAllocator.directPageAllocator
        val buffer    = allocator.allocate()

        buffer.fill('a')

        val array      = new Array[Byte](10)
        val byteBuffer = ByteBuffer.allocate(10)
        val buffer1    = allocator.allocate()

        buffer.copyInto(0, array, 0, 10)
        buffer.copyInto(0, byteBuffer, 0, 10)
        buffer.copyInto(0, buffer1, 0, 10)

        assert(array.forall(bt => bt == 'a'))
        assert(byteBuffer.get(0) == 'a')
        assert(buffer1.getByte(1) == 'a')
    }

    test("string op in buffer") {
        val allocator = BufferAllocator.directPageAllocator
        val buffer    = allocator.allocate()

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
        val allocator = BufferAllocator.directPageAllocator
        val buffer    = allocator.allocate()

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

    test("write bytes to buffer") {
        val buffer = heapPageAllocator.allocate()

        val adaptiveBuffer = AdaptiveBuffer(heapPageAllocator)

        for (idx <- 0 until 1600) adaptiveBuffer.writeInt(idx)

        for (idx <- 0 until 800) adaptiveBuffer.readInt

        buffer.writeBytes(adaptiveBuffer)

    }

}
