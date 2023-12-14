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

import java.nio.charset.StandardCharsets
import scala.language.unsafeNulls
import scala.util.Try

object BenchAnalysis {

    private val directPooledPageAllocator = new DirectPooledPageAllocator()
    private val heapPooledPageAllocator   = new HeapPooledPageAllocator()

    def main(args: Array[String]): Unit = {
        val command         = args(0)
        val isHeap: Boolean = Try(args(1).trim.toBoolean).toOption.getOrElse(false)
        command match
            case "bytesBefore2" => bytesBefore2(isHeap)
            case "bytesBefore3" => bytesBefore3(isHeap)
            case "bytesBefore4" => bytesBefore4(isHeap)
            case "bytesBefore5" => bytesBefore5(isHeap)
            case "bytesBefore6" => bytesBefore6(isHeap)
            case "bytesBefore7" => bytesBefore7(isHeap)
            case "bytesBefore8" => bytesBefore8(isHeap)
            case "getVsRead"    => getVsRead(isHeap)
    }

    private def bytesBefore2(heap: Boolean = false): Unit = {
        val allocator              = if (heap) heapPooledPageAllocator else directPooledPageAllocator
        var HTTP_1_1: Array[Byte]  = "HTTP 1.1".getBytes(StandardCharsets.UTF_8)
        val adaptiveBuffer: Buffer = AdaptiveBuffer(allocator)

        for (idx <- 0 until 5024) adaptiveBuffer.writeByte('-')

        adaptiveBuffer.writeByte('H')
        adaptiveBuffer.writeByte('T')
        adaptiveBuffer.writeByte('T')
        adaptiveBuffer.writeByte('P')
        adaptiveBuffer.writeByte(' ')
        adaptiveBuffer.writeByte('1')
        adaptiveBuffer.writeByte('.')
        adaptiveBuffer.writeByte('1')

        var i = 0

        while (i < 100_000) { // Warmup
            adaptiveBuffer.bytesBefore('H', 'T')
            i += 1
        }

        i = 0
        val start = System.currentTimeMillis()
        while (i < 10_000_000) {
            adaptiveBuffer.bytesBefore('H', 'T')
            i += 1
        }
        val end = System.currentTimeMillis()

        println(s"[bytesBefore 2, heap = ${heap}] ${end - start}")
    }

    private def bytesBefore3(heap: Boolean = false): Unit = {
        val allocator              = if (heap) heapPooledPageAllocator else directPooledPageAllocator
        var HTTP_1_1: Array[Byte]  = "HTTP 1.1".getBytes(StandardCharsets.UTF_8)
        val adaptiveBuffer: Buffer = AdaptiveBuffer(allocator)

        for (idx <- 0 until 5024) adaptiveBuffer.writeByte('-')

        adaptiveBuffer.writeByte('H')
        adaptiveBuffer.writeByte('T')
        adaptiveBuffer.writeByte('T')
        adaptiveBuffer.writeByte('P')
        adaptiveBuffer.writeByte(' ')
        adaptiveBuffer.writeByte('1')
        adaptiveBuffer.writeByte('.')
        adaptiveBuffer.writeByte('1')

        var i = 0

        while (i < 100_000) { // Warmup
            adaptiveBuffer.bytesBefore('H'.toByte, 'T'.toByte, 'T'.toByte)
            i += 1
        }

        i = 0
        val start = System.currentTimeMillis()
        while (i < 10_000_000) {
            adaptiveBuffer.bytesBefore('H'.toByte, 'T'.toByte, 'T'.toByte)
            i += 1
        }
        val end = System.currentTimeMillis()

        println(s"[bytesBefore 3, heap = ${heap}] ${end - start}")
    }

