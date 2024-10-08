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

import cc.otavia.buffer.{Buffer, BufferUtils}
import cc.otavia.util.ASCII

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.time.{Duration as JDuration, *}
import java.util.UUID
import scala.concurrent.duration.Duration
import scala.language.unsafeNulls

private[json] object JsonHelper {

    final def skipBlanks(in: Buffer): Unit = while (in.skipIfNextIn(JsonConstants.TOKEN_BLANKS)) {}

    final def isNextToken(in: Buffer, token: Byte): Boolean = {
        skipBlanks(in)
        in.skipIfNextIs(token)
    }

    final def isNextToken(in: Buffer, token: Array[Byte]): Boolean = {
        skipBlanks(in)
        in.skipIfNextAre(token)
    }

    final def serializeObjectStart(out: Buffer): Unit = out.writeByte(ASCII.BRACE_LEFT)

    // TODO: replace to exceptXXX, if false throws error
    final def skipObjectStart(in: Buffer): Boolean = {
        skipBlanks(in)
        in.skipIfNextIs(ASCII.BRACE_LEFT)
    }

    final def serializeArrayStart(out: Buffer): Unit = out.writeByte(ASCII.BRACKET_LEFT)

    final def skipArrayStart(in: Buffer): Boolean = {
        skipBlanks(in)
        in.skipIfNextIs(ASCII.BRACKET_LEFT)
    }

    final def serializeObjectEnd(out: Buffer): Unit = out.writeByte(ASCII.BRACE_RIGHT)

    final def skipObjectEnd(in: Buffer): Boolean = {
        skipBlanks(in)
        in.skipIfNextIs(ASCII.BRACE_RIGHT)
    }

    final def serializeArrayEnd(out: Buffer): Unit = out.writeByte(ASCII.BRACKET_RIGHT)

    final def skipArrayEnd(in: Buffer): Boolean = {
        skipBlanks(in)
        in.skipIfNextIs(ASCII.BRACKET_RIGHT)
    }

    final def serializeKey(key: String, out: Buffer): Unit = {
        out.writeByte('\"')
        BufferUtils.writeEscapedString(out, key)
        out.writeByte('\"')
        out.writeByte(ASCII.COLON)
    }

    final def serializeKey(key: Array[Byte], out: Buffer): Unit = {
        out.writeByte('\"')
        out.writeBytes(key)
        out.writeByte('\"')
        out.writeByte(ASCII.COLON)
    }

    final def serializeNull(out: Buffer): Unit = out.writeBytes(JsonConstants.TOKEN_NULL)

    final def serializeByte(byte: Byte, out: Buffer): Unit = BufferUtils.writeByteAsString(out, byte)

    final def serializeBoolean(boolean: Boolean, out: Buffer): Unit = BufferUtils.writeBooleanAsString(out, boolean)

    final def serializeChar(char: Char, out: Buffer): Unit = {
        out.writeByte('\"')
        BufferUtils.writeEscapedChar(out, char)
        out.writeByte('\"')
    }

    final def serializeShort(short: Short, out: Buffer): Unit = BufferUtils.writeShortAsString(out, short)

    final def serializeInt(int: Int, out: Buffer): Unit = BufferUtils.writeIntAsString(out, int)

    final def serializeLong(long: Long, out: Buffer): Unit = BufferUtils.writeLongAsString(out, long)

    final def serializeFloat(float: Float, out: Buffer): Unit = BufferUtils.writeFloatAsString(out, float)

    final def serializeDouble(double: Double, out: Buffer): Unit = BufferUtils.writeDoubleAsString(out, double)

    final def serializeString(string: String, out: Buffer): Unit = {
        out.writeByte('\"')
        BufferUtils.writeEscapedString(out, string)
        out.writeByte('\"')
    }

    final def deserializeByte(in: Buffer): Byte = {
        skipBlanks(in)
        BufferUtils.readStringAsByte(in)
    }

    final def deserializeBoolean(in: Buffer): Boolean = {
        skipBlanks(in)
        BufferUtils.readStringAsBoolean(in)
    }

    final def deserializeChar(in: Buffer): Char = {
        skipBlanks(in)
        assert(in.skipIfNextIs('\"'), s"except \" but get ${in.readByte}")
        val b = BufferUtils.readEscapedChar(in)
        assert(in.skipIfNextIs('\"'), s"except \" but get ${in.readByte}")
        b
    }

    final def deserializeShort(in: Buffer): Short = BufferUtils.readStringAsShort(in)

    final def deserializeInt(in: Buffer): Int = BufferUtils.readStringAsInt(in)

    final def deserializeLong(in: Buffer): Long = {
        skipBlanks(in)
        BufferUtils.readStringAsLong(in)
    }

    final def deserializeFloat(in: Buffer): Float = {
        skipBlanks(in)
        BufferUtils.readStringAsFloat(in)
    }

    final def deserializeDouble(in: Buffer): Double = {
        skipBlanks(in)
        BufferUtils.readStringAsDouble(in)
    }

    final def deserializeString(in: Buffer): String = {
        skipBlanks(in)
        assert(in.skipIfNextIs('\"'), s"except \" but get ${in.readByte}")
        val len = in.bytesBefore('\"'.toByte) // TODO: escape
        val str = in.readCharSequence(len, StandardCharsets.UTF_8).toString
        in.readByte
        str
    }

    // math type

    final def serializeBigInt(bigInt: BigInt, out: Buffer): Unit = BufferUtils.writeBigIntAsString(out, bigInt)

    final def serializeBigDecimal(bigDecimal: BigDecimal, out: Buffer): Unit = ???

    final def serializeBigInteger(bigInteger: BigInteger, out: Buffer): Unit =
        BufferUtils.writeBigIntegerAsString(out, bigInteger)

    final def serializeJBigDecimal(bigDecimal: java.math.BigDecimal, out: Buffer): Unit = ???

    final def deserializeBigInt(in: Buffer): BigInt = ???

    final def deserializeBigDecimal(in: Buffer): BigDecimal = ???

    final def deserializeBigInteger(in: Buffer): BigInteger = ???

    final def deserializeJBigDecimal(in: Buffer): java.math.BigDecimal = ???

    final def serializeJDuration(duration: JDuration, out: Buffer): Unit = {
        out.writeByte('\"')
        BufferUtils.writeJDurationAsString(out, duration)
        out.writeByte('\"')
    }

    final def serializeDuration(duration: Duration, out: Buffer): Unit = {
        out.writeByte('\"')
        BufferUtils.writeDurationAsString(out, duration)
        out.writeByte('\"')
    }

    final def serializeInstant(instant: Instant, out: Buffer): Unit = {
        out.writeByte('\"')
        BufferUtils.writeInstantAsString(out, instant)
        out.writeByte('\"')
    }

    final def serializeLocalDate(localDate: LocalDate, out: Buffer): Unit = {
        out.writeByte('\"')
        BufferUtils.writeLocalDateAsString(out, localDate)
        out.writeByte('\"')
    }

    final def serializeLocalDateTime(localDateTime: LocalDateTime, out: Buffer): Unit = {
        out.writeByte('\"')
        BufferUtils.writeLocalDateTimeAsString(out, localDateTime)
        out.writeByte('\"')
    }

    final def serializeLocalTime(localTime: LocalTime, out: Buffer): Unit = {
        out.writeByte('\"')
        BufferUtils.writeLocalTimeAsString(out, localTime)
        out.writeByte('\"')
    }

    final def serializeMonthDay(monthDay: MonthDay, out: Buffer): Unit = {
        out.writeByte('\"')
        BufferUtils.writeMonthDayAsString(out, monthDay)
        out.writeByte('\"')
    }

    final def serializeOffsetDateTime(offsetDateTime: OffsetDateTime, out: Buffer): Unit = {
        out.writeByte('\"')
        BufferUtils.writeOffsetDateTimeAsString(out, offsetDateTime)
        out.writeByte('\"')
    }

    final def serializeOffsetTime(offsetTime: OffsetTime, out: Buffer): Unit = {
        out.writeByte('\"')
        BufferUtils.writeOffsetTimeAsString(out, offsetTime)
        out.writeByte('\"')
    }

    final def serializePeriod(period: Period, out: Buffer): Unit = {
        out.writeByte('\"')
        BufferUtils.writePeriodAsString(out, period)
        out.writeByte('\"')
    }

    final def serializeYear(year: Year, out: Buffer): Unit = {
        out.writeByte('\"')
        BufferUtils.writeYearAsString(out, year)
        out.writeByte('\"')
    }

    final def serializeYearMonth(yearMonth: YearMonth, out: Buffer): Unit = {
        out.writeByte('\"')
        BufferUtils.writeYearMonthAsString(out, yearMonth)
        out.writeByte('\"')
    }

    final def serializeZonedDateTime(zonedDateTime: ZonedDateTime, out: Buffer): Unit = {
        out.writeByte('\"')
        BufferUtils.writeZonedDateTime(out, zonedDateTime)
        out.writeByte('\"')
    }

    final def serializeZoneId(zoneId: ZoneId, out: Buffer): Unit = {
        out.writeByte('\"')
        BufferUtils.writeZoneIdAsString(out, zoneId)
        out.writeByte('\"')
    }

    final def serializeZoneOffset(zoneOffset: ZoneOffset, out: Buffer): Unit = {
        out.writeByte('\"')
        BufferUtils.writeZoneOffset(out, zoneOffset)
        out.writeByte('\"')
    }

    /** Reads a JSON string value into a [[java.time.Duration]] instance.
     *  @param in
     *    The [[Buffer]] to read.
     *  @return
     *    a [[java.time.Duration]] instance of the parsed JSON value.
     */
    final def deserializeJDuration(in: Buffer): JDuration = {
        assert(in.skipIfNextIs('\"'), s"except \" but get ${in.readByte.toChar}")
        val duration = BufferUtils.readStringAsJDuration(in)
        assert(in.skipIfNextIs('\"'), s"except \" but get ${in.readByte.toChar}")
        duration
    }

    /** Reads a JSON string value into a [[Duration]] instance.
     *
     *  @param in
     *    The [[Buffer]] to read.
     *  @return
     *    a [[Duration]] instance of the parsed JSON value.
     */
    final def deserializeDuration(in: Buffer): Duration = {
        assert(in.skipIfNextIs('\"'), s"except \" but get ${in.readByte.toChar}")
        val duration = BufferUtils.readStringAsDuration(in)
        assert(in.skipIfNextIs('\"'), s"except \" but get ${in.readByte.toChar}")
        duration
    }

    final def deserializeInstant(in: Buffer): Instant = {
        assert(in.skipIfNextIs('\"'), s"except \" but get ${in.readByte.toChar}")
        val instant = BufferUtils.readStringAsInstant(in)
        assert(in.skipIfNextIs('\"'), s"except \" but get ${in.readByte.toChar}")
        instant
    }

    final def deserializeLocalDate(in: Buffer): LocalDate = {
        assert(in.skipIfNextIs('\"'), s"except \" but get ${in.readByte.toChar}")
        val localDate = BufferUtils.readStringAsLocalDate(in)
        assert(in.skipIfNextIs('\"'), s"except \" but get ${in.readByte.toChar}")
        localDate
    }

    final def deserializeLocalDateTime(in: Buffer): LocalDateTime = {
        assert(in.skipIfNextIs('\"'), s"except \" but get ${in.readByte.toChar}")
        val localDateTime = BufferUtils.readStringAsLocalDateTime(in)
        assert(in.skipIfNextIs('\"'), s"except \" but get ${in.readByte.toChar}")
        localDateTime
    }

    final def deserializeLocalTime(in: Buffer): LocalTime = {
        assert(in.skipIfNextIs('\"'), s"except \" but get ${in.readByte.toChar}")
        val localTime = BufferUtils.readStringAsLocalTime(in)
        assert(in.skipIfNextIs('\"'), s"except \" but get ${in.readByte.toChar}")
        localTime
    }

    final def deserializeMonthDay(in: Buffer): MonthDay = {
        assert(in.skipIfNextIs('\"'), s"except \" but get ${in.readByte.toChar}")
        val monthDay = BufferUtils.readStringAsMonthDay(in)
        assert(in.skipIfNextIs('\"'), s"except \" but get ${in.readByte.toChar}")
        monthDay
    }

    final def deserializeOffsetDateTime(in: Buffer): OffsetDateTime = {
        assert(in.skipIfNextIs('\"'), s"except \" but get ${in.readByte.toChar}")
        val offsetDateTime = BufferUtils.readStringAsOffsetDateTime(in)
        assert(in.skipIfNextIs('\"'), s"except \" but get ${in.readByte.toChar}")
        offsetDateTime
    }

    final def deserializeOffsetTime(in: Buffer): OffsetTime = {
        assert(in.skipIfNextIs('\"'), s"except \" but get ${in.readByte.toChar}")
        val offsetTime = BufferUtils.readStringAsOffsetTime(in)
        assert(in.skipIfNextIs('\"'), s"except \" but get ${in.readByte.toChar}")
        offsetTime
    }

    final def deserializePeriod(in: Buffer): Period = {
        assert(in.skipIfNextIs('\"'), s"except \" but get ${in.readByte.toChar}")
        val period = BufferUtils.readStringAsPeriod(in)
        assert(in.skipIfNextIs('\"'), s"except \" but get ${in.readByte.toChar}")
        period
    }

    final def deserializeYear(in: Buffer): Year = {
        assert(in.skipIfNextIs('\"'), s"except \" but get ${in.readByte.toChar}")
        val year = BufferUtils.readStringAsYear(in)
        assert(in.skipIfNextIs('\"'), s"except \" but get ${in.readByte.toChar}")
        year
    }

    final def deserializeYearMonth(in: Buffer): YearMonth = {
        assert(in.skipIfNextIs('\"'), s"except \" but get ${in.readByte.toChar}")
        val yearMonth = BufferUtils.readStringAsYearMonth(in)
        assert(in.skipIfNextIs('\"'), s"except \" but get ${in.readByte.toChar}")
        yearMonth
    }

    final def deserializeZonedDateTime(in: Buffer): ZonedDateTime = {
        assert(in.skipIfNextIs('\"'), s"except \" but get ${in.readByte.toChar}")
        val zonedDateTime = BufferUtils.readStringAsZonedDateTime(in)
        assert(in.skipIfNextIs('\"'), s"except \" but get ${in.readByte.toChar}")
        zonedDateTime
    }

    final def deserializeZoneId(in: Buffer): ZoneId = {
        assert(in.skipIfNextIs('\"'), s"except \" but get ${in.readByte.toChar}")
        val zoneId = BufferUtils.readStringAsZoneId(in)
        assert(in.skipIfNextIs('\"'), s"except \" but get ${in.readByte.toChar}")
        zoneId
    }

    final def deserializeZoneOffset(in: Buffer): ZoneOffset = {
        assert(in.skipIfNextIs('\"'), s"except \" but get ${in.readByte.toChar}")
        val offset = BufferUtils.readStringAsZoneOffset(in)
        assert(in.skipIfNextIs('\"'), s"except \" but get ${in.readByte.toChar}")
        offset
    }

    final def serializeUUID(uuid: UUID, out: Buffer): Unit = {
        out.writeByte('\"')
        BufferUtils.writeUUIDAsString(out, uuid)
        out.writeByte('\"')
    }

    final def deserializeUUID(in: Buffer): UUID = {
        assert(in.skipIfNextIs('\"'), s"except \" but get ${in.readByte.toChar}")
        val uuid = BufferUtils.readStringAsUUID(in)
        assert(in.skipIfNextIs('\"'), s"except \" but get ${in.readByte.toChar}")
        uuid
    }

}
