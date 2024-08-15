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

import cc.otavia.buffer.Buffer

import java.math.{BigInteger, BigDecimal as JBigDecimal}
import java.time.{Duration as JDuration, *}
import java.util.{Currency, Locale, UUID}
import scala.concurrent.duration.Duration

trait RowWriter {

    def writeRowStart(): Unit

    def writeRowEnd(): Unit

    def writeNull(index: Int): Unit

    def writeChar(value: Char, index: Int): Unit

    def writeString(value: String, index: Int): Unit

    def writeBoolean(value: Boolean, index: Int): Unit

    def writeByte(value: Byte, index: Int): Unit

    def writeShort(value: Short, index: Int): Unit

    def writeInt(value: Int, index: Int): Unit

    def writeLong(value: Long, index: Int): Unit

    def writeFloat(value: Float, index: Int): Unit

    def writeDouble(value: Double, index: Int): Unit

    def writeBigInt(value: BigInt, index: Int): Unit

    def writeBigDecimal(value: BigDecimal, index: Int): Unit

    def writeBigInteger(value: BigInteger, index: Int): Unit

    def writeJBigDecimal(value: JBigDecimal, index: Int): Unit

    def writeInstant(value: Instant, index: Int): Unit

    def writeLocalDate(value: LocalDate, index: Int): Unit

    def writeLocalDateTime(value: LocalDateTime, index: Int): Unit

    def writeLocalTime(value: LocalTime, index: Int): Unit

    def writeMonthDay(value: MonthDay, index: Int): Unit

    def writeOffsetDateTime(value: OffsetDateTime, index: Int): Unit

    def writeOffsetTime(value: OffsetTime, index: Int): Unit

    def writePeriod(value: Period, index: Int): Unit

    def writeYear(value: Year, index: Int): Unit

    def writeYearMonth(value: YearMonth, index: Int): Unit

    def writeZoneId(value: ZoneId, index: Int): Unit

    def writeZoneOffset(value: ZoneOffset, index: Int): Unit

    def writeZonedDateTime(value: ZonedDateTime, index: Int): Unit

    def writeJDuration(value: JDuration, index: Int): Unit

    def writeDuration(value: Duration, index: Int): Unit

    def writeCurrency(value: Currency, index: Int): Unit

    def writeLocale(value: Locale, index: Int): Unit

    def writeUUID(value: UUID, index: Int): Unit

}