    private def bytesBefore4(heap: Boolean = false): Unit = {
        val allocator              = if (heap) heapPooledPageAllocator else directPooledPageAllocator
        var HTTP_1_1: Array[Byte]  = "HTTP 1.1".getBytes(StandardCharsets.UTF_8)
        val adaptiveBuffer: Buffer = AdaptiveBuffer(allocator)

        for (idx <- 0 until 5024) adaptiveBuffer.writeByte('-')

        adaptiveBuffer.writeByte('H')
        adaptiveBuffer.writeByte('T')
        adaptiveBuffer.writeByte('T')
        adaptiveBuffer.writeByte('P')
        adaptiveBuffer.writeByte(' ')
        adaptiveBuffer.writeByte('1')
        adaptiveBuffer.writeByte('.')
        adaptiveBuffer.writeByte('1')

        var i = 0

        while (i < 100_000) { // Warmup
            val len = adaptiveBuffer.bytesBefore('H'.toByte, 'T'.toByte, 'T'.toByte, 'P'.toByte)
            i += 1
        }

        i = 0
        val start = System.currentTimeMillis()
        while (i < 10_000_000) {
            adaptiveBuffer.bytesBefore('H'.toByte, 'T'.toByte, 'T'.toByte, 'P'.toByte)
            i += 1
        }
        val end = System.currentTimeMillis()

        println(s"[bytesBefore 4, heap = ${heap}] ${end - start}")
    }

    private def bytesBefore5(heap: Boolean = false): Unit = {
        val allocator              = if (heap) heapPooledPageAllocator else directPooledPageAllocator
        var HTTP_1_1: Array[Byte]  = "HTTP 1.1".getBytes(StandardCharsets.UTF_8)
        val adaptiveBuffer: Buffer = AdaptiveBuffer(allocator)

        for (idx <- 0 until 5024) adaptiveBuffer.writeByte('-')

        adaptiveBuffer.writeByte('H')
        adaptiveBuffer.writeByte('T')
        adaptiveBuffer.writeByte('T')
        adaptiveBuffer.writeByte('P')
        adaptiveBuffer.writeByte(' ')
        adaptiveBuffer.writeByte('1')
        adaptiveBuffer.writeByte('.')
        adaptiveBuffer.writeByte('1')

        var i = 0

        while (i < 100_000) { // Warmup
            adaptiveBuffer.bytesBefore(Array[Byte]('H', 'T', 'T', 'P', ' '))
            i += 1
        }

        i = 0
        val start = System.currentTimeMillis()
        while (i < 10_000_000) {
            adaptiveBuffer.bytesBefore(Array[Byte]('H', 'T', 'T', 'P', ' '))
            i += 1
        }
        val end = System.currentTimeMillis()

        println(s"[bytesBefore 5, heap = ${heap}] ${end - start}")
    }

    private def bytesBefore6(heap: Boolean = false): Unit = {
        val allocator              = if (heap) heapPooledPageAllocator else directPooledPageAllocator
        var HTTP_1_1: Array[Byte]  = "HTTP 1.1".getBytes(StandardCharsets.UTF_8)
        val adaptiveBuffer: Buffer = AdaptiveBuffer(allocator)

        for (idx <- 0 until 5024) adaptiveBuffer.writeByte('-')

        adaptiveBuffer.writeByte('H')
        adaptiveBuffer.writeByte('T')
        adaptiveBuffer.writeByte('T')
        adaptiveBuffer.writeByte('P')
        adaptiveBuffer.writeByte(' ')
        adaptiveBuffer.writeByte('1')
        adaptiveBuffer.writeByte('.')
        adaptiveBuffer.writeByte('1')

        var i = 0

        while (i < 100_000) { // Warmup
            adaptiveBuffer.bytesBefore(Array[Byte]('H', 'T', 'T', 'P', ' ', '1'))
            i += 1
        }

        i = 0
        val start = System.currentTimeMillis()
        while (i < 10_000_000) {
            adaptiveBuffer.bytesBefore(Array[Byte]('H', 'T', 'T', 'P', ' ', '1'))
            i += 1
        }
        val end = System.currentTimeMillis()

        println(s"[bytesBefore 6, heap = $heap] ${end - start}")
    }

