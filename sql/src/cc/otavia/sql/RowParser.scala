/*
 * Copyright 2022 Yan Kun <yan_kun_1992@foxmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License")

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

package cc.otavia.sql

import java.math.{BigInteger, BigDecimal as JBigDecimal}
import java.time.{Duration as JDuration, *}
import java.util.{Currency, Locale, UUID}
import scala.concurrent.duration.Duration

trait RowParser {

    def nameQueryIndex(columnName: String): Int

    def indexQueryName(columnIndex: Int): String

    def isNull(columnIndex: Int): Boolean

    def parseChar(columnIndex: Int): Char

    def parseString(columnIndex: Int): String

    def parseBoolean(columnIndex: Int): Boolean

    def parseByte(columnIndex: Int): Byte

    def parseShort(columnIndex: Int): Short

    def parseInt(columnIndex: Int): Int

    def parseLong(columnIndex: Int): Long

    def parseFloat(columnIndex: Int): Float

    def parseDouble(columnIndex: Int): Double

    def parseBigInt(columnIndex: Int): BigInt

    def parseBigDecimal(columnIndex: Int): BigDecimal

    def parseBigInteger(columnIndex: Int): BigInteger

    def parseJBigDecimal(columnIndex: Int): JBigDecimal

    def parseInstant(columnIndex: Int): Instant

    def parseLocalDate(columnIndex: Int): LocalDate

    def parseLocalDateTime(columnIndex: Int): LocalDateTime

    def parseLocalTime(columnIndex: Int): LocalTime

    def parseMonthDay(columnIndex: Int): MonthDay

    def parseOffsetDateTime(columnIndex: Int): OffsetDateTime

    def parseOffsetTime(columnIndex: Int): OffsetTime

    def parsePeriod(columnIndex: Int): Period

    def parseYear(columnIndex: Int): Year

    def parseYearMonth(columnIndex: Int): YearMonth

    def parseZoneId(columnIndex: Int): ZoneId

    def parseZoneOffset(columnIndex: Int): ZoneOffset

    def parseZonedDateTime(columnIndex: Int): ZonedDateTime

    def parseJDuration(columnIndex: Int): JDuration

    def parseDuration(columnIndex: Int): Duration

    def parseCurrency(columnIndex: Int): Currency

    def parseLocale(columnIndex: Int): Locale

    def parseUUID(columnIndex: Int): UUID

    final def parseChar(columnName: String): Char = parseChar(nameQueryIndex(columnName))

    final def parseString(columnName: String): String = parseString(nameQueryIndex(columnName))

    final def parseBoolean(columnName: String): Boolean = parseBoolean(nameQueryIndex(columnName))

    final def parseByte(columnName: String): Byte = parseByte(nameQueryIndex(columnName))

    final def parseShort(columnName: String): Short = parseShort(nameQueryIndex(columnName))

    final def parseInt(columnName: String): Int = parseInt(nameQueryIndex(columnName))

    final def parseLong(columnName: String): Long = parseLong(nameQueryIndex(columnName))

    final def parseFloat(columnName: String): Float = parseFloat(nameQueryIndex(columnName))

    final def parseDouble(columnName: String): Double = parseDouble(nameQueryIndex(columnName))

    final def parseBigInt(columnName: String): BigInt = parseBigInt(nameQueryIndex(columnName))

    final def parseBigDecimal(columnName: String): BigDecimal = parseBigDecimal(nameQueryIndex(columnName))

    final def parseBigInteger(columnName: String): BigInteger = parseBigInteger(nameQueryIndex(columnName))

    final def parseJBigDecimal(columnName: String): JBigDecimal = parseJBigDecimal(nameQueryIndex(columnName))

    final def parseInstant(columnName: String): Instant = parseInstant(nameQueryIndex(columnName))

    final def parseLocalDate(columnName: String): LocalDate = parseLocalDate(nameQueryIndex(columnName))

    final def parseLocalDateTime(columnName: String): LocalDateTime = parseLocalDateTime(nameQueryIndex(columnName))

    final def parseLocalTime(columnName: String): LocalTime = parseLocalTime(nameQueryIndex(columnName))

    final def parseMonthDay(columnName: String): MonthDay = parseMonthDay(nameQueryIndex(columnName))

    final def parseOffsetDateTime(columnName: String): OffsetDateTime = parseOffsetDateTime(nameQueryIndex(columnName))

    final def parseOffsetTime(columnName: String): OffsetTime = parseOffsetTime(nameQueryIndex(columnName))

    final def parsePeriod(columnName: String): Period = parsePeriod(nameQueryIndex(columnName))

    final def parseYear(columnName: String): Year = parseYear(nameQueryIndex(columnName))

    final def parseYearMonth(columnName: String): YearMonth = parseYearMonth(nameQueryIndex(columnName))

    final def parseZoneId(columnName: String): ZoneId = parseZoneId(nameQueryIndex(columnName))

    final def parseZoneOffset(columnName: String): ZoneOffset = parseZoneOffset(nameQueryIndex(columnName))

    final def parseZonedDateTime(columnName: String): ZonedDateTime = parseZonedDateTime(nameQueryIndex(columnName))

    final def parseJDuration(columnName: String): JDuration = parseJDuration(nameQueryIndex(columnName))

    final def parseDuration(columnName: String): Duration = parseDuration(nameQueryIndex(columnName))

    final def parseCurrency(columnName: String): Currency = parseCurrency(nameQueryIndex(columnName))

    final def parseLocale(columnName: String): Locale = parseLocale(nameQueryIndex(columnName))

    final def parseUUID(columnName: String): UUID = parseUUID(nameQueryIndex(columnName))

    final def parseCharOption(columnIndex: Int): Option[Char] =
        if (isNull(columnIndex)) None else Some(parseChar(columnIndex))

    final def parseStringOption(columnIndex: Int): Option[String] =
        if (isNull(columnIndex)) None else Some(parseString(columnIndex))

    final def parseBooleanOption(columnIndex: Int): Option[Boolean] =
        if (isNull(columnIndex)) None else Some(parseBoolean(columnIndex))

    final def parseByteOption(columnIndex: Int): Option[Byte] =
        if (isNull(columnIndex)) None else Some(parseByte(columnIndex))

    final def parseShortOption(columnIndex: Int): Option[Short] =
        if (isNull(columnIndex)) None else Some(parseShort(columnIndex))

    final def parseIntOption(columnIndex: Int): Option[Int] =
        if (isNull(columnIndex)) None else Some(parseInt(columnIndex))

    final def parseLongOption(columnIndex: Int): Option[Long] =
        if (isNull(columnIndex)) None else Some(parseLong(columnIndex))

    final def parseFloatOption(columnIndex: Int): Option[Float] =
        if (isNull(columnIndex)) None else Some(parseFloat(columnIndex))

    final def parseDoubleOption(columnIndex: Int): Option[Double] =
        if (isNull(columnIndex)) None else Some(parseDouble(columnIndex))

    final def parseBigIntOption(columnIndex: Int): Option[BigInt] =
        if (isNull(columnIndex)) None else Some(parseBigInt(columnIndex))

    final def parseBigDecimalOption(columnIndex: Int): Option[BigDecimal] =
        if (isNull(columnIndex)) None else Some(parseBigDecimal(columnIndex))

    final def parseBigIntegerOption(columnIndex: Int): Option[BigInteger] =
        if (isNull(columnIndex)) None else Some(parseBigInteger(columnIndex))

    final def parseJBigDecimalOption(columnIndex: Int): Option[JBigDecimal] =
        if (isNull(columnIndex)) None else Some(parseJBigDecimal(columnIndex))

    final def parseInstantOption(columnIndex: Int): Option[Instant] =
        if (isNull(columnIndex)) None else Some(parseInstant(columnIndex))

    final def parseLocalDateOption(columnIndex: Int): Option[LocalDate] =
        if (isNull(columnIndex)) None else Some(parseLocalDate(columnIndex))

    final def parseLocalDateTimeOption(columnIndex: Int): Option[LocalDateTime] =
        if (isNull(columnIndex)) None else Some(parseLocalDateTime(columnIndex))

    final def parseLocalTimeOption(columnIndex: Int): Option[LocalTime] =
        if (isNull(columnIndex)) None else Some(parseLocalTime(columnIndex))

    final def parseMonthDayOption(columnIndex: Int): Option[MonthDay] =
        if (isNull(columnIndex)) None else Some(parseMonthDay(columnIndex))

    final def parseOffsetDateTimeOption(columnIndex: Int): Option[OffsetDateTime] =
        if (isNull(columnIndex)) None else Some(parseOffsetDateTime(columnIndex))

    final def parseOffsetTimeOption(columnIndex: Int): Option[OffsetTime] =
        if (isNull(columnIndex)) None else Some(parseOffsetTime(columnIndex))

    final def parsePeriodOption(columnIndex: Int): Option[Period] =
        if (isNull(columnIndex)) None else Some(parsePeriod(columnIndex))

    final def parseYearOption(columnIndex: Int): Option[Year] =
        if (isNull(columnIndex)) None else Some(parseYear(columnIndex))

    final def parseYearMonthOption(columnIndex: Int): Option[YearMonth] =
        if (isNull(columnIndex)) None else Some(parseYearMonth(columnIndex))

    final def parseZoneIdOption(columnIndex: Int): Option[ZoneId] =
        if (isNull(columnIndex)) None else Some(parseZoneId(columnIndex))

    final def parseZoneOffsetOption(columnIndex: Int): Option[ZoneOffset] =
        if (isNull(columnIndex)) None else Some(parseZoneOffset(columnIndex))

    final def parseZonedDateTimeOption(columnIndex: Int): Option[ZonedDateTime] =
        if (isNull(columnIndex)) None else Some(parseZonedDateTime(columnIndex))

    final def parseJDurationOption(columnIndex: Int): Option[JDuration] =
        if (isNull(columnIndex)) None else Some(parseJDuration(columnIndex))

    final def parseDurationOption(columnIndex: Int): Option[Duration] =
        if (isNull(columnIndex)) None else Some(parseDuration(columnIndex))

    final def parseCurrencyOption(columnIndex: Int): Option[Currency] =
        if (isNull(columnIndex)) None else Some(parseCurrency(columnIndex))

    final def parseLocaleOption(columnIndex: Int): Option[Locale] =
        if (isNull(columnIndex)) None else Some(parseLocale(columnIndex))

    final def parseUUIDOption(columnIndex: Int): Option[UUID] =
        if (isNull(columnIndex)) None else Some(parseUUID(columnIndex))

    final def parseCharOption(columnName: String): Option[Char] = {
        val index = nameQueryIndex(columnName)
        if (isNull(index)) None else Some(parseChar(index))
    }

    final def parseStringOption(columnName: String): Option[String] = {
        val index = nameQueryIndex(columnName)
        if (isNull(index)) None else Some(parseString(index))
    }

    final def parseBooleanOption(columnName: String): Option[Boolean] = {
        val index = nameQueryIndex(columnName)
        if (isNull(index)) None else Some(parseBoolean(index))
    }

    final def parseByteOption(columnName: String): Option[Byte] = {
        val index = nameQueryIndex(columnName)
        if (isNull(index)) None else Some(parseByte(index))
    }

    final def parseShortOption(columnName: String): Option[Short] = {
        val index = nameQueryIndex(columnName)
        if (isNull(index)) None else Some(parseShort(index))
    }

    final def parseIntOption(columnName: String): Option[Int] = {
        val index = nameQueryIndex(columnName)
        if (isNull(index)) None else Some(parseInt(index))
    }

    final def parseLongOption(columnName: String): Option[Long] = {
        val index = nameQueryIndex(columnName)
        if (isNull(index)) None else Some(parseLong(index))
    }

    final def parseFloatOption(columnName: String): Option[Float] = {
        val index = nameQueryIndex(columnName)
        if (isNull(index)) None else Some(parseFloat(index))
    }

    final def parseDoubleOption(columnName: String): Option[Double] = {
        val index = nameQueryIndex(columnName)
        if (isNull(index)) None else Some(parseDouble(index))
    }

    final def parseBigIntOption(columnName: String): Option[BigInt] = {
        val index = nameQueryIndex(columnName)
        if (isNull(index)) None else Some(parseBigInt(index))
    }

    final def parseBigDecimalOption(columnName: String): Option[BigDecimal] = {
        val index = nameQueryIndex(columnName)
        if (isNull(index)) None else Some(parseBigDecimal(index))
    }

    final def parseBigIntegerOption(columnName: String): Option[BigInteger] = {
        val index = nameQueryIndex(columnName)
        if (isNull(index)) None else Some(parseBigInteger(index))
    }

    final def parseJBigDecimalOption(columnName: String): Option[JBigDecimal] = {
        val index = nameQueryIndex(columnName)
        if (isNull(index)) None else Some(parseJBigDecimal(index))
    }

    final def parseInstantOption(columnName: String): Option[Instant] = {
        val index = nameQueryIndex(columnName)
        if (isNull(index)) None else Some(parseInstant(index))
    }

    final def parseLocalDateOption(columnName: String): Option[LocalDate] = {
        val index = nameQueryIndex(columnName)
        if (isNull(index)) None else Some(parseLocalDate(index))
    }

    final def parseLocalDateTimeOption(columnName: String): Option[LocalDateTime] = {
        val index = nameQueryIndex(columnName)
        if (isNull(index)) None else Some(parseLocalDateTime(index))
    }

    final def parseLocalTimeOption(columnName: String): Option[LocalTime] = {
        val index = nameQueryIndex(columnName)
        if (isNull(index)) None else Some(parseLocalTime(index))
    }

    final def parseMonthDayOption(columnName: String): Option[MonthDay] = {
        val index = nameQueryIndex(columnName)
        if (isNull(index)) None else Some(parseMonthDay(index))
    }

    final def parseOffsetDateTimeOption(columnName: String): Option[OffsetDateTime] = {
        val index = nameQueryIndex(columnName)
        if (isNull(index)) None else Some(parseOffsetDateTime(index))
    }

    final def parseOffsetTimeOption(columnName: String): Option[OffsetTime] = {
        val index = nameQueryIndex(columnName)
        if (isNull(index)) None else Some(parseOffsetTime(index))
    }

    final def parsePeriodOption(columnName: String): Option[Period] = {
        val index = nameQueryIndex(columnName)
        if (isNull(index)) None else Some(parsePeriod(index))
    }

    final def parseYearOption(columnName: String): Option[Year] = {
        val index = nameQueryIndex(columnName)
        if (isNull(index)) None else Some(parseYear(index))
    }

    final def parseYearMonthOption(columnName: String): Option[YearMonth] = {
        val index = nameQueryIndex(columnName)
        if (isNull(index)) None else Some(parseYearMonth(index))
    }

    final def parseZoneIdOption(columnName: String): Option[ZoneId] = {
        val index = nameQueryIndex(columnName)
        if (isNull(index)) None else Some(parseZoneId(index))
    }

    final def parseZoneOffsetOption(columnName: String): Option[ZoneOffset] = {
        val index = nameQueryIndex(columnName)
        if (isNull(index)) None else Some(parseZoneOffset(index))
    }

    final def parseZonedDateTimeOption(columnName: String): Option[ZonedDateTime] = {
        val index = nameQueryIndex(columnName)
        if (isNull(index)) None else Some(parseZonedDateTime(index))
    }

    final def parseJDurationOption(columnName: String): Option[JDuration] = {
        val index = nameQueryIndex(columnName)
        if (isNull(index)) None else Some(parseJDuration(index))
    }

    final def parseDurationOption(columnName: String): Option[Duration] = {
        val index = nameQueryIndex(columnName)
        if (isNull(index)) None else Some(parseDuration(index))
    }

    final def parseCurrencyOption(columnName: String): Option[Currency] = {
        val index = nameQueryIndex(columnName)
        if (isNull(index)) None else Some(parseCurrency(index))
    }

    final def parseLocaleOption(columnName: String): Option[Locale] = {
        val index = nameQueryIndex(columnName)
        if (isNull(index)) None else Some(parseLocale(index))
    }

    final def parseUUIDOption(columnName: String): Option[UUID] = {
        val index = nameQueryIndex(columnName)
        if (isNull(index)) None else Some(parseUUID(index))
    }

}
