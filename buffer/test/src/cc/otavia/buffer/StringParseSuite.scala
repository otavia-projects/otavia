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

import cc.otavia.buffer.pool.{AdaptiveBuffer, HeapPooledPageAllocator, PooledPageAllocator}
import org.scalatest.funsuite.AnyFunSuiteLike

import java.util.UUID
import scala.language.unsafeNulls

class StringParseSuite extends AnyFunSuiteLike {

    val allocator: PooledPageAllocator = new HeapPooledPageAllocator()
    val adaptiveBuffer: AdaptiveBuffer = AdaptiveBuffer(allocator)

    test("buffer uuid") {
        val uuid = UUID.randomUUID()

        val buffer = allocator.allocate()
        BufferUtils.writeUUIDAsString(buffer, uuid)

        buffer.writerOffset(36 + 36)

        BufferUtils.setUUIDAsString(buffer, 36, uuid)

        assert(buffer.getCharSequence(0, 36).toString == uuid.toString)
        assert(buffer.getCharSequence(36, 36).toString == uuid.toString)

        assert(BufferUtils.getStringAsUUID(buffer, 0) == uuid)
        assert(BufferUtils.getStringAsUUID(buffer, 36) == uuid)

        assert(BufferUtils.readStringAsUUID(buffer) == uuid)
        assert(BufferUtils.readStringAsUUID(buffer) == uuid)

    }

    test("adaptive buffer uuid") {
        val uuid   = UUID.randomUUID()
        val buffer = AdaptiveBuffer(allocator)

        BufferUtils.writeUUIDAsString(buffer, uuid)
        buffer.writeCharSequence(" " * 36)
        BufferUtils.setUUIDAsString(buffer, 36, uuid)

        assert(buffer.getCharSequence(0, 36).toString == uuid.toString)
        assert(buffer.getCharSequence(36, 36).toString == uuid.toString)

        assert(BufferUtils.getStringAsUUID(buffer, 0) == uuid)
        assert(BufferUtils.getStringAsUUID(buffer, 36) == uuid)

        assert(BufferUtils.readStringAsUUID(buffer) == uuid)
        assert(BufferUtils.readStringAsUUID(buffer) == uuid)

        buffer.writeCharSequence(" " * (allocator.fixedCapacity - 10))

        BufferUtils.writeUUIDAsString(buffer, uuid)

        buffer.readerOffset(buffer.writerOffset - 36)
        assert(BufferUtils.readStringAsUUID(buffer) == uuid)

    }

}
