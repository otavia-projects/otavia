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

package io.otavia.buffer

import org.scalatest.funsuite.AnyFunSuite

class PoolAllocatorSuite extends AnyFunSuite {

    test("buffer initial") {
        val allocator = new PooledSinglePageAllocator()
        val buffer    = allocator.allocate()

        assert(buffer.readerOffset == 0)
        assert(buffer.writerOffset == 0)
        assert(buffer.readableBytes == 0)
        assert(buffer.writableBytes == buffer.capacity)
    }

    test("allocate direct") {
        val allocator = new PooledSinglePageAllocator(true)
        val buffer    = allocator.allocate()

        assert(buffer.isDirect)

    }

    test("allocate heap") {
        val allocator = new PooledSinglePageAllocator(false)

        val buffer = allocator.allocate()

        assert(!buffer.isDirect)
    }

}
