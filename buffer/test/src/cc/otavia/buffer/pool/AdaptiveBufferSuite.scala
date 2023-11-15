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

import cc.otavia.buffer.pool.{AdaptiveBuffer, HeapPooledPageAllocator, PooledPageAllocator}
import org.scalatest.funsuite.AnyFunSuite

import scala.language.unsafeNulls

class AdaptiveBufferSuite extends AnyFunSuite {

    val allocator: PooledPageAllocator = new HeapPooledPageAllocator()
    val adaptiveBuffer: AdaptiveBuffer = AdaptiveBuffer(allocator)

    def bufferSize: Int = allocator.fixedCapacity

    test("splitBefore one buffer") {
        val adaptive = AdaptiveBuffer(allocator)

        for (idx <- 0 until 1024) adaptive.writeInt(idx)

        for (idx <- 0 until 1024) assert(adaptive.getInt(idx * Integer.BYTES) == idx)

        val SPLIT_OFFSET = 700

        val pageChain = adaptive.splitBefore(4 * SPLIT_OFFSET)

        assert(pageChain.next == null)

        assert(adaptive.readerOffset == 4 * SPLIT_OFFSET)

        assert(pageChain.getInt(4 * (SPLIT_OFFSET - 1)) == SPLIT_OFFSET - 1)
        assert(adaptive.readInt == SPLIT_OFFSET)

        assert(adaptive.readerOffset == 4 * (SPLIT_OFFSET + 1))

    }

    test("splitBefore much buffer split at head") {
        val adaptive = AdaptiveBuffer(allocator)

        for (idx <- 0 until 1024 * 8) adaptive.writeInt(idx)

        for (idx <- 0 until 1024 * 8) assert(adaptive.getInt(idx * Integer.BYTES) == idx)

        val SPLIT_OFFSET = 700

        val pageChain = adaptive.splitBefore(4 * SPLIT_OFFSET)

        assert(pageChain.next == null)

        assert(adaptive.readerOffset == 4 * SPLIT_OFFSET)

        assert(pageChain.getInt(4 * (SPLIT_OFFSET - 1)) == SPLIT_OFFSET - 1)
        assert(adaptive.readInt == SPLIT_OFFSET)

        assert(adaptive.readerOffset == 4 * (SPLIT_OFFSET + 1))

    }

    test("splitBefore much buffer split not head") {
        val adaptive = AdaptiveBuffer(allocator)

        for (idx <- 0 until 1024 * 8) adaptive.writeInt(idx)

        for (idx <- 0 until 1024 * 8) assert(adaptive.getInt(idx * Integer.BYTES) == idx)

        val SPLIT_OFFSET = 3000

        val pageChain = adaptive.splitBefore(4 * SPLIT_OFFSET)

        var pageChainReadable = 0
        var cursor            = pageChain
        while (cursor != null) {
            pageChainReadable += cursor.readableBytes
            cursor = cursor.next
        }
        assert(pageChainReadable == SPLIT_OFFSET * Integer.BYTES)

        assert(adaptive.readerOffset == 4 * SPLIT_OFFSET)

        assert(adaptive.readInt == SPLIT_OFFSET)

        assert(adaptive.readerOffset == 4 * (SPLIT_OFFSET + 1))

    }

    test("set offset") {}

