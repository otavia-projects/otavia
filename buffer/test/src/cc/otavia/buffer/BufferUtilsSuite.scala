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

import cc.otavia.buffer.pool.HeapPooledPageAllocator
import org.scalatest.funsuite.AnyFunSuiteLike

import java.nio.charset.StandardCharsets
import java.util.UUID
import scala.language.unsafeNulls

class BufferUtilsSuite extends AnyFunSuiteLike {

    val allocator = new HeapPooledPageAllocator()

    test("escape string") {
        val buffer = allocator.allocate()

        val str           = "hello \t \\\t \" 你好"
        val serialization = """hello \t \\\t \" 你好"""
        BufferUtils.writeEscapedString(buffer, str)

        assert(buffer.nextAre(serialization.getBytes(StandardCharsets.UTF_8)))
        val str2 = BufferUtils.readEscapedString(buffer, buffer.readableBytes)

        assert(str2 == str)
    }

    test("escape char no quote") {
        val buffer = allocator.allocate()
        val ch     = 1.toChar

        BufferUtils.writeEscapedChar(buffer, ch)
        assert(buffer.skipIfNextAre("""\u0001""".getBytes(StandardCharsets.US_ASCII)))

        BufferUtils.writeEscapedChar(buffer, '\t')
        assert(buffer.skipIfNextAre("""\t""".getBytes(StandardCharsets.US_ASCII)))

        BufferUtils.writeEscapedChar(buffer, 'A')
        assert(buffer.skipIfNextIs('A'))

        BufferUtils.writeEscapedChar(buffer, '好')
        assert(buffer.skipIfNextAre("好".getBytes(StandardCharsets.UTF_8)))

        BufferUtils.writeEscapedChar(buffer, ch)
        assert(BufferUtils.readEscapedChar(buffer) == ch)

        BufferUtils.writeEscapedChar(buffer, '\t')
        assert(BufferUtils.readEscapedChar(buffer) == '\t')

        BufferUtils.writeEscapedChar(buffer, 'A')
        assert(BufferUtils.readEscapedChar(buffer) == 'A')

        BufferUtils.writeEscapedChar(buffer, '好')
        assert(BufferUtils.readEscapedChar(buffer) == '好')

    }

    test("escape char with quote") {
        val buffer = allocator.allocate()
        val ch     = 1.toChar

        BufferUtils.writeEscapedCharWithQuote(buffer, ch)
        assert(buffer.skipIfNextAre(""""\u0001"""".getBytes(StandardCharsets.US_ASCII)))

        BufferUtils.writeEscapedCharWithQuote(buffer, '\t')
        assert(buffer.skipIfNextAre(""""\t"""".getBytes(StandardCharsets.US_ASCII)))

        BufferUtils.writeEscapedCharWithQuote(buffer, 'A')
        assert(buffer.skipIfNextAre(""""A"""".getBytes(StandardCharsets.US_ASCII)))

        BufferUtils.writeEscapedCharWithQuote(buffer, '好')
        assert(buffer.skipIfNextAre("\"好\"".getBytes(StandardCharsets.UTF_8)))

    }

}
