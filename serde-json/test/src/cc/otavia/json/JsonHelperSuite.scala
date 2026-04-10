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

package cc.otavia.json

import cc.otavia.buffer.BufferAllocator
import org.scalatest.funsuite.AnyFunSuite

import java.nio.charset.StandardCharsets
import java.time.LocalDate
import scala.language.unsafeNulls

class JsonHelperSuite extends AnyFunSuite {

    private val allocator = BufferAllocator.onHeapAllocator()

    test("LocalDate") {
        val buffer    = allocator.allocate(4096)
        val localDate = LocalDate.of(2024, 7, 1)

        JsonHelper.serializeLocalDate(localDate, buffer)

        assert(buffer.nextAre("\"2024-07-01\"".getBytes()))
    }

    // ==================== deserializeString tests ====================

    test("deserializeString - simple string without escapes") {
        val buffer = allocator.allocate(4096)
        buffer.writeBytes("\"hello world\"".getBytes(StandardCharsets.UTF_8))
        buffer.readerOffset(0)

        val result = JsonHelper.deserializeString(buffer)
        assert(result == "hello world")
    }

    test("deserializeString - empty string") {
        val buffer = allocator.allocate(4096)
        buffer.writeBytes("\"\"".getBytes(StandardCharsets.UTF_8))
        buffer.readerOffset(0)

        val result = JsonHelper.deserializeString(buffer)
        assert(result == "")
    }

    test("deserializeString - string with escape sequences") {
        val buffer = allocator.allocate(4096)
        buffer.writeBytes("\"hello\\nworld\"".getBytes(StandardCharsets.UTF_8))
        buffer.readerOffset(0)

        val result = JsonHelper.deserializeString(buffer)
        assert(result == "hello\nworld")
    }

    test("deserializeString - string with escaped quote") {
        val buffer = allocator.allocate(4096)
        buffer.writeBytes("\"say \\\"hello\\\"\"".getBytes(StandardCharsets.UTF_8))
        buffer.readerOffset(0)

        val result = JsonHelper.deserializeString(buffer)
        assert(result == "say \"hello\"")
    }

    test("deserializeString - string with backslash") {
        val buffer = allocator.allocate(4096)
        buffer.writeBytes("\"path\\\\to\\\\file\"".getBytes(StandardCharsets.UTF_8))
        buffer.readerOffset(0)

        val result = JsonHelper.deserializeString(buffer)
        assert(result == "path\\to\\file")
    }

    test("deserializeString - string with tab escape") {
        val buffer = allocator.allocate(4096)
        buffer.writeBytes("\"col1\\tcol2\"".getBytes(StandardCharsets.UTF_8))
        buffer.readerOffset(0)

        val result = JsonHelper.deserializeString(buffer)
        assert(result == "col1\tcol2")
    }

    test("deserializeString - string with unicode escape") {
        val buffer = allocator.allocate(4096)
        buffer.writeBytes("\"emoji: \\uD83D\\uDE00\"".getBytes(StandardCharsets.UTF_8))
        buffer.readerOffset(0)

        val result = JsonHelper.deserializeString(buffer)
        assert(result == "emoji: \uD83D\uDE00")
    }

    test("deserializeString - UTF-8 string") {
        val buffer = allocator.allocate(4096)
        buffer.writeBytes("\"中文测试\"".getBytes(StandardCharsets.UTF_8))
        buffer.readerOffset(0)

        val result = JsonHelper.deserializeString(buffer)
        assert(result == "中文测试")
    }

    test("deserializeString - mixed escapes and UTF-8") {
        val buffer = allocator.allocate(4096)
        buffer.writeBytes("\"hello\\n世界\\t!\"".getBytes(StandardCharsets.UTF_8))
        buffer.readerOffset(0)

        val result = JsonHelper.deserializeString(buffer)
        assert(result == "hello\n世界\t!")
    }

    // ==================== skipValue tests ====================

