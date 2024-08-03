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

import cc.otavia.buffer.Buffer
import cc.otavia.datatype.Money
import cc.otavia.json.JsonMacro.derivedMacro
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

trait JsonSerde[A] extends Serde[A] with SerdeOps {

    final protected def skipBlanks(in: Buffer): Unit = JsonHelper.skipBlanks(in)

    final protected def serializeObjectStart(out: Buffer): this.type = {
        JsonHelper.serializeObjectStart(out)
        this
    }

    final protected def skipObjectStart(in: Buffer): Boolean = JsonHelper.skipObjectStart(in)

    final protected def serializeArrayStart(out: Buffer): this.type = {
        JsonHelper.serializeArrayStart(out)
        this
    }

    final protected def skipArrayStart(in: Buffer): Boolean = JsonHelper.skipArrayStart(in)

    final protected def serializeObjectEnd(out: Buffer): this.type = {
        JsonHelper.serializeObjectEnd(out)
        this
    }

    final protected def skipObjectEnd(in: Buffer): Boolean = JsonHelper.skipObjectEnd(in)

    final protected def serializeArrayEnd(out: Buffer): this.type = {
        JsonHelper.serializeArrayEnd(out)
        this
    }

    final protected def skipArrayEnd(in: Buffer): Boolean = JsonHelper.skipArrayEnd(in)

    final protected def serializeKey(key: String, out: Buffer): this.type = {
        JsonHelper.serializeKey(key, out)
        this
    }

    override final protected def serializeByte(byte: Byte, out: Buffer): this.type = serializeInt(byte, out)

    override final protected def serializeBoolean(boolean: Boolean, out: Buffer): this.type = {
        JsonHelper.serializeBoolean(boolean, out)
        this
    }

    override final protected def serializeChar(char: Char, out: Buffer): this.type = {
        JsonHelper.serializeChar(char, out)
        this
    }

    override final protected def serializeShort(short: Short, out: Buffer): this.type = {
        JsonHelper.serializeShort(short, out)
        this
    }

    override final protected def serializeInt(int: Int, out: Buffer): this.type = {
        JsonHelper.serializeInt(int, out)
        this
    }

    override final protected def serializeLong(long: Long, out: Buffer): this.type = {
        JsonHelper.serializeLong(long, out)
        this
    }

    override final protected def serializeFloat(float: Float, out: Buffer): this.type = {
        JsonHelper.serializeFloat(float, out)
        this
    }

    override final protected def serializeDouble(double: Double, out: Buffer): this.type = {
        JsonHelper.serializeDouble(double, out)
        this
    }

    override final protected def serializeString(string: String, out: Buffer): this.type = {
        JsonHelper.serializeString(string, out)
        this
    }

    override final protected def deserializeByte(in: Buffer): Byte = deserializeInt(in).toByte

    override final protected def deserializeBoolean(in: Buffer): Boolean = JsonHelper.deserializeBoolean(in)

    override final protected def deserializeChar(in: Buffer): Char = JsonHelper.deserializeChar(in)

    override final protected def deserializeShort(in: Buffer): Short = deserializeInt(in).toShort

    override final protected def deserializeInt(in: Buffer): Int = JsonHelper.deserializeInt(in)

    override final protected def deserializeLong(in: Buffer): Long = JsonHelper.deserializeLong(in)

    override final protected def deserializeFloat(in: Buffer): Float = JsonHelper.deserializeFloat(in)

    override final protected def deserializeDouble(in: Buffer): Double = JsonHelper.deserializeDouble(in)

    override final protected def deserializeString(in: Buffer): String = JsonHelper.deserializeString(in)

    // math type

    override final protected def serializeBigInt(bigInt: BigInt, out: Buffer): this.type = {
        JsonHelper.serializeBigInt(bigInt, out)
        this
    }

    override final protected def serializeBigDecimal(bigDecimal: BigDecimal, out: Buffer): this.type = {
        JsonHelper.serializeBigDecimal(bigDecimal, out)
        this
    }

    override final protected def serializeBigInteger(bigInteger: BigInteger, out: Buffer): this.type = {
        JsonHelper.serializeBigInteger(bigInteger, out)
        this
    }

    override final protected def serializeJBigDecimal(bigDecimal: java.math.BigDecimal, out: Buffer): this.type = {
        JsonHelper.serializeJBigDecimal(bigDecimal, out)
        this
    }

    override final protected def deserializeBigInt(in: Buffer): BigInt = JsonHelper.deserializeBigInt(in)

    override final protected def deserializeBigDecimal(in: Buffer): BigDecimal = JsonHelper.deserializeBigDecimal(in)

    override final protected def deserializeBigInteger(in: Buffer): BigInteger = JsonHelper.deserializeBigInteger(in)

    override final protected def deserializeJBigDecimal(in: Buffer): java.math.BigDecimal =
        JsonHelper.deserializeJBigDecimal(in)

    override final protected def serializeJDuration(duration: JDuration, out: Buffer): this.type = {
        JsonHelper.serializeJDuration(duration, out)
        this
    }

    override final protected def serializeInstant(instant: Instant, out: Buffer): this.type = {
        JsonHelper.serializeInstant(instant, out)
        this
    }

    override final protected def serializeLocalDate(localDate: LocalDate, out: Buffer): this.type = {
        JsonHelper.serializeLocalDate(localDate, out)
        this
    }

    override final protected def serializeLocalDateTime(
        localDateTime: LocalDateTime,
        out: Buffer
    ): this.type = {
        JsonHelper.serializeLocalDateTime(localDateTime, out)
        this
    }

    override final protected def serializeLocalTime(localTime: LocalTime, out: Buffer): this.type = {
        JsonHelper.serializeLocalTime(localTime, out)
        this
    }

