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

import cc.otavia.buffer.pool.{AdaptiveBuffer, DirectPooledPageAllocator, HeapPooledPageAllocator}
import org.openjdk.jmh.annotations.{Benchmark, Scope, Setup, State}

import java.nio.charset.StandardCharsets

@State(Scope.Thread)
class DirectAdaptiveBench {

    private var adaptiveBuffer: Buffer = _

    private var HTTP_1_1: Array[Byte] = _

    @Setup
    def init(): Unit = {
        val allocator = new DirectPooledPageAllocator()
        HTTP_1_1 = "HTTP 1.1".getBytes(StandardCharsets.UTF_8)
        adaptiveBuffer = AdaptiveBuffer(allocator)

        for (idx <- 0 until 5024) adaptiveBuffer.writeByte('-')

        adaptiveBuffer.writeByte('H')
        adaptiveBuffer.writeByte('T')
        adaptiveBuffer.writeByte('T')
        adaptiveBuffer.writeByte('P')
        adaptiveBuffer.writeByte(' ')
        adaptiveBuffer.writeByte('1')
        adaptiveBuffer.writeByte('.')
        adaptiveBuffer.writeByte('1')
    }

    @Benchmark
    def bytesBefore2(): Unit = {
        adaptiveBuffer.bytesBefore('H', 'T')
    }

    @Benchmark
    def bytesBefore3(): Unit = {
        adaptiveBuffer.bytesBefore('H'.toByte, 'T'.toByte, 'T'.toByte)
    }

    @Benchmark
    def bytesBefore4(): Unit = {
        adaptiveBuffer.bytesBefore('H'.toByte, 'T'.toByte, 'T'.toByte, 'P'.toByte)
    }

    @Benchmark
    def bytesBeforeArray(): Unit = {
        adaptiveBuffer.bytesBefore(HTTP_1_1)
    }

}
