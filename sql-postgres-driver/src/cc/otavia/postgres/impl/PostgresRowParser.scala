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

package cc.otavia.postgres.impl

import cc.otavia.buffer.Buffer
import cc.otavia.postgres.protocol.DataFormat
import cc.otavia.sql.RowParser

import java.lang
import java.math.BigInteger
import java.nio.CharBuffer
import java.time.format.DateTimeFormatter.{ISO_LOCAL_DATE, ISO_LOCAL_TIME}
import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder}
import java.time.temporal.ChronoUnit
import java.time.{Duration as JDuration, *}
import java.util.{Currency, Locale, UUID}
import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.language.unsafeNulls

final class PostgresRowParser extends RowParser {

    import PostgresRowParser.*

    private var desc: RowDesc                           = _
    private var buffer: Buffer                          = _
    private var offsets: mutable.ArrayBuffer[RowOffset] = _

    def setRowDesc(rowDesc: RowDesc): Unit                              = this.desc = rowDesc
    def setPayload(buffer: Buffer): Unit                                = this.buffer = buffer
    def setRowOffsets(rowOffsets: mutable.ArrayBuffer[RowOffset]): Unit = this.offsets = rowOffsets

    override def isNull(columnIndex: Int): Boolean = offsets(columnIndex).length == -1

    override def nameQueryIndex(columnName: String): Int = desc.queryColumnIndex(columnName)

    override def indexQueryName(columnIndex: Int): String = desc(columnIndex).name

    override def parseChar(columnIndex: Int): Char = ???

    override def parseString(columnIndex: Int): String = {
        val rowOffset = offsets(columnIndex)
        val rowDesc   = desc(columnIndex)
        val offset    = buffer.readerOffset + rowOffset.offset + 4
        if (rowDesc.dataFormat == DataFormat.TEXT) {} else {}
        buffer.getCharSequence(offset, rowOffset.length).toString
    }

    override def parseBoolean(columnIndex: Int): Boolean = {
        val rowOffset = offsets(columnIndex)
        val rowDesc   = desc(columnIndex)
        val offset    = buffer.readerOffset + rowOffset.offset + 4
        if (rowDesc.dataFormat == DataFormat.TEXT)
            buffer.getByte(offset) == 't'
        else
            buffer.getBoolean(offset)
    }

    override def parseByte(columnIndex: Int): Byte = ???

    override def parseShort(columnIndex: Int): Short = ???

    override def parseInt(columnIndex: Int): Int = {
        val rowOffset = offsets(columnIndex)
        val value     = buffer.getStringAsLong(buffer.readerOffset + rowOffset.offset + 4, rowOffset.length)
        value.toInt
    }

    override def parseLong(columnIndex: Int): Long = {
        val rowOffset = offsets(columnIndex)
        buffer.getStringAsLong(buffer.readerOffset + rowOffset.offset + 4, rowOffset.length)
    }

    override def parseFloat(columnIndex: Int): Float = ???

    override def parseDouble(columnIndex: Int): Double = ???

    override def parseBigInt(columnIndex: Int): BigInt = ???

    override def parseBigDecimal(columnIndex: Int): BigDecimal = ???

    override def parseBigInteger(columnIndex: Int): BigInteger = ???

    override def parseJBigDecimal(columnIndex: Int): java.math.BigDecimal = ???

    override def parseInstant(columnIndex: Int): Instant = ???

    override def parseLocalDate(columnIndex: Int): LocalDate = ???

    override def parseLocalDateTime(columnIndex: Int): LocalDateTime = {
        val rowOffset = offsets(columnIndex)
        val rowDesc   = desc(columnIndex)
        val offset    = buffer.readerOffset + rowOffset.offset + 4
        if (rowDesc.dataFormat == DataFormat.TEXT) {
            val str = buffer.getCharSequence(offset, rowOffset.length).toString
            str match
                case "infinity"  => LocalDateTime.MAX
                case "-infinity" => LocalDateTime.MIN
                case _           => LocalDateTime.parse(str, TIMESTAMP_FORMAT)
        } else {
            val value = LOCAL_DATE_TIME_EPOCH.plus(buffer.getLong(offset), ChronoUnit.MICROS)
            if (LDT_PLUS_INFINITY.equals(value)) LocalDateTime.MAX
            else if (LDT_MINUS_INFINITY.equals(value)) LocalDateTime.MIN
            else value
        }
    }

    override def parseLocalTime(columnIndex: Int): LocalTime = ???

    override def parseMonthDay(columnIndex: Int): MonthDay = ???

    override def parseOffsetDateTime(columnIndex: Int): OffsetDateTime = ???

    override def parseOffsetTime(columnIndex: Int): OffsetTime = ???

    override def parsePeriod(columnIndex: Int): Period = ???

    override def parseYear(columnIndex: Int): Year = ???

    override def parseYearMonth(columnIndex: Int): YearMonth = ???

    override def parseZoneId(columnIndex: Int): ZoneId = ???

    override def parseZoneOffset(columnIndex: Int): ZoneOffset = ???

    override def parseZonedDateTime(columnIndex: Int): ZonedDateTime = ???

    override def parseJDuration(columnIndex: Int): JDuration = ???

    override def parseDuration(columnIndex: Int): Duration = ???

    override def parseCurrency(columnIndex: Int): Currency = ???

    override def parseLocale(columnIndex: Int): Locale = ???

    override def parseUUID(columnIndex: Int): UUID = ???

}

object PostgresRowParser {

    private val TIMESTAMP_FORMAT = new DateTimeFormatterBuilder().parseCaseInsensitive
        .append(ISO_LOCAL_DATE)
        .appendLiteral(' ')
        .append(ISO_LOCAL_TIME)
        .toFormatter

    private val LOCAL_DATE_TIME_EPOCH  = LocalDateTime.of(2000, 1, 1, 0, 0, 0)
    private val OFFSET_DATE_TIME_EPOCH = LocalDateTime.of(2000, 1, 1, 0, 0, 0).atOffset(ZoneOffset.UTC)

    // 294277-01-09 04:00:54.775807
    val LDT_PLUS_INFINITY: LocalDateTime = LOCAL_DATE_TIME_EPOCH.plus(Long.MaxValue, ChronoUnit.MICROS)
    // 4714-11-24 00:00:00 BC
    val LDT_MINUS_INFINITY: LocalDateTime =
        LocalDateTime.parse("4714-11-24 00:00:00 BC", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss G", Locale.ROOT))

}