    override final protected def serializeMonthDay(monthDay: MonthDay, out: Buffer): this.type = {
        JsonHelper.serializeMonthDay(monthDay, out)
        this
    }

    override final protected def serializeOffsetDateTime(
        offsetDateTime: OffsetDateTime,
        out: Buffer
    ): this.type = {
        JsonHelper.serializeOffsetDateTime(offsetDateTime, out)
        this
    }

    override final protected def serializeOffsetTime(offsetTime: OffsetTime, out: Buffer): this.type = {
        JsonHelper.serializeOffsetTime(offsetTime, out)
        this
    }

    override final protected def serializePeriod(period: Period, out: Buffer): this.type = {
        JsonHelper.serializePeriod(period, out)
        this
    }

    override final protected def serializeYear(year: Year, out: Buffer): this.type = {
        JsonHelper.serializeYear(year, out)
        this
    }

    override final protected def serializeYearMonth(yearMonth: YearMonth, out: Buffer): this.type = {
        JsonHelper.serializeYearMonth(yearMonth, out)
        this
    }

    override final protected def serializeZonedDateTime(
        zonedDateTime: ZonedDateTime,
        out: Buffer
    ): this.type = {
        JsonHelper.serializeZonedDateTime(zonedDateTime, out)
        this
    }

    override final protected def serializeZoneId(zoneId: ZoneId, out: Buffer): this.type = {
        JsonHelper.serializeZoneId(zoneId, out)
        this
    }

    override final protected def serializeZoneOffset(zoneOffset: ZoneOffset, out: Buffer): this.type = {
        JsonHelper.serializeZoneOffset(zoneOffset, out)
        this
    }

    override final protected def deserializeJDuration(in: Buffer): JDuration = JsonHelper.deserializeJDuration(in)

    override final protected def deserializeDuration(in: Buffer): Duration = JsonHelper.deserializeDuration(in)

    override final protected def deserializeInstant(in: Buffer): Instant = JsonHelper.deserializeInstant(in)

    override final protected def deserializeLocalDate(in: Buffer): LocalDate = JsonHelper.deserializeLocalDate(in)

    override final protected def deserializeLocalDateTime(in: Buffer): LocalDateTime =
        JsonHelper.deserializeLocalDateTime(in)

    override final protected def deserializeLocalTime(in: Buffer): LocalTime = JsonHelper.deserializeLocalTime(in)

    override final protected def deserializeMonthDay(in: Buffer): MonthDay = JsonHelper.deserializeMonthDay(in)

    override final protected def deserializeOffsetDateTime(in: Buffer): OffsetDateTime =
        JsonHelper.deserializeOffsetDateTime(in)

    override final protected def deserializeOffsetTime(in: Buffer): OffsetTime = JsonHelper.deserializeOffsetTime(in)

    override final protected def deserializePeriod(in: Buffer): Period = JsonHelper.deserializePeriod(in)

    override final protected def deserializeYear(in: Buffer): Year = JsonHelper.deserializeYear(in)

    override final protected def deserializeYearMonth(in: Buffer): YearMonth = JsonHelper.deserializeYearMonth(in)

    override final protected def deserializeZonedDateTime(in: Buffer): ZonedDateTime =
        JsonHelper.deserializeZonedDateTime(in)

    override final protected def deserializeZoneId(in: Buffer): ZoneId = JsonHelper.deserializeZoneId(in)

    override final protected def deserializeZoneOffset(in: Buffer): ZoneOffset = JsonHelper.deserializeZoneOffset(in)

    override final protected def serializeUUID(uuid: UUID, out: Buffer): this.type = {
        JsonHelper.serializeUUID(uuid, out)
        this
    }

    override final protected def deserializeUUID(in: Buffer): UUID = JsonHelper.deserializeUUID(in)

}

object JsonSerde {

    given JsonSerde[BigInt]         = BigIntJsonSerde
    given JsonSerde[BigDecimal]     = BigDecimalJsonSerde
    given JsonSerde[BigInteger]     = BigIntegerJsonSerde
    given JsonSerde[JBigDecimal]    = JBigDecimalJsonSerde
    given JsonSerde[JDuration]      = JDurationJsonSerde
    given JsonSerde[Instant]        = InstantJsonSerde
    given JsonSerde[LocalDate]      = LocalDateJsonSerde
    given JsonSerde[LocalDateTime]  = LocalDateTimeJsonSerde
    given JsonSerde[LocalTime]      = LocalTimeJsonSerde
    given JsonSerde[MonthDay]       = MonthDayJsonSerde
    given JsonSerde[OffsetDateTime] = OffsetDateTimeJsonSerde
    given JsonSerde[OffsetTime]     = OffsetTimeJsonSerde
    given JsonSerde[Period]         = PeriodJsonSerde
    given JsonSerde[Year]           = YearJsonSerde
    given JsonSerde[YearMonth]      = YearMonthJsonSerde
    given JsonSerde[ZonedDateTime]  = ZonedDateTimeJsonSerde
    given JsonSerde[ZoneId]         = ZoneIdJsonSerde
    given JsonSerde[ZoneOffset]     = ZoneOffsetJsonSerde
    given JsonSerde[UUID]           = UUIDJsonSerde
    given JsonSerde[Locale]         = LocaleJsonSerde
    given JsonSerde[Currency]       = CurrencyJsonSerde
    given JsonSerde[Money]          = MoneyJsonSerde
    given JsonSerde[String]         = StringJsonSerde

    /** Derives a [[JsonSerde]] for JSON values for the specified type [[T]].
     *
     *  @tparam T
     *    a type that should be encoded and decoded by the derived serde
     *  @return
     *    an instance of the derived serde
     */
    inline def derived[T]: JsonSerde[T] = ${ derivedMacro[T] }

}
