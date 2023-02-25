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

package io.otavia.core.buffer

import io.netty5.buffer.{BufferAllocator, DefaultBufferAllocators}
import org.scalatest.funsuite.AnyFunSuite

import scala.language.unsafeNulls

class AdaptiveBufferSuite extends AnyFunSuite {

    val allocator: BufferAllocator = DefaultBufferAllocators.onHeapAllocator()
    val adaptiveBuffer             = new AdaptiveBuffer(allocator)

    test("set offset") {
        val buffer = adaptiveBuffer
        buffer.ensureWritable(10)
        buffer.writerOffset(10)
        buffer.readerOffset(10)
        assert(buffer.writableBytes() == 0)
        println("")
        assert(true)
    }

}
