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

import cc.otavia.buffer.pool.{AdaptiveBuffer, HeapPooledPageAllocator}
import org.scalatest.funsuite.AnyFunSuiteLike

import java.math.{BigInteger, BigDecimal as JBigDecimal}
import java.nio.charset.{Charset, StandardCharsets}
import java.time.{Duration as JDuration, *}
import java.util.{Currency, Locale, UUID}
import scala.concurrent.duration.Duration
import scala.jdk.CollectionConverters.*
import scala.language.unsafeNulls
import scala.util.Random

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

    test("long > 100000000") {
        val buffer = allocator.allocate()

        BufferUtils.writeLongAsString(buffer, 1100000001)
        assert(buffer.nextAre("1100000001".getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsLong(buffer) == 1100000001)

        val num = 100000000L * 100000000L + 556677L
        BufferUtils.writeLongAsString(buffer, num)
        assert(buffer.nextAre(num.toString.getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsLong(buffer) == num)

        val num2 = 100000000L * 100000000L - 556677L
        BufferUtils.writeLongAsString(buffer, num2)
        assert(buffer.nextAre(num2.toString.getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsLong(buffer) == num2)

        val num3 = 100000000L + 556677L
        BufferUtils.writeLongAsString(buffer, num3)
        assert(buffer.nextAre(num3.toString.getBytes(StandardCharsets.US_ASCII)))
        assert(BufferUtils.readStringAsLong(buffer) == num3)

    }

    test("float") {
        val buffer = allocator.allocate()

        BufferUtils.writeFloatAsString(buffer, 0.01)
        assert(buffer.skipIfNextAre("0.01".getBytes()))

        BufferUtils.writeFloatAsString(buffer, 0.1345600000015678)
        assert(buffer.skipIfNextAre("0.13456".getBytes()))

        BufferUtils.writeFloatAsString(buffer, 0.00015678)
        assert(buffer.skipIfNextAre("1.5678E-4".getBytes()))

        BufferUtils.writeFloatAsString(buffer, 0.0015678)
        assert(buffer.skipIfNextAre("0.0015678".getBytes()))

        BufferUtils.writeFloatAsString(buffer, 0.000000000015678)
        assert(buffer.skipIfNextAre("1.5678E-11".getBytes()))

        BufferUtils.writeFloatAsString(buffer, -0.000000000015678)
        assert(buffer.skipIfNextAre("-1.5678E-11".getBytes()))

        BufferUtils.writeFloatAsString(buffer, 456789.0000000000000000123)
        assert(buffer.skipIfNextAre("456789.0".getBytes()))
    }

    test("double") {
        val buffer = allocator.allocate()

        BufferUtils.writeDoubleAsString(buffer, 0.01)
        assert(buffer.skipIfNextAre("0.01".getBytes()))

        BufferUtils.writeDoubleAsString(buffer, 0.1345600000015678)
        assert(buffer.skipIfNextAre("0.1345600000015678".getBytes()))

        BufferUtils.writeDoubleAsString(buffer, 0.00015678)
        assert(buffer.skipIfNextAre("1.5678E-4".getBytes()))

        BufferUtils.writeDoubleAsString(buffer, 0.0015678)
        assert(buffer.skipIfNextAre("0.0015678".getBytes()))

        BufferUtils.writeDoubleAsString(buffer, 0.000000000015678)
        assert(buffer.skipIfNextAre("1.5678E-11".getBytes()))

        BufferUtils.writeDoubleAsString(buffer, -0.000000000015678)
        assert(buffer.skipIfNextAre("-1.5678E-11".getBytes()))

        BufferUtils.writeDoubleAsString(buffer, 456789.0000000000000000123)
        assert(buffer.skipIfNextAre("456789.0".getBytes()))
    }

    test("BigInt") {
        val buffer = AdaptiveBuffer(allocator)

        var num = -BigInt(1 + Random.nextInt(10000))

        var i = 0
        while (i < 1000) {
            BufferUtils.writeBigIntAsString(buffer, num)
            assert(buffer.skipIfNextAre(num.toString().getBytes()))
            assert(buffer.readableBytes == 0)
            buffer.compact()
            num = num * BigInt(1 + Random.nextInt(100000))
            i += 1
        }
    }

    test("BigInteger") {
        val buffer = AdaptiveBuffer(allocator)

        var num = BigInteger.valueOf(1 + Random.nextInt(10000))

        var i = 0
        while (i < 1000) {
            BufferUtils.writeBigIntegerAsString(buffer, num)
            assert(buffer.skipIfNextAre(num.toString().getBytes()))
            assert(buffer.readableBytes == 0)
            buffer.compact()
            num = num.multiply(BigInteger.valueOf(1 + Random.nextInt(100000)))
            i += 1
        }
    }

    test("LocalDate") {
        val buffer = AdaptiveBuffer(allocator)

        0 to 10000 foreach { i =>
            val localDate =
                LocalDate.of(Random.nextInt(999999999 * 2) - 999999999, Random.nextInt(12) + 1, Random.nextInt(28) + 1)
            BufferUtils.writeLocalDateAsString(buffer, localDate)
            assert(BufferUtils.readStringAsLocalDate(buffer) == localDate)
            buffer.compact()
            assert(buffer.readableBytes == 0)
        }
    }

    test("LocalTime") {
        val buffer = AdaptiveBuffer(allocator)

        0 to 10000 foreach { i =>
            val localTime =
                LocalTime.of(Random.nextInt(24), Random.nextInt(60), Random.nextInt(60), Random.nextInt(1000000000))
            BufferUtils.writeLocalTimeAsString(buffer, localTime)
            assert(BufferUtils.readStringAsLocalTime(buffer) == localTime)
            buffer.compact()
            assert(buffer.readableBytes == 0)
        }
    }

    test("ZoneOffset") {
        val buffer = allocator.allocate()

        val zoneOffset = ZoneOffset.UTC
        BufferUtils.writeZoneOffset(buffer, zoneOffset)
        assert(BufferUtils.readStringAsZoneOffset(buffer) == zoneOffset)
        buffer.compact()
        assert(buffer.readableBytes == 0)

        0 to 10000 foreach { i =>
            val offset = ZoneOffset.ofHoursMinutesSeconds(Random.nextInt(18), Random.nextInt(60), Random.nextInt(60))
            BufferUtils.writeZoneOffset(buffer, offset)
            assert(BufferUtils.readStringAsZoneOffset(buffer) == offset)
            buffer.compact()
            assert(buffer.readableBytes == 0)
        }

        0 to 10000 foreach { i =>
            val offset = ZoneOffset.ofHoursMinutesSeconds(-Random.nextInt(18), -Random.nextInt(60), -Random.nextInt(60))
            BufferUtils.writeZoneOffset(buffer, offset)
            assert(BufferUtils.readStringAsZoneOffset(buffer) == offset)
            buffer.compact()
            assert(buffer.readableBytes == 0)
        }

        (-18 to 18).foreach { hour =>
            val zoneOffset = ZoneOffset.ofHours(hour)
            BufferUtils.writeZoneOffset(buffer, zoneOffset)
            assert(BufferUtils.readStringAsZoneOffset(buffer) == zoneOffset)
            buffer.compact()
            assert(buffer.readableBytes == 0)
        }

    }

    test("ZoneId") {
        val buffer = allocator.allocate()

        val ids = ZoneId.getAvailableZoneIds.asScala

        ids.foreach { id =>
            val zoneId = ZoneId.of(id)
            BufferUtils.writeZoneIdAsString(buffer, zoneId)
            assert(BufferUtils.readStringAsZoneId(buffer) == zoneId)
            buffer.compact()
            assert(buffer.readableBytes == 0)
        }

    }

    test("ZonedDateTime") {
        val buffer = allocator.allocate()

        val zonedDateTime = ZonedDateTime.now()

        BufferUtils.writeZonedDateTime(buffer, zonedDateTime)
        assert(buffer.skipIfNextAre(zonedDateTime.toString.getBytes()))

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

    test("Period") {
        val buffer = allocator.allocate()

        val zero = Period.of(0, 0, 0)
        BufferUtils.writePeriodAsString(buffer, zero)
        assert(BufferUtils.readStringAsPeriod(buffer) == zero)
        buffer.compact()
        assert(buffer.readableBytes == 0)

        0 to 10000 foreach { _ =>
            val period = Period.of(Random.nextInt(), Random.nextInt(), Random.nextInt())
            BufferUtils.writePeriodAsString(buffer, period)
            assert(BufferUtils.readStringAsPeriod(buffer) == period)
            buffer.compact()
            assert(buffer.readableBytes == 0)
        }

    }

    test("Year") {
        val buffer = allocator.allocate()

        val years =
            Seq(0, 1, 3, 10, 11, 20, 99, 100, 122, 999, 1000, 1002, 4000, 7901, 9999, 10000, 10001, 36781, 3456789)

        for (y <- years ++ years.map(a => -a)) {
            val y1 = Year.of(y)
            BufferUtils.writeYearAsString(buffer, y1)
            assert(BufferUtils.readStringAsYear(buffer) == y1)
            buffer.compact()
            assert(buffer.readableBytes == 0)
        }

    }

    test("MonthDay") {
        val buffer = allocator.allocate()

        val monthDays = Seq(
          MonthDay.of(1, 2),
          MonthDay.of(3, 8),
          MonthDay.of(8, 16),
          MonthDay.of(10, 27),
          MonthDay.of(11, 27),
          MonthDay.of(12, 31)
        )

        for (monthDay <- monthDays) {
            BufferUtils.writeMonthDayAsString(buffer, monthDay)
            assert(BufferUtils.readStringAsMonthDay(buffer) == monthDay)
            buffer.compact()
            assert(buffer.readableBytes == 0)
        }

    }

    test("Instant") {
        val buffer = allocator.allocate()

        val instant = Instant.now()
        BufferUtils.writeInstantAsString(buffer, instant)
        assert(buffer.skipIfNextAre(instant.toString.getBytes()))
        assert(buffer.readableBytes == 0)

    }

    test("YearMonth") {
        val buffer = allocator.allocate()

        val months = 1 to 12

        val yearMonths =
            Seq(0, 1, 3, 10, 11, 20, 99, 100, 122, 999, 1000, 1002, 4000, 7901, 9999, 10000, 10001, 36781, 3456789)
                .flatMap(y => months.map(m => YearMonth.of(y, m)) ++ months.map(m => YearMonth.of(-y, m)))
                .distinct

        for (yearMonth <- yearMonths) {
            BufferUtils.writeYearMonthAsString(buffer, yearMonth)
            assert(BufferUtils.readStringAsYearMonth(buffer) == yearMonth)
            buffer.compact()
            assert(buffer.readableBytes == 0)
        }

    }

}