    test("skipValue - skip string") {
        val buffer = allocator.allocate(4096)
        buffer.writeBytes("\"hello\"remaining".getBytes(StandardCharsets.UTF_8))
        buffer.readerOffset(0)

        JsonHelper.skipValue(buffer)
        assert(buffer.readerOffset == 7)
        assert(buffer.readByte == 'r')
    }

    test("skipValue - skip number") {
        val buffer = allocator.allocate(4096)
        buffer.writeBytes("123.45remaining".getBytes(StandardCharsets.UTF_8))
        buffer.readerOffset(0)

        JsonHelper.skipValue(buffer)
        assert(buffer.readerOffset == 6)
        assert(buffer.readByte == 'r')
    }

    test("skipValue - skip negative number") {
        val buffer = allocator.allocate(4096)
        buffer.writeBytes("-987.65e+10remaining".getBytes(StandardCharsets.UTF_8))
        buffer.readerOffset(0)

        JsonHelper.skipValue(buffer)
        assert(buffer.readerOffset == 11)
        assert(buffer.readByte == 'r')
    }

    test("skipValue - skip boolean true") {
        val buffer = allocator.allocate(4096)
        buffer.writeBytes("trueremaining".getBytes(StandardCharsets.UTF_8))
        buffer.readerOffset(0)

        JsonHelper.skipValue(buffer)
        assert(buffer.readerOffset == 4)
        assert(buffer.readByte == 'r')
    }

    test("skipValue - skip boolean false") {
        val buffer = allocator.allocate(4096)
        buffer.writeBytes("falseremaining".getBytes(StandardCharsets.UTF_8))
        buffer.readerOffset(0)

        JsonHelper.skipValue(buffer)
        assert(buffer.readerOffset == 5)
        assert(buffer.readByte == 'r')
    }

    test("skipValue - skip null") {
        val buffer = allocator.allocate(4096)
        buffer.writeBytes("nullremaining".getBytes(StandardCharsets.UTF_8))
        buffer.readerOffset(0)

        JsonHelper.skipValue(buffer)
        assert(buffer.readerOffset == 4)
        assert(buffer.readByte == 'r')
    }

    test("skipValue - skip array") {
        val buffer = allocator.allocate(4096)
        buffer.writeBytes("[1,2,3]remaining".getBytes(StandardCharsets.UTF_8))
        buffer.readerOffset(0)

        JsonHelper.skipValue(buffer)
        assert(buffer.readerOffset == 7)
        assert(buffer.readByte == 'r')
    }

    test("skipValue - skip nested array") {
        val buffer = allocator.allocate(4096)
        buffer.writeBytes("[[1,2],[3,4]]remaining".getBytes(StandardCharsets.UTF_8))
        buffer.readerOffset(0)

        JsonHelper.skipValue(buffer)
        assert(buffer.readerOffset == 13)
        assert(buffer.readByte == 'r')
    }

    test("skipValue - skip object") {
        val buffer = allocator.allocate(4096)
        buffer.writeBytes("{\"a\":1}remaining".getBytes(StandardCharsets.UTF_8))
        buffer.readerOffset(0)

        JsonHelper.skipValue(buffer)
        assert(buffer.readerOffset == 7)
        assert(buffer.readByte == 'r')
    }

    test("skipValue - skip nested object") {
        val buffer = allocator.allocate(4096)
        buffer.writeBytes("{\"a\":{\"b\":2}}remaining".getBytes(StandardCharsets.UTF_8))
        buffer.readerOffset(0)

        JsonHelper.skipValue(buffer)
        assert(buffer.readerOffset == 13)
        assert(buffer.readByte == 'r')
    }

    test("skipValue - skip complex nested structure") {
        val buffer = allocator.allocate(4096)
        buffer.writeBytes("{\"arr\":[1,{\"x\":2},null],\"str\":\"value\"}remaining".getBytes(StandardCharsets.UTF_8))
        buffer.readerOffset(0)

        JsonHelper.skipValue(buffer)
        assert(buffer.readByte == 'r')
    }

}
