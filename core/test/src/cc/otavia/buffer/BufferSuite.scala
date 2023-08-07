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
import scala.language.unsafeNulls

class BufferSuite extends AnyFunSuite {

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

}
