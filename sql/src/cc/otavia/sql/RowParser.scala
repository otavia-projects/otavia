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

package cc.otavia.sql

import java.math.{BigInteger, BigDecimal as JBigDecimal}
import java.time.{Duration as JDuration, *}
import java.util.{Currency, Locale, UUID}
import scala.concurrent.duration.Duration

trait RowParser {

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

    def parseChar(columnName: String): Char

    def parseString(columnName: String): String

    def parseBoolean(columnName: String): Boolean

    def parseByte(columnName: String): Byte

    def parseShort(columnName: String): Short

    def parseInt(columnName: String): Int

    def parseLong(columnName: String): Long

    def parseFloat(columnName: String): Float

    def parseDouble(columnName: String): Double

    def parseBigInt(columnName: String): BigInt

    def parseBigDecimal(columnName: String): BigDecimal

    def parseBigInteger(columnName: String): BigInteger

    def parseJBigDecimal(columnName: String): JBigDecimal

    def parseInstant(columnName: String): Instant

    def parseLocalDate(columnName: String): LocalDate

    def parseLocalDateTime(columnName: String): LocalDateTime

    def parseLocalTime(columnName: String): LocalTime

    def parseMonthDay(columnName: String): MonthDay

    def parseOffsetDateTime(columnName: String): OffsetDateTime

    def parseOffsetTime(columnName: String): OffsetTime

    def parsePeriod(columnName: String): Period

    def parseYear(columnName: String): Year

    def parseYearMonth(columnName: String): YearMonth

    def parseZoneId(columnName: String): ZoneId

    def parseZoneOffset(columnName: String): ZoneOffset

    def parseZonedDateTime(columnName: String): ZonedDateTime

    def parseJDuration(columnName: String): JDuration

    def parseDuration(columnName: String): Duration

    def parseCurrency(columnName: String): Currency

    def parseLocale(columnName: String): Locale

    def parseUUID(columnName: String): UUID

}