    test("bytesBefore 1") {
        val adaptive = AdaptiveBuffer(allocator)
        for (idx <- 0 until 1024) adaptive.writeByte(0)
        assert(adaptive.writerOffset == 1024)
        adaptive.writeByte('H')
        assert(adaptive.writerOffset == 1025)
        assert(adaptive.bytesBefore('H'.toByte) == 1024)

        for (idx <- 1025 until allocator.fixedCapacity - 1) adaptive.writeByte(0)
        adaptive.writeByte('T')
        assert(adaptive.bytesBefore('T'.toByte) == allocator.fixedCapacity - 1)

        for (idx <- 0 until 1024) adaptive.writeByte(0)
        adaptive.writeByte('P')
        assert(adaptive.bytesBefore('P'.toByte) == allocator.fixedCapacity + 1024)

        for (idx <- 1025 until allocator.fixedCapacity) adaptive.writeByte(0)
        adaptive.writeByte('S')
        assert(adaptive.bytesBefore('S'.toByte) == allocator.fixedCapacity * 2)
        adaptive.readerOffset(allocator.fixedCapacity + 56)
        assert(adaptive.bytesBefore('S'.toByte) == allocator.fixedCapacity - 56)

        val buffer = allocator.allocate()
        buffer.writeByte('A')
        adaptive.extend(buffer)
        assert(adaptive.bytesBefore('A'.toByte) == allocator.fixedCapacity - 56 + 1)
    }

    test("bytesBefore array with length 1") {
        val adaptive = AdaptiveBuffer(allocator)
        for (idx <- 0 until 1024) adaptive.writeByte(0)
        assert(adaptive.writerOffset == 1024)
        adaptive.writeByte('H') // at 1024
        assert(adaptive.writerOffset == 1025)
        assert(adaptive.bytesBefore(Array[Byte]('H'), 0, 1025) == 1024)
        assert(adaptive.bytesBefore(Array[Byte]('H'), 0, 1024) == -1)
        assert(adaptive.bytesBefore(Array[Byte]('h'), 0, 1025) == -1)
        assert(adaptive.bytesBefore(Array[Byte]('h'), 0, 1025, true) == 1024)

        assert(adaptive.bytesBefore(Array[Byte]('H'), 10, 1025) == 1014)
        assert(adaptive.bytesBefore(Array[Byte]('h'), 10, 1025) == -1)
        assert(adaptive.bytesBefore(Array[Byte]('h'), 10, 1025, true) == 1014)

        for (idx <- 1025 until allocator.fixedCapacity - 1) adaptive.writeByte(0)
        adaptive.writeByte('T') // at allocator.fixedCapacity
        assert(adaptive.bytesBefore('T'.toByte) == allocator.fixedCapacity - 1)
        assert(adaptive.bytesBefore(Array[Byte]('T'), 0, allocator.fixedCapacity) == allocator.fixedCapacity - 1)

        for (idx <- 0 until 1024) adaptive.writeByte(0)
        adaptive.writeByte('P') // at allocator.fixedCapacity + 1024
        assert(adaptive.bytesBefore('P'.toByte) == allocator.fixedCapacity + 1024)
        assert(adaptive.bytesBefore(Array[Byte]('P'), 0, adaptive.writerOffset) == allocator.fixedCapacity + 1024)
        assert(adaptive.bytesBefore(Array[Byte]('P'), allocator.fixedCapacity, adaptive.writerOffset) == 1024)

        assert(adaptive.bytesBefore(Array[Byte]('T'), 0, allocator.fixedCapacity) == allocator.fixedCapacity - 1)

        for (idx <- 1025 until allocator.fixedCapacity - 1) adaptive.writeByte(0)
        adaptive.writeByte('S') // at 2 * allocator.fixedCapacity - 1
        adaptive.writeByte('Y') // at 2 * allocator.fixedCapacity

        assert(adaptive.bytesBefore(Array[Byte]('S'), 0, adaptive.writerOffset) == 2 * allocator.fixedCapacity - 1)
        assert(adaptive.bytesBefore(Array[Byte]('S'), bufferSize, adaptive.writerOffset) == allocator.fixedCapacity - 1)
        assert(adaptive.bytesBefore(Array[Byte]('S'), bufferSize - 1, adaptive.writerOffset) == allocator.fixedCapacity)

        assert(adaptive.bytesBefore(Array[Byte]('Y'), 0, adaptive.writerOffset) == 2 * allocator.fixedCapacity)

    }

