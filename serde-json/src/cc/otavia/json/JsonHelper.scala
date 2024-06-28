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
import cc.otavia.datatype.Money
import cc.otavia.json.types.*
import cc.otavia.serde.{Serde, SerdeOps}

import java.math.{BigInteger, BigDecimal as JBigDecimal}
import java.nio.charset.{Charset, StandardCharsets}
import java.time.{Duration as JDuration, *}
import java.util.{Currency, Locale, UUID}
import scala.collection.mutable
import scala.compiletime.*
import scala.concurrent.duration.Duration
import scala.deriving.Mirror
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

    final def serializeObjectStart(out: Buffer): Unit = out.writeByte(JsonConstants.TOKEN_OBJECT_START)

    // TODO: replace to exceptXXX, if false throws error
    final def skipObjectStart(in: Buffer): Boolean = {
        skipBlanks(in)
        in.skipIfNextIs(JsonConstants.TOKEN_OBJECT_START)
    }

    final def serializeArrayStart(out: Buffer): Unit = out.writeByte(JsonConstants.TOKEN_ARRAY_START)

    final def skipArrayStart(in: Buffer): Boolean = {
        skipBlanks(in)
        in.skipIfNextIs(JsonConstants.TOKEN_ARRAY_START)
    }

    final def serializeObjectEnd(out: Buffer): Unit = out.writeByte(JsonConstants.TOKEN_OBJECT_END)

    final def skipObjectEnd(in: Buffer): Boolean = {
        skipBlanks(in)
        in.skipIfNextIs(JsonConstants.TOKEN_OBJECT_END)
    }

    final def serializeArrayEnd(out: Buffer): Unit = out.writeByte(JsonConstants.TOKEN_ARRAY_END)

    final def skipArrayEnd(in: Buffer): Boolean = {
        skipBlanks(in)
        in.skipIfNextIs(JsonConstants.TOKEN_ARRAY_END)
    }

    final def serializeKey(key: String, out: Buffer): Unit = {
        out.writeByte(JsonConstants.TOKEN_DOUBLE_QUOTE)
        out.writeCharSequence(key)
        out.writeByte(JsonConstants.TOKEN_DOUBLE_QUOTE)
        out.writeByte(JsonConstants.TOKEN_COLON)
    }

    final def serializeKey(key: Array[Byte], out: Buffer): Unit = {
        out.writeByte(JsonConstants.TOKEN_DOUBLE_QUOTE)
        out.writeBytes(key)
        out.writeByte(JsonConstants.TOKEN_DOUBLE_QUOTE)
        out.writeByte(JsonConstants.TOKEN_COLON)
    }

    final def serializeNull(out: Buffer): Unit = out.writeBytes(JsonConstants.TOKEN_NULL)

    final def serializeByte(byte: Byte, out: Buffer): Unit = serializeInt(byte, out)

    final def serializeBoolean(boolean: Boolean, out: Buffer): Unit = if (boolean)
        out.writeBytes(JsonConstants.TOKEN_TURE)
    else
        out.writeBytes(JsonConstants.TOKEN_FALSE)

    final def serializeChar(char: Char, out: Buffer): Unit = {
        out.writeByte(JsonConstants.TOKEN_DOUBLE_QUOTE)
        out.writeByte(char.toByte)
        out.writeByte(JsonConstants.TOKEN_DOUBLE_QUOTE)
    }

    final def serializeShort(short: Short, out: Buffer): Unit = BufferUtils.writeShortAsString(out, short)

    final def serializeInt(int: Int, out: Buffer): Unit = BufferUtils.writeIntAsString(out, int)

    final def serializeLong(long: Long, out: Buffer): Unit = out.writeCharSequence(long.toString)

    final def serializeFloat(float: Float, out: Buffer): Unit = out.writeCharSequence(float.toString)

    final def serializeDouble(double: Double, out: Buffer): Unit = out.writeCharSequence(double.toString)

    final def serializeString(string: String, out: Buffer): Unit = {
        out.writeByte(JsonConstants.TOKEN_DOUBLE_QUOTE)
        out.writeCharSequence(string, StandardCharsets.UTF_8) // TODO: escape char
        out.writeByte(JsonConstants.TOKEN_DOUBLE_QUOTE)
    }

    final def deserializeByte(in: Buffer): Byte = deserializeInt(in).toByte

    final def deserializeBoolean(in: Buffer): Boolean = {
        skipBlanks(in)
        if (in.skipIfNextAre(JsonConstants.TOKEN_TURE)) true
        else if (in.skipIfNextAre(JsonConstants.TOKEN_FALSE)) false
        else throw new JsonFormatException()
    }

    final def deserializeChar(in: Buffer): Char = {
        skipBlanks(in)
        assert(in.skipIfNextIs(JsonConstants.TOKEN_DOUBLE_QUOTE), s"except \" but get ${in.readByte}")
        val b = in.readByte
        assert(in.skipIfNextIs(JsonConstants.TOKEN_DOUBLE_QUOTE), s"except \" but get ${in.readByte}")
        b.toChar
    }

    final def deserializeShort(in: Buffer): Short = BufferUtils.readStringAsShort(in)

    final def deserializeInt(in: Buffer): Int = BufferUtils.readStringAsInt(in)

    final def deserializeLong(in: Buffer): Long = {
        skipBlanks(in)
        in.skipIfNextIs(JsonConstants.TOKEN_PLUS)
        val minus     = in.skipIfNextIs(JsonConstants.TOKEN_MINUS)
        var ret: Long = 0
        while (in.readableBytes > 0 && in.nextIn(JsonConstants.TOKEN_NUMBERS)) {
            val b = in.readByte
            ret = ret * 10L + (b - JsonConstants.TOKEN_ZERO)
        }
        if (minus) -ret else ret
    }

    final def deserializeFloat(in: Buffer): Float = {
        skipBlanks(in)
        in.skipIfNextIs(JsonConstants.TOKEN_PLUS)
        val minus               = in.skipIfNextIs(JsonConstants.TOKEN_MINUS)
        var intPart: Float      = 0
        var floatPart: Float    = 0f
        var startFloat: Boolean = false
        var floatIdx: Float     = 0
        while (in.readableBytes > 0 && in.nextIn(JsonConstants.TOKEN_FLOATS)) {
            val b = in.readByte
            if (b == JsonConstants.TOKEN_POINT) startFloat = true
            else {
                if (!startFloat) intPart = intPart * 10f + (b - JsonConstants.TOKEN_ZERO)
                else {
                    floatIdx += 1
                    floatPart = floatPart + ((b - JsonConstants.TOKEN_ZERO).toFloat / Math.pow(10f, floatIdx).toFloat)
                }
            }
        }
        if (minus) -(intPart + floatPart) else intPart + floatPart
    }

    final def deserializeDouble(in: Buffer): Double = {
        skipBlanks(in)
        in.skipIfNextIs(JsonConstants.TOKEN_PLUS)
        val minus               = in.skipIfNextIs(JsonConstants.TOKEN_MINUS)
        var intPart: Double     = 0d
        var floatPart: Double   = 0d
        var startFloat: Boolean = false
        var floatIdx: Double    = 0d
        while (in.readableBytes > 0 && in.nextIn(JsonConstants.TOKEN_FLOATS)) {
            val b = in.readByte
            if (b == JsonConstants.TOKEN_POINT) startFloat = true
            else {
                if (!startFloat) intPart = intPart * 10d + (b - JsonConstants.TOKEN_ZERO)
                else {
                    floatIdx += 1
                    floatPart = floatPart + ((b - JsonConstants.TOKEN_ZERO).toFloat / Math.pow(10d, floatIdx))
                }
            }
        }
        if (minus) -(intPart + floatPart) else intPart + floatPart
    }

    final def deserializeString(in: Buffer): String = {
        skipBlanks(in)
        assert(in.skipIfNextIs(JsonConstants.TOKEN_DOUBLE_QUOTE), s"except \" but get ${in.readByte}")
        val len = in.bytesBefore(JsonConstants.TOKEN_DOUBLE_QUOTE) // TODO: escape
        val str = in.readCharSequence(len, StandardCharsets.UTF_8).toString
        in.readByte
        str
    }

    // math type

    final def serializeBigInt(bigInt: BigInt, out: Buffer): Unit = ???

    final def serializeBigDecimal(bigDecimal: BigDecimal, out: Buffer): Unit = ???

    final def serializeBigInteger(bigInteger: BigInteger, out: Buffer): Unit = ???

    final def serializeJBigDecimal(bigDecimal: java.math.BigDecimal, out: Buffer): Unit = ???

    final def deserializeBigInt(in: Buffer): BigInt = ???

    final def deserializeBigDecimal(in: Buffer): BigDecimal = ???

    final def deserializeBigInteger(in: Buffer): BigInteger = ???

    final def deserializeJBigDecimal(in: Buffer): java.math.BigDecimal = ???

    final def serializeJDuration(duration: JDuration, out: Buffer): Unit = ???

    final def serializeDuration(duration: Duration, out: Buffer): Unit = ???

    final def serializeInstant(instant: Instant, out: Buffer): Unit = ???

    final def serializeLocalDate(localDate: LocalDate, out: Buffer): Unit = ???

    final def serializeLocalDateTime(localDateTime: LocalDateTime, out: Buffer): Unit = ???

    final def serializeLocalTime(localTime: LocalTime, out: Buffer): Unit = ???

    final def serializeMonthDay(monthDay: MonthDay, out: Buffer): Unit = ???

    final def serializeOffsetDateTime(offsetDateTime: OffsetDateTime, out: Buffer): Unit = ???

    final def serializeOffsetTime(offsetTime: OffsetTime, out: Buffer): Unit = ???

    final def serializePeriod(period: Period, out: Buffer): Unit = ???

    final def serializeYear(year: Year, out: Buffer): Unit = ???

    final def serializeYearMonth(yearMonth: YearMonth, out: Buffer): Unit = ???

    final def serializeZonedDateTime(zonedDateTime: ZonedDateTime, out: Buffer): Unit = ???

    final def serializeZoneId(zoneId: ZoneId, out: Buffer): Unit = ???

    final def serializeZoneOffset(zoneOffset: ZoneOffset, out: Buffer): Unit = ???

    final def deserializeJDuration(in: Buffer): JDuration = ???

    final def deserializeDuration(in: Buffer): Duration = ???

    final def deserializeInstant(in: Buffer): Instant = ???

    final def deserializeLocalDate(in: Buffer): LocalDate = ???

    final def deserializeLocalDateTime(in: Buffer): LocalDateTime = ???

    final def deserializeLocalTime(in: Buffer): LocalTime = ???

    final def deserializeMonthDay(in: Buffer): MonthDay = ???

    final def deserializeOffsetDateTime(in: Buffer): OffsetDateTime = ???

    final def deserializeOffsetTime(in: Buffer): OffsetTime = ???

    final def deserializePeriod(in: Buffer): Period = ???

    final def deserializeYear(in: Buffer): Year = ???

    final def deserializeYearMonth(in: Buffer): YearMonth = ???

    final def deserializeZonedDateTime(in: Buffer): ZonedDateTime = ???

    final def deserializeZoneId(in: Buffer): ZoneId = ???

    final def deserializeZoneOffset(in: Buffer): ZoneOffset = ???

    final def serializeUUID(uuid: UUID, out: Buffer): Unit = ???

    final def serializeLocale(locale: Locale, out: Buffer): Unit = ???

    final def serializeCurrency(currency: Currency, out: Buffer): Unit = ???

    final def deserializeUUID(in: Buffer): UUID = ???

    final def deserializeLocale(in: Buffer): Locale = ???

    final def deserializeCurrency(in: Buffer): Currency = ???

    final def serializeMoney(money: Money, out: Buffer): Unit = ???

    final def deserializeMoney(in: Buffer): Money = ???

}
