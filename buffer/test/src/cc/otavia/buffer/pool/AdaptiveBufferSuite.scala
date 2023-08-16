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

import cc.otavia.buffer.pool.{AdaptiveBuffer, HeapPagePooledAllocator, PagePooledAllocator}
import org.scalatest.funsuite.AnyFunSuite

import scala.language.unsafeNulls

class AdaptiveBufferSuite extends AnyFunSuite {

    val allocator: PagePooledAllocator = new HeapPagePooledAllocator()
    val adaptiveBuffer: AdaptiveBuffer = AdaptiveBuffer(allocator)

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

}