    test("bytesBefore 2") {
        val adaptive = AdaptiveBuffer(allocator)

        for (idx <- 0 until 1024) adaptive.writeByte(0)

        assert(adaptive.writerOffset == 1024)

        adaptive.writeByte('A')
        adaptive.writeByte('A')

        assert(adaptive.writerOffset == 1026)

        assert(adaptive.bytesBefore('A'.toByte, 'A') == 1024)

        for (idx <- 1026 until allocator.fixedCapacity - 1) adaptive.writeByte(0)

        adaptive.writeByte('B')
        adaptive.writeByte('B')

        assert(adaptive.bytesBefore('B'.toByte, 'B') == allocator.fixedCapacity - 1)

        for (idx <- 1 until 1024) adaptive.writeByte(0)

        adaptive.writeByte('P')
        adaptive.writeByte('P')

        assert(adaptive.bytesBefore('P'.toByte, 'P') == allocator.fixedCapacity + 1024)

        for (idx <- 1026 until allocator.fixedCapacity) adaptive.writeByte(0)

        adaptive.writeByte('S')
        adaptive.writeByte('s')

        assert(adaptive.bytesBefore('S'.toByte, 's') == allocator.fixedCapacity * 2)

        adaptive.readerOffset(allocator.fixedCapacity + 56)

        val len = adaptive.bytesBefore('S'.toByte, 's')

        assert(adaptive.bytesBefore('S'.toByte, 's') == allocator.fixedCapacity - 56)

        val buffer = allocator.allocate()

        buffer.writeByte('C')
        buffer.writeByte('c')

        adaptive.extend(buffer)

        assert(adaptive.bytesBefore('C'.toByte, 'c') == allocator.fixedCapacity - 56 + 2)

    }

    test("bytesBefore 3") {
        val adaptive = AdaptiveBuffer(allocator)

        for (idx <- 0 until 1024) adaptive.writeByte(0)

        assert(adaptive.writerOffset == 1024)

        adaptive.writeByte('A')
        adaptive.writeByte('A')
        adaptive.writeByte('A')

        assert(adaptive.writerOffset == 1027)

        assert(adaptive.bytesBefore('A'.toByte, 'A'.toByte, 'A'.toByte) == 1024)

        for (idx <- 1027 until allocator.fixedCapacity - 1) adaptive.writeByte(0)

        adaptive.writeByte('B')
        adaptive.writeByte('B')
        adaptive.writeByte('B')

        assert(adaptive.bytesBefore('B'.toByte, 'B'.toByte, 'B'.toByte) == allocator.fixedCapacity - 1)

        for (idx <- 2 until 1024) adaptive.writeByte(0)

        adaptive.writeByte('P')
        adaptive.writeByte('P')
        adaptive.writeByte('P')

        assert(adaptive.bytesBefore('P'.toByte, 'P'.toByte, 'P'.toByte) == allocator.fixedCapacity + 1024)

        for (idx <- 1027 until allocator.fixedCapacity) adaptive.writeByte(0)

        adaptive.writeByte('S')
        adaptive.writeByte('s')
        adaptive.writeByte('s')

        assert(adaptive.bytesBefore('S'.toByte, 's'.toByte, 's'.toByte) == allocator.fixedCapacity * 2)

        adaptive.readerOffset(allocator.fixedCapacity + 56)

        val len = adaptive.bytesBefore('S'.toByte, 's'.toByte, 's'.toByte)

        assert(adaptive.bytesBefore('S'.toByte, 's'.toByte, 's'.toByte) == allocator.fixedCapacity - 56)

        val buffer = allocator.allocate()

        buffer.writeByte('C')
        buffer.writeByte('c')
        buffer.writeByte('c')

        adaptive.extend(buffer)

        assert(adaptive.bytesBefore('C'.toByte, 'c'.toByte, 'c'.toByte) == allocator.fixedCapacity - 56 + 3)

    }

    test("adaptive set") {
        val adaptive = AdaptiveBuffer(allocator)
        for (idx <- 0 until 1024) adaptive.writeByte(0)

        adaptive.setMediumLE(0, 137)

        assert(adaptive.getMediumLE(0) == 137)

    }

}