    private def bytesBefore7(heap: Boolean = false): Unit = {
        val allocator              = if (heap) heapPooledPageAllocator else directPooledPageAllocator
        var HTTP_1_1: Array[Byte]  = "HTTP 1.1".getBytes(StandardCharsets.UTF_8)
        val adaptiveBuffer: Buffer = AdaptiveBuffer(allocator)

        for (idx <- 0 until 5024) adaptiveBuffer.writeByte('-')

        adaptiveBuffer.writeByte('H')
        adaptiveBuffer.writeByte('T')
        adaptiveBuffer.writeByte('T')
        adaptiveBuffer.writeByte('P')
        adaptiveBuffer.writeByte(' ')
        adaptiveBuffer.writeByte('1')
        adaptiveBuffer.writeByte('.')
        adaptiveBuffer.writeByte('1')

        var i = 0

        while (i < 100_000) { // Warmup
            adaptiveBuffer.bytesBefore(Array[Byte]('H', 'T', 'T', 'P', ' ', '1', '.'))
            i += 1
        }

        i = 0
        val start = System.currentTimeMillis()
        while (i < 10_000_000) {
            adaptiveBuffer.bytesBefore(Array[Byte]('H', 'T', 'T', 'P', ' ', '1', '.'))
            i += 1
        }
        val end = System.currentTimeMillis()

        println(s"[bytesBefore 7, heap = $heap] ${end - start}")
    }

    private def bytesBefore8(heap: Boolean = false): Unit = {
        val allocator              = if (heap) heapPooledPageAllocator else directPooledPageAllocator
        var HTTP_1_1: Array[Byte]  = "HTTP 1.1".getBytes(StandardCharsets.UTF_8)
        val adaptiveBuffer: Buffer = AdaptiveBuffer(allocator)

        for (idx <- 0 until 5024) adaptiveBuffer.writeByte('-')

        adaptiveBuffer.writeByte('H')
        adaptiveBuffer.writeByte('T')
        adaptiveBuffer.writeByte('T')
        adaptiveBuffer.writeByte('P')
        adaptiveBuffer.writeByte(' ')
        adaptiveBuffer.writeByte('1')
        adaptiveBuffer.writeByte('.')
        adaptiveBuffer.writeByte('1')

        var i = 0

        while (i < 100_000) { // Warmup
            adaptiveBuffer.bytesBefore(Array[Byte]('H', 'T', 'T', 'P', ' ', '1', '.', '1'))
            i += 1
        }

        i = 0
        val start = System.currentTimeMillis()
        while (i < 10_000_000) {
            adaptiveBuffer.bytesBefore(Array[Byte]('H', 'T', 'T', 'P', ' ', '1', '.', '1'))
            i += 1
        }
        val end = System.currentTimeMillis()

        println(s"[bytesBefore 8, heap = ${heap}] ${end - start}")
    }

    private def getVsRead(heap: Boolean): Unit = {
        val allocator              = if (heap) heapPooledPageAllocator else directPooledPageAllocator
        val adaptiveBuffer: Buffer = AdaptiveBuffer(allocator)

        var total = 0

        for (idx <- 0 until 5024) adaptiveBuffer.writeByte('-')

        val start = System.currentTimeMillis()
        for (idx <- 0 until 1_000_000) {
            var i = 0
            while (i < 1000) {
                total += adaptiveBuffer.readInt
                i += 1
            }
            adaptiveBuffer.readerOffset(0)
        }
        val end = System.currentTimeMillis()
        println(end - start)

        val start2 = System.currentTimeMillis()
        for (idx <- 0 until 1_000_000) {
            var i = 0
            while (i < 1000) {
                val step = i * 4
                total += adaptiveBuffer.getInt(step)
                i += 1
            }
            adaptiveBuffer.readerOffset(0)
        }
        val end2 = System.currentTimeMillis()

        println(end2 - start2)

    }

}
