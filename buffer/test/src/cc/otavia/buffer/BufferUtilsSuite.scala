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

import java.math.{BigInteger, BigDecimal as JBigDecimal}
import java.nio.charset.{Charset, StandardCharsets}
import java.time.{Duration as JDuration, *}
import java.util.{Currency, Locale, UUID}
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

    test("byte") {
        val buffer = allocator.allocate()
        BufferUtils.writeByteAsString(buffer, 0)
        assert(buffer.nextIs('0'))
        assert(BufferUtils.readStringAsByte(buffer) == 0)

        BufferUtils.writeByteAsString(buffer, 5)
        assert(buffer.nextIs('5'))
        assert(BufferUtils.readStringAsByte(buffer) == 5)

        BufferUtils.writeByteAsString(buffer, 10)
        assert(buffer.nextAre("10".getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsByte(buffer) == 10)

        BufferUtils.writeByteAsString(buffer, 88)
        assert(buffer.nextAre("88".getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsByte(buffer) == 88)

        BufferUtils.writeByteAsString(buffer, 100)
        assert(buffer.nextAre("100".getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsByte(buffer) == 100)

        BufferUtils.writeByteAsString(buffer, 110)
        assert(buffer.nextAre("110".getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsByte(buffer) == 110)

    }

    test("boolean") {
        val buffer = allocator.allocate()

        BufferUtils.writeBooleanAsString(buffer, true)
        assert(buffer.nextAre("true".getBytes()))
        assert(BufferUtils.readStringAsBoolean(buffer))

        BufferUtils.writeBooleanAsString(buffer, false)
        assert(buffer.nextAre("false".getBytes()))
        assert(!BufferUtils.readStringAsBoolean(buffer))
    }

    test("short") {
        val buffer = allocator.allocate()

        BufferUtils.writeShortAsString(buffer, 0)
        assert(buffer.nextIs('0'))
        assert(BufferUtils.readStringAsShort(buffer) == 0)

        BufferUtils.writeShortAsString(buffer, 5)
        assert(buffer.nextIs('5'))
        assert(BufferUtils.readStringAsShort(buffer) == 5)

        BufferUtils.writeShortAsString(buffer, 10)
        assert(buffer.nextAre("10".getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsShort(buffer) == 10)

        BufferUtils.writeShortAsString(buffer, 88)
        assert(buffer.nextAre("88".getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsShort(buffer) == 88)

        BufferUtils.writeShortAsString(buffer, 100)
        assert(buffer.nextAre("100".getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsShort(buffer) == 100)

        BufferUtils.writeShortAsString(buffer, 456)
        assert(buffer.nextAre("456".getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsShort(buffer) == 456)

        BufferUtils.writeShortAsString(buffer, 1000)
        assert(buffer.nextAre("1000".getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsShort(buffer) == 1000)

        BufferUtils.writeShortAsString(buffer, 4567)
        assert(buffer.nextAre("4567".getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsShort(buffer) == 4567)

        BufferUtils.writeShortAsString(buffer, 10000)
        assert(buffer.nextAre("10000".getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsShort(buffer) == 10000)

        BufferUtils.writeShortAsString(buffer, 32765)
        assert(buffer.nextAre("32765".getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsShort(buffer) == 32765)

        BufferUtils.writeShortAsString(buffer, -5)
        assert(buffer.nextAre("-5".getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsShort(buffer) == -5)

        BufferUtils.writeShortAsString(buffer, -10)
        assert(buffer.nextAre("-10".getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsShort(buffer) == -10)

        BufferUtils.writeShortAsString(buffer, -88)
        assert(buffer.nextAre("-88".getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsShort(buffer) == -88)

        BufferUtils.writeShortAsString(buffer, -100)
        assert(buffer.nextAre("-100".getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsShort(buffer) == -100)

        BufferUtils.writeShortAsString(buffer, -456)
        assert(buffer.nextAre("-456".getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsShort(buffer) == -456)

        BufferUtils.writeShortAsString(buffer, -1000)
        assert(buffer.nextAre("-1000".getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsShort(buffer) == -1000)

        BufferUtils.writeShortAsString(buffer, -4567)
        assert(buffer.nextAre("-4567".getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsShort(buffer) == -4567)

        BufferUtils.writeShortAsString(buffer, -10000)
        assert(buffer.nextAre("-10000".getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsShort(buffer) == -10000)

        BufferUtils.writeShortAsString(buffer, -32765)
        assert(buffer.nextAre("-32765".getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsShort(buffer) == -32765)

    }

    test("int") {
        val buffer = allocator.allocate()

        BufferUtils.writeIntAsString(buffer, 0)
        assert(buffer.nextIs('0'))
        assert(BufferUtils.readStringAsInt(buffer) == 0)

        BufferUtils.writeIntAsString(buffer, 5)
        assert(buffer.nextIs('5'))
        assert(BufferUtils.readStringAsInt(buffer) == 5)

        BufferUtils.writeIntAsString(buffer, 10)
        assert(buffer.nextAre("10".getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsInt(buffer) == 10)

        BufferUtils.writeIntAsString(buffer, 88)
        assert(buffer.nextAre("88".getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsInt(buffer) == 88)

        BufferUtils.writeIntAsString(buffer, 5566)
        assert(buffer.nextAre("5566".getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsInt(buffer) == 5566)

        BufferUtils.writeIntAsString(buffer, 556677)
        assert(buffer.nextAre("556677".getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsInt(buffer) == 556677)

        BufferUtils.writeIntAsString(buffer, 556677)
        assert(buffer.nextAre("556677".getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsInt(buffer) == 556677)

        BufferUtils.writeIntAsString(buffer, Int.MaxValue)
        assert(buffer.nextAre(Int.MaxValue.toString.getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsInt(buffer) == Int.MaxValue)

        BufferUtils.writeIntAsString(buffer, -5)
        assert(buffer.nextAre("-5".getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsInt(buffer) == -5)

        BufferUtils.writeIntAsString(buffer, -10)
        assert(buffer.nextAre("-10".getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsInt(buffer) == -10)

        BufferUtils.writeIntAsString(buffer, -88)
        assert(buffer.nextAre("-88".getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsInt(buffer) == -88)

        BufferUtils.writeIntAsString(buffer, -5566)
        assert(buffer.nextAre("-5566".getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsInt(buffer) == -5566)

        BufferUtils.writeIntAsString(buffer, -556677)
        assert(buffer.nextAre("-556677".getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsInt(buffer) == -556677)

        BufferUtils.writeIntAsString(buffer, -556677)
        assert(buffer.nextAre("-556677".getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsInt(buffer) == -556677)

        BufferUtils.writeIntAsString(buffer, Int.MinValue)
        assert(buffer.nextAre(Int.MinValue.toString.getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsInt(buffer) == Int.MinValue)
    }

    test("long") {
        val buffer = allocator.allocate()

        BufferUtils.writeLongAsString(buffer, 0)
        assert(buffer.nextIs('0'))
        assert(BufferUtils.readStringAsLong(buffer) == 0)

        BufferUtils.writeLongAsString(buffer, 5)
        assert(buffer.nextIs('5'))
        assert(BufferUtils.readStringAsLong(buffer) == 5)

        BufferUtils.writeLongAsString(buffer, 10)
        assert(buffer.nextAre("10".getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsLong(buffer) == 10)

        BufferUtils.writeLongAsString(buffer, 88)
        assert(buffer.nextAre("88".getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsLong(buffer) == 88)

        BufferUtils.writeLongAsString(buffer, 5566)
        assert(buffer.nextAre("5566".getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsLong(buffer) == 5566)

        BufferUtils.writeLongAsString(buffer, 556677)
        assert(buffer.nextAre("556677".getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsLong(buffer) == 556677)

        BufferUtils.writeLongAsString(buffer, 556677)
        assert(buffer.nextAre("556677".getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsLong(buffer) == 556677)

        BufferUtils.writeLongAsString(buffer, Int.MaxValue)
        assert(buffer.nextAre(Int.MaxValue.toString.getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsLong(buffer) == Int.MaxValue)

        BufferUtils.writeLongAsString(buffer, -5)
        assert(buffer.nextAre("-5".getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsLong(buffer) == -5)

        BufferUtils.writeLongAsString(buffer, -10)
        assert(buffer.nextAre("-10".getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsLong(buffer) == -10)

        BufferUtils.writeLongAsString(buffer, -88)
        assert(buffer.nextAre("-88".getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsLong(buffer) == -88)

        BufferUtils.writeLongAsString(buffer, -5566)
        assert(buffer.nextAre("-5566".getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsLong(buffer) == -5566)

        BufferUtils.writeLongAsString(buffer, -556677)
        assert(buffer.nextAre("-556677".getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsLong(buffer) == -556677)

        BufferUtils.writeLongAsString(buffer, -556677)
        assert(buffer.nextAre("-556677".getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsLong(buffer) == -556677)

        BufferUtils.writeLongAsString(buffer, Int.MinValue)
        assert(buffer.nextAre(Int.MinValue.toString.getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsLong(buffer) == Int.MinValue)
    }

    test("JDuration") {
        val buffer = allocator.allocate()

        val d1 = JDuration.ofHours(100000000)
        BufferUtils.writeJDurationAsString(buffer, d1)
        assert(buffer.skipIfNextAre(d1.toString.getBytes()))
        assert(buffer.readableBytes == 0)

        val d2 = JDuration.ofHours(100000000 + 1)
        BufferUtils.writeJDurationAsString(buffer, d2)
        assert(buffer.skipIfNextAre(d2.toString.getBytes()))
        assert(buffer.readableBytes == 0)

        val localDateTime1 = LocalDateTime.of(2024, 7, 2, 7, 7, 7)
        val localDateTime2 = LocalDateTime.of(2023, 6, 1, 6, 6, 6, 6)

        val d3 = JDuration.between(localDateTime1, localDateTime2)
        BufferUtils.writeJDurationAsString(buffer, d3)
        assert(buffer.skipIfNextAre(d3.toString.getBytes()))
        assert(buffer.readableBytes == 0)

        assert(true)

    }

}
