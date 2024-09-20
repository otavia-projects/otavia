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

package cc.otavia.buffer.utils

import cc.otavia.buffer.Buffer
import cc.otavia.buffer.constant.DurationConstants

import java.time.format.DateTimeParseException
import java.time.{Duration as JDuration, *}
import scala.concurrent.duration.*
import scala.language.unsafeNulls

trait BufferTimeUtils extends BufferNumberUtils {

    final def writeYearAsString(buffer: Buffer, year: Year): Unit = writeYearAsString(buffer, year.getValue)

    final def writeYearAsString(buffer: Buffer, year: Int): Unit = {
        val ds = BufferConstants.digits
        if (year >= 0 && year < 10000) write4Digits(buffer, year, ds) else writeYearWithSign(buffer, year, ds)
    }

    private def writeYearWithSign(buffer: Buffer, year: Int, ds: Array[Short]): Unit = {
        var q0 = year

        var b: Byte = '+'
        if (q0 < 0) {
            q0 = -q0
            b = '-'
        }
        buffer.writeByte(b)
        if (q0 < 10000) write4Digits(buffer, q0, ds)
        else {
            buffer.writerOffset(buffer.writerOffset + digitCount(q0))
            writePositiveIntDigits(q0, buffer.writerOffset, buffer, ds)
        }
    }

    final def readStringAsYear(buffer: Buffer): Year = Year.of(readStringAsIntYear(buffer))

    private def parseNon4DigitYearWithByte(buffer: Buffer, maxDigits: Int): Int = {
        val b1 = buffer.readByte.toChar
        assert((b1 == '-') || (b1 == '+'), s"excepted '-' or '+', but got $b1")
        var year = buffer.readIntLE - 0x30303030
        val m =
            (year + 0x76767676 | year) & 0x80808080 // Based on the fast parsing of numbers by 8-byte words: https://github.com/wrandelshofer/FastDoubleParser/blob/0903817a765b25e654f02a5a9d4f1476c98a80c9/src/main/java/ch.randelshofer.fastdoubleparser/ch/randelshofer/fastdoubleparser/FastDoubleSimd.java#L114-L130
        assert(m == 0)
        year = (year * 2561 >> 8 & 0xff00ff) * 6553601 >> 16
        var yearDigits = 4
        while (buffer.readableBytes > 0 && buffer.nextInRange('0', '9') && yearDigits < maxDigits) {
            year = if (year > 100000000) 2147483647 else year * 10 + (buffer.readByte - '0')
            yearDigits += 1
        }
        if (b1 == '-') year = -year
        year
    }

    final def readStringAsIntYear(buffer: Buffer): Int = {
        var year = buffer.getIntLE(buffer.readerOffset) - 0x30303030
        if (((year + 0x76767676 | year) & 0x80808080) == 0) {
            year = (year * 2561 >> 8 & 0xff00ff) * 6553601 >> 16
            buffer.skipReadableBytes(4)
            year
        } else parseNon4DigitYearWithByte(buffer, 9)
    }

    final def writeYearMonthAsString(buffer: Buffer, ym: YearMonth): Unit = {
        val ds = BufferConstants.digits
        writeYearAsString(buffer, ym.getYear)
        buffer.writeMediumLE(ds(ym.getMonthValue) << 8 | 0x00002d)
    }

    final def readStringAsYearMonth(buffer: Buffer): YearMonth = {
        val year = readStringAsIntYear(buffer)
        buffer.skipReadableBytes(1) // skip '-'
        val month = (buffer.readByte - '0') * 10 + buffer.readByte - '0'
        YearMonth.of(year, month)
    }

    final def writeInstantAsString(buffer: Buffer, x: Instant): Unit = {
        val epochSecond = x.getEpochSecond
        if (epochSecond < 0) writeBeforeEpochInstant(buffer, epochSecond, x.getNano)
        else {
            val epochDay = Math.multiplyHigh(epochSecond, 1749024623285053783L) >> 13 // epochSecond / 86400
            val marchZeroDay = epochDay + 719468 // 719468 == 719528 - 60 == days 0000 to 1970 - days 1st Jan to 1st Mar
            var year =
                (Math.multiplyHigh(
                  marchZeroDay * 400 + 591,
                  4137408090565272301L
                ) >> 15).toInt // ((marchZeroDay * 400 + 591) / 146097).toInt
            var year365        = year * 365L
            var year1374389535 = year * 1374389535L
            var century        = (year1374389535 >> 37).toInt
            var marchDayOfYear = (marchZeroDay - year365).toInt - (year >> 2) + century - (century >> 2)
            if (marchDayOfYear < 0) {
                year365 -= 365
                year1374389535 -= 1374389535
                year -= 1
                century = (year1374389535 >> 37).toInt
                marchDayOfYear = (marchZeroDay - year365).toInt - (year >> 2) + century - (century >> 2)
            }
            val marchMonth = marchDayOfYear * 17135 + 6854 >> 19 // (marchDayOfYear * 5 + 2) / 153
            val day =
                marchDayOfYear - (marchMonth * 1002762 - 16383 >> 15) // marchDayOfYear - (marchMonth * 306 + 5) / 10 + 1
            val m     = 9 - marchMonth >> 4
            val month = (m & -9 | 3) + marchMonth
            year -= m
            writeInstant(buffer, year, month, day, (epochSecond - epochDay * 86400).toInt, x.getNano)
        }
    }

    private def writeBeforeEpochInstant(buffer: Buffer, epochSecond: Long, nano: Int): Unit = {
        val epochDay =
            (Math.multiplyHigh(epochSecond - 86399, 1749024623285053783L) >> 13) + 1 // (epochSecond - 86399) / 86400
        var marchZeroDay = epochDay + 719468 // 719468 == 719528 - 60 == days 0000 to 1970 - days 1st Jan to 1st Mar
        val adjust400YearCycles = ((marchZeroDay + 1) * 7525902 >> 40).toInt // ((marchZeroDay + 1) / 146097).toInt - 1
        marchZeroDay -= adjust400YearCycles * 146097L
        var year = { // ((marchZeroDay * 400 + 591) / 146097).toInt
            val pa = marchZeroDay * 400 + 591
            ((Math.multiplyHigh(pa, 4137408090565272301L) >> 15) + (pa >> 63)).toInt
        }
        var year365        = year * 365L
        var year1374389535 = year * 1374389535L
        var century        = (year1374389535 >> 37).toInt
        var marchDayOfYear = (marchZeroDay - year365).toInt - (year >> 2) + century - (century >> 2)
        if (marchDayOfYear < 0) {
            year365 -= 365
            year1374389535 -= 1374389535
            year -= 1
            century = (year1374389535 >> 37).toInt
            marchDayOfYear = (marchZeroDay - year365).toInt - (year >> 2) + century - (century >> 2)
        }
        val marchMonth = marchDayOfYear * 17135 + 6854 >> 19 // (marchDayOfYear * 5 + 2) / 153
        val day =
            marchDayOfYear - (marchMonth * 1002762 - 16383 >> 15) // marchDayOfYear - (marchMonth * 306 + 5) / 10 + 1
        val m     = 9 - marchMonth >> 4
        val month = (m & -9 | 3) + marchMonth
        year += adjust400YearCycles * 400 - m
        writeInstant(buffer, year, month, day, (epochSecond - epochDay * 86400).toInt, nano)
    }

    private def writeInstant(buffer: Buffer, year: Int, month: Int, day: Int, secsOfDay: Int, nano: Int): Unit = {
        val ds = BufferConstants.digits
        writeYearAsString(buffer, year)
        buffer.writeLongLE(ds(month) << 8 | ds(day).toLong << 32 | 0x5400002d00002dL)
        buffer.writerOffset(buffer.writerOffset - 1)
        val y1 =
            secsOfDay * 37283 // Based on James Anhalt's algorithm: https://jk-jeon.github.io/posts/2022/02/jeaiii-algorithm/
        val y2 = (y1 & 0x7ffffff) * 15
        val y3 = (y2 & 0x1ffffff) * 15
        buffer.writeLongLE(ds(y1 >>> 27) | ds(y2 >> 25).toLong << 24 | ds(y3 >> 23).toLong << 48 | 0x3a00003a0000L)
        if (nano != 0) writeNanos(buffer, nano, ds)
        buffer.writeByte(0x5a)
    }

    final def readStringAsInstant(buffer: Buffer): Instant = {
        val year = readStringAsIntYear(buffer)
        assert(buffer.skipIfNextIs('-'), s"except '-' but got ${buffer.readByte.toChar}")
        val month = readStringAsInt(buffer)
        assert(buffer.skipIfNextIs('-'), s"except '-' but got ${buffer.readByte.toChar}")
        val day = readStringAsInt(buffer)
        assert(buffer.skipIfNextIs('T'), s"except 'T' but got ${buffer.readByte.toChar}")

        val hour = readStringAsInt(buffer)
        assert(buffer.skipIfNextIs(':'), s"except ':' but got ${buffer.readByte.toChar}")
        val minute = readStringAsInt(buffer)
        assert(buffer.skipIfNextIs(':'), s"except ':' but got ${buffer.readByte.toChar}")
        val second = readStringAsInt(buffer)

        var epochSecond = epochDay(year, month, day) * 86400 // 86400 == seconds per day
        epochSecond = hour * 3600 + minute * 60 + second + epochSecond

        var nano = 0
        if (buffer.skipIfNextIs('.')) { // parse nano
            var count = 0
            while (buffer.readableBytes > 0 && buffer.nextInRange('0', '9')) {
                nano = nano * 10 + (buffer.readByte - '0')
                count += 1
            }
            if (count < 9)
                nano = nano * Math.pow(10, 9 - count).toInt
        }

        if (!buffer.skipIfNextIs('Z')) { // parse zone offset
            val b = buffer.readByte
            val isNeg =
                if (b == '-') true
                else if (b == '+') false
                else throw AssertionError(s"except '-' or '-' but got ${b.toChar}")
            var minutes = 0
            var seconds = 0

            val hours = readStringAsInt(buffer)
            if (buffer.skipIfNextIs(':')) {
                minutes = readStringAsInt(buffer)
                if (buffer.skipIfNextIs(':')) seconds = readStringAsInt(buffer)
            }
            val offsetTotal = hours * 60 * 60 + minutes * 60 + seconds
            if (isNeg) epochSecond += offsetTotal else epochSecond -= offsetTotal
        }

        if (nano == 0) Instant.ofEpochSecond(epochSecond) else Instant.ofEpochSecond(epochSecond, nano.toLong)
    }

    private def epochDay(year: Int, month: Int, day: Int): Long =
        year * 365L + ((year + 3 >> 2) - {
            val cp = year * 1374389535L
            if (year < 0) (cp >> 37) - (cp >> 39)                        // year / 100 - year / 400
            else (cp + 136064563965L >> 37) - (cp + 548381424465L >> 39) // (year + 99) / 100 - (year + 399) / 400
        }.toInt + (month * 1002277 - 988622 >> 15) +                     // (month * 367 - 362) / 12
            (if (month <= 2) -719529
             else if (isLeap(year)) -719530
             else -719531) + day) // 719528 == days 0000 to 1970)

    final def readStringAsLocalDate(buffer: Buffer): LocalDate = {
        val year = readStringAsIntYear(buffer)
        buffer.skipIfNextIs('-')
        val month = readStringAsInt(buffer)
        buffer.skipIfNextIs('-')
        val days = readStringAsInt(buffer)
        LocalDate.of(year, month, days)
    }

    final def writeLocalDateAsString(buffer: Buffer, localDate: LocalDate): Unit = {
        val ds = BufferConstants.digits
        writeYearAsString(buffer, localDate.getYear)
        val d1 = ds(localDate.getMonthValue) << 8
        val d2 = ds(localDate.getDayOfMonth) << 8
        buffer.writeUnsignedMediumLE(d1 | 0x00002d) // -xx
        buffer.writeUnsignedMediumLE(d2 | 0x00002d) // -xx
    }

    final def writeLocalTimeAsString(buffer: Buffer, localTime: LocalTime): Unit = {
        val ds     = BufferConstants.digits
        val second = localTime.getSecond
        val nano   = localTime.getNano
        val d1     = ds(localTime.getHour) | 0x3a00003a0000L
        val d2     = ds(localTime.getMinute).toLong << 24
        if ((second | nano) == 0) {
            buffer.writeLongLE(d1 | d2)
            buffer.writerOffset(buffer.writerOffset - 3)
        } else {
            val d3 = ds(second).toLong << 48
            buffer.writeLongLE(d1 | d2 | d3)
            if (nano != 0) writeNanos(buffer, nano, ds)
        }
    }

    final def readStringAsLocalTime(buffer: Buffer): LocalTime = {
        var minute = 0
        var second = 0
        var nano   = 0
        val hour   = readStringAsInt(buffer)
        if (buffer.skipIfNextIs(':')) {
            minute = readStringAsInt(buffer)
            if (buffer.skipIfNextIs(':')) {
                second = readStringAsInt(buffer)
                if (buffer.skipIfNextIs('.')) {
                    var count = 0
                    while (buffer.readableBytes > 0 && buffer.nextInRange('0', '9')) {
                        nano = nano * 10 + (buffer.readByte - '0')
                        count += 1
                    }
                    if (count < 9)
                        nano = nano * Math.pow(10, 9 - count).toInt
                }
            }
        }
        LocalTime.of(hour, minute, second, nano)
    }

    private def writeNanos(buffer: Buffer, q0: Long, ds: Array[Short]): Unit = {
        val y1 =
            q0 * 1441151881 // Based on James Anhalt's algorithm for 9 digits: https://jk-jeon.github.io/posts/2022/02/jeaiii-algorithm/
        val y2 = (y1 & 0x1ffffffffffffffL) * 100
        var m  = y1 >>> 57 << 8 | ds((y2 >>> 57).toInt) << 16 | 0x302e
        if ((y2 & 0x1fffff800000000L) == 0) {
            buffer.writeIntLE(m.toInt)
        } else {
            val y3 = (y2 & 0x1ffffffffffffffL) * 100
            val y4 = (y3 & 0x1ffffffffffffffL) * 100
            m |= ds((y3 >>> 57).toInt).toLong << 32
            val d = ds((y4 >>> 57).toInt)
            buffer.writeLongLE(m | d.toLong << 48)
            if ((y4 & 0x1ff000000000000L) == 0 && d <= 0x3039) buffer.writerOffset(buffer.writerOffset - 1)
            else
                buffer.writeShortLE(ds(((y4 & 0x1ffffffffffffffL) * 100 >>> 57).toInt))
        }
    }

    final def writeLocalDateTimeAsString(buffer: Buffer, localDateTime: LocalDateTime): Unit = {
        writeLocalDateAsString(buffer, localDateTime.toLocalDate)
        buffer.writeByte('T')
        writeLocalTimeAsString(buffer, localDateTime.toLocalTime)
    }

    final def readStringAsLocalDateTime(buffer: Buffer): LocalDateTime = {
        val year = readStringAsIntYear(buffer)
        buffer.skipIfNextIs('-')
        val month = readStringAsInt(buffer)
        buffer.skipIfNextIs('-')
        val days = readStringAsInt(buffer)

        assert(buffer.skipIfNextIs('T'), s"except 'T' but got ${buffer.readByte.toChar}")

        var minute = 0
        var second = 0
        var nano   = 0
        val hour   = readStringAsInt(buffer)
        if (buffer.skipIfNextIs(':')) {
            minute = readStringAsInt(buffer)
            if (buffer.skipIfNextIs(':')) {
                second = readStringAsInt(buffer)
                if (buffer.skipIfNextIs('.')) {
                    var count = 0
                    while (buffer.readableBytes > 0 && buffer.nextInRange('0', '9')) {
                        nano = nano * 10 + (buffer.readByte - '0')
                        count += 1
                    }
                    if (count < 9)
                        nano = nano * Math.pow(10, 9 - count).toInt
                }
            }
        }

        LocalDateTime.of(year, month, days, hour, minute, second, nano)
    }

    final def readStringAsMonthDay(buffer: Buffer): MonthDay = {
        val ps       = buffer.readUnsignedMediumLE.toLong | (buffer.readUnsignedIntLE << 24)
        val bs       = ps - 0x30302d30302d2dL
        val monthDay = (bs * 2561 >> 24).toInt
        val month    = monthDay.toByte.toInt
        val day      = monthDay >> 24
        MonthDay.of(month, day)
    }

    final def writeMonthDayAsString(buffer: Buffer, monthDay: MonthDay): Unit = { // "--01-01"
        val ds = BufferConstants.digits
        val d1 = ds(monthDay.getMonthValue) << 16
        val d2 = ds(monthDay.getDayOfMonth).toLong << 40
        buffer.writeLongLE(d1 | d2 | 0x00002d00002d2dL)
        buffer.writerOffset(buffer.writerOffset - 1)
    }

    final def writeOffsetDateTimeAsString(buffer: Buffer, offsetDateTime: OffsetDateTime): Unit = {
        val ds = BufferConstants.digits
        writeLocalDateTimeAsString(buffer, offsetDateTime.toLocalDateTime)
        writeZoneOffset(buffer, offsetDateTime.getOffset)
    }

    final def writeOffsetTimeAsString(buffer: Buffer, offsetTime: OffsetTime): Unit = {
        val ds = BufferConstants.digits
        writeLocalTimeAsString(buffer, offsetTime.toLocalTime)
        writeZoneOffset(buffer, offsetTime.getOffset)
    }

    final def writeZoneOffset(buffer: Buffer, zoneOffset: ZoneOffset): Unit = {
        val ds = BufferConstants.digits
        var y  = zoneOffset.getTotalSeconds
        if (y == 0) {
            buffer.writeByte(0x5a)
        } else {
            var m = 0x30303a00002bL
            if (y < 0) {
                y = -y
                m = 0x30303a00002dL
            }
            y *= 37283 // Based on James Anhalt's algorithm: https://jk-jeon.github.io/posts/2022/02/jeaiii-algorithm/
            m |= ds(y >>> 27) << 8
            if ((y & 0x7ff8000) == 0) {
                buffer.writeLongLE(m)
                buffer.writerOffset(buffer.writerOffset - 2)
            } else {
                y = (y & 0x7ffffff) * 15
                buffer.writeLongLE(ds(y >> 25).toLong << 32 | m)
                if ((y & 0x1f80000) == 0)
                    buffer.writerOffset(buffer.writerOffset - 2) // check if totalSeconds is divisible by 60
                else {
                    buffer.writerOffset(buffer.writerOffset - 2)
                    buffer.writeMediumLE(ds((y & 0x1ffffff) * 15 >> 23) << 8 | 0x00003a)
                }
            }
        }
    }

    final def readStringAsOffsetDateTime(buffer: Buffer): OffsetDateTime = {
        val localDateTime = readStringAsLocalDateTime(buffer)
        val offset        = readStringAsZoneOffset(buffer)
        OffsetDateTime.of(localDateTime, offset)
    }

    final def readStringAsZoneOffset(buffer: Buffer): ZoneOffset = if (buffer.skipIfNextIs('Z')) ZoneOffset.UTC
    else {
        val b = buffer.readByte
        val isNeg =
            if (b == '-') true
            else if (b == '+') false
            else throw AssertionError(s"except '-' or '-' but got ${b.toChar}")
        var minutes = 0
        var seconds = 0

        val hours = readStringAsInt(buffer)
        if (buffer.skipIfNextIs(':')) {
            minutes = readStringAsInt(buffer)
            if (buffer.skipIfNextIs(':')) seconds = readStringAsInt(buffer)
        }
        if (isNeg) ZoneOffset.ofHoursMinutesSeconds(-hours, -minutes, -seconds)
        else ZoneOffset.ofHoursMinutesSeconds(hours, minutes, seconds)
    }

    final def readStringAsOffsetTime(buffer: Buffer): OffsetTime = {
        val localTime  = readStringAsLocalTime(buffer)
        val zoneOffset = readStringAsZoneOffset(buffer)
        OffsetTime.of(localTime, zoneOffset)
    }

    final def writeZoneIdAsString(buffer: Buffer, zoneId: ZoneId): Unit =
        buffer.writeBytes(BufferConstants.zoneIdMap(zoneId))

    final def readStringAsZoneId(buffer: Buffer): ZoneId = {
        var i            = 0
        var break        = false
        var zone: ZoneId = null
        while (i < BufferConstants.allZoneIdSort.length && !break) {
            val tp     = BufferConstants.allZoneIdSort(i)
            val zoneId = tp._1
            val bytes  = tp._2
            if (buffer.readableBytes >= bytes.length && buffer.nextIs(bytes(0)) && buffer.skipIfNextAre(bytes)) {
                zone = zoneId
                break = true
            }
            i += 1
        }
        zone
    }

    final def writeZonedDateTime(buffer: Buffer, zonedDateTime: ZonedDateTime): Unit = {
        writeLocalDateTimeAsString(buffer, zonedDateTime.toLocalDateTime)
        writeZoneOffset(buffer, zonedDateTime.getOffset)
        val zone = zonedDateTime.getZone
        if (!zone.isInstanceOf[ZoneOffset]) {
            buffer.writeByte('[')
            writeZoneIdAsString(buffer, zone)
            buffer.writeByte(']')
        }
    }

    final def readStringAsZonedDateTime(buffer: Buffer): ZonedDateTime = {
        val localDateTime = readStringAsLocalDateTime(buffer)
        val offset        = readStringAsZoneOffset(buffer)
        if (buffer.skipIfNextIs('[')) {
            val zoneId = readStringAsZoneId(buffer)
            assert(buffer.skipIfNextIs(']'), s"except ']' but got '${buffer.readByte.toChar}'")
            ZonedDateTime.ofInstant(localDateTime, offset, zoneId)
        } else ZonedDateTime.of(localDateTime, offset)
    }

    final def readStringAsPeriod(buffer: Buffer): Period = {
        assert(buffer.skipIfNextIs('P'), s"except 'P' but got ${buffer.readByte.toChar}")
        var year  = 0
        var month = 0
        var days  = 0

        var cont = true
        while (cont && buffer.readableBytes > 0) {
            val int = readStringAsInt(buffer)
            if (buffer.skipIfNextIs('Y')) year = int
            else if (buffer.skipIfNextIs('M')) month = int
            else if (buffer.skipIfNextIs('D')) {
                days = int
                cont = false
            } else assert(false, s"except 'D' but got ${buffer.readByte.toChar}")
        }

        Period.of(year, month, days)
    }

    final def writePeriodAsString(buffer: Buffer, period: Period): Unit = {
        val years  = period.getYears
        val months = period.getMonths
        val days   = period.getDays
        if ((years | months | days) == 0) buffer.writeMediumLE(0x443050)
        else {
            val ds = BufferConstants.digits
            buffer.writeByte('P')
            if (years != 0) {
                writeIntAsString(buffer, years)
                buffer.writeByte('Y')
            }
            if (months != 0) {
                writeIntAsString(buffer, months)
                buffer.writeByte('M')
            }
            if (days != 0) {
                writeIntAsString(buffer, days)
                buffer.writeByte('D')
            }
        }
    }

    def writeJDurationAsString(buffer: Buffer, duration: JDuration): Unit = {
        val totalSecs = duration.getSeconds
        var nano      = duration.getNano
        if ((totalSecs | nano) != 0) {
            buffer.writeShortLE(0x5450) // PT
            val effectiveTotalSecs = if (totalSecs < 0) (-nano >> 31) - totalSecs else totalSecs
            val hours =
                Math.multiplyHigh(effectiveTotalSecs >> 4, 655884233731895169L) >> 3 // divide a positive long by 3600
            val secsOfHour = (effectiveTotalSecs - hours * 3600).toInt
            val minutes    = secsOfHour * 17477 >> 20 // divide a small positive int by 60
            val seconds    = secsOfHour - minutes * 60
            val ds         = BufferConstants.digits
            if (hours != 0) {
                if (totalSecs < 0) buffer.writeByte('-')
                if (hours < 100000000) {
                    buffer.writerOffset(buffer.writerOffset + digitCount(hours))
                    writePositiveIntDigits(hours.toInt, buffer.writerOffset, buffer, ds)
                } else {
                    val q1 =
                        Math.multiplyHigh(hours, 6189700196426901375L) >>> 25 // divide a positive long by 100000000
                    buffer.writerOffset(buffer.writerOffset + digitCount(q1))
                    writePositiveIntDigits(q1.toInt, buffer.writerOffset, buffer, ds)
                    write8Digits(buffer, hours - q1 * 100000000, ds)
                }
                buffer.writeByte('H')
            }
            if (minutes != 0) {
                if (totalSecs < 0) buffer.writeByte('-')
                if (minutes < 10) buffer.writeByte((minutes + '0').toByte) else write2Digits(buffer, minutes, ds)
                buffer.writeByte('M')
            }
            if ((seconds | nano) != 0) {
                if (totalSecs < 0) buffer.writeByte('-')
                if (seconds < 10) buffer.writeByte((seconds + '0').toByte) else write2Digits(buffer, seconds, ds)
                if (nano != 0) {
                    if (totalSecs < 0) nano = 1000000000 - nano
                    val dotPos = buffer.writerOffset
                    writeSignificantFractionDigits(nano, dotPos + 9, dotPos, buffer, ds)
                    buffer.setByte(dotPos, '.')
                }
                buffer.writeByte('S')
            }
        } else buffer.writeUnsignedIntLE(0x53305450L) // PT0S
    }

    /** Obtains a [[java.time.Duration]] from a text string such as `PnDTnHnMn.nS`.
     *
     *  This will parse a textual representation of a duration, like the string produced by `toString`. The formats
     *  accepted are based on the ISO-8601 duration format `PnDTnHnMn.nS` with days considered to be exactly 24 hours.
     *
     *  The string starts with an optional sign, denoted by the ASCII negative or positive symbol. If negative, the
     *  whole period is negated. The ASCII letter "P" is next in upper or lower case. There are then four sections, each
     *  consisting of a number and a suffix. The sections have suffixes in ASCII of "D", "H", "M" and "S" for days,
     *  hours, minutes and seconds, accepted in upper or lower case. The suffixes must occur in order. The ASCII letter
     *  "T" must occur before the first occurrence, if any, of an hour, minute or second section. At least one of the
     *  four sections must be present, and if "T" is present there must be at least one section after the "T". The
     *  number part of each section must consist of one or more ASCII digits. The number may be prefixed by the ASCII
     *  negative or positive symbol. The number of days, hours and minutes must parse to a `long`. The number of seconds
     *  must parse to a `long` with optional fraction. The decimal point may be either a dot or a comma. The fractional
     *  part may have from zero to 9 digits.
     *
     *  The leading plus/minus sign, and negative values for other units are not part of the ISO-8601 standard.
     *
     *  Examples:
     *  {{{
     *    "PT20.345S" -- parses as "20.345 seconds"
     *    "PT15M"     -- parses as "15 minutes" (where a minute is 60 seconds)
     *    "PT10H"     -- parses as "10 hours" (where an hour is 3600 seconds)
     *    "P2D"       -- parses as "2 days" (where a day is 24 hours or 86400 seconds)
     *    "P2DT3H4M"  -- parses as "2 days, 3 hours and 4 minutes"
     *    "PT-6H3M"    -- parses as "-6 hours and +3 minutes"
     *    "-PT6H3M"    -- parses as "-6 hours and -3 minutes"
     *    "-PT-6H+3M"  -- parses as "+6 hours and -3 minutes"
     *  }}}
     *
     *  @param buffer
     *    the [[Buffer]] to read.
     *  @return
     *    the parsed duration, not null
     *  @throws DateTimeParseException
     *    if the text cannot be parsed to a duration
     */
    final def readStringAsJDuration(buffer: Buffer): JDuration = {
        var b = buffer.readByte
        var s = 0L
        if (b == '-') {
            b = buffer.readByte
            s = ~s
        } else if (b == '+') b = buffer.readByte
        if (b != 'P') throw new DateTimeParseException(s"except 'p' but got ${b.toChar}", "", buffer.readerOffset)
        b = buffer.readByte
        var state = -1
        if (b == 'T') {
            b = buffer.readByte
            state = 0
        }
        var seconds = 0L
        var nano    = 0

        while ({
            var sx = s
            if (b == '-') {
                b = buffer.readByte
                sx = ~sx
            } else if (b == '+') b = buffer.readByte
            if (b < '0' || b > '9')
                throw new DateTimeParseException(s"except digit but got ${b.toChar}", "", buffer.readerOffset)

            var x = ('0' - b).toLong
            while (buffer.readableBytes > 0 && buffer.nextInRange('0', '9')) {
                if (
                  x < -922337203685477580L || {
                      x = x * 10 + ('0' - buffer.readByte)
                      x > 0
                  }
                ) throw new DateTimeParseException("illegal duration", "", buffer.readerOffset)
            }

            b = buffer.readByte

            if (b == 'D' && state < 0) {
                seconds = (sx - (x ^ sx)) * 86400
                state = 0
                buffer.skipIfNextIs('T') && buffer.readableBytes > 0
            } else if (b == 'H' && state <= 0) {
                seconds = (sx - (x ^ sx)) * 3600 + seconds
                state = 1
                buffer.readableBytes > 0 && (buffer.nextInRange('0', '9') || buffer.nextIs('-') || buffer.nextIs('+'))
            } else if (b == 'M' && state <= 1) {
                seconds = (sx - (x ^ sx)) * 60 + seconds
                state = 2
                buffer.readableBytes > 0 && (buffer.nextInRange('0', '9') || buffer.nextIs('-') || buffer.nextIs('+'))
            } else if (b == 'S' || b == '.') {
                seconds = sx - (x ^ sx) + seconds
                state = 3
                if (b == '.') {
                    var count = 0
                    while (buffer.readableBytes > 0 && buffer.nextInRange('0', '9')) {
                        nano = nano * 10 + (buffer.readByte - '0')
                        count += 1
                    }
                    if (count < 9) nano = nano * Math.pow(10, 9 - count).toInt

                    if (sx != 0) nano = -nano

                    if (buffer.skipIfNextIs('S')) {
                        false
                    } else {
                        val msg = s"except 'S' but got ${buffer.readByte.toChar}"
                        throw new DateTimeParseException(msg, "", buffer.readerOffset)
                    }
                } else false
            } else throw new DateTimeParseException("", "", buffer.readerOffset)
        }) b = buffer.readByte

        JDuration.ofSeconds(seconds, nano.toLong)
    }

    final def writeDurationAsString(buffer: Buffer, duration: Duration): Unit = {
        if (duration eq Duration.Undefined) buffer.writeBytes(DurationConstants.Undefined)
        else if (duration == Duration.Inf) buffer.writeBytes(DurationConstants.Inf)
        else if (duration == Duration.MinusInf) buffer.writeBytes(DurationConstants.MinusInf)
        else {
            writeLongAsString(buffer, duration.length)
            buffer.writeByte(' ')
            duration.unit match
                case DAYS         => buffer.writeBytes(DurationConstants.DAY_BYTES)
                case HOURS        => buffer.writeBytes(DurationConstants.HOUR_BYTES)
                case MINUTES      => buffer.writeBytes(DurationConstants.MINUTE_BYTES)
                case SECONDS      => buffer.writeBytes(DurationConstants.SECOND_BYTES)
                case MILLISECONDS => buffer.writeBytes(DurationConstants.MILLISECOND_BYTES)
                case MICROSECONDS => buffer.writeBytes(DurationConstants.MICROSECOND_BYTES)
                case NANOSECONDS  => buffer.writeBytes(DurationConstants.NANOSECOND_BYTES)
            if (duration.length != 1) buffer.writeByte('s')
        }
    }

    /** Parse String into Duration. Format is `"<length><unit>"`, where whitespace is allowed before, between and after
     *  the parts. Infinities are designated by `"Inf"`, `"PlusInf"`, `"+Inf"`, `"Duration.Inf"` and `"-Inf"`,
     *  `"MinusInf"` or `"Duration.MinusInf"`. Undefined is designated by `"Duration.Undefined"`.
     *
     *  @throws NumberFormatException
     *    if format is not parsable
     */
    final def readStringAsDuration(buffer: Buffer): Duration = {
        while (buffer.skipIfNextIs(' ')) {}

        if (buffer.nextInRange('0', '9')) { // parse FiniteDuration
            val length = readStringAsLong(buffer)
            while (buffer.skipIfNextIs(' ')) {}
            val timeUnit =
                if (buffer.skipIfNextAre(DurationConstants.DAY_BYTES)) DAYS
                else if (buffer.skipIfNextAre(DurationConstants.HOUR_BYTES)) HOURS
                else if (buffer.skipIfNextAre(DurationConstants.MINUTE_BYTES)) MINUTES
                else if (buffer.skipIfNextAre(DurationConstants.SECOND_BYTES)) SECONDS
                else if (buffer.skipIfNextAre(DurationConstants.MILLISECOND_BYTES)) MILLISECONDS
                else if (buffer.skipIfNextAre(DurationConstants.MICROSECOND_BYTES)) MICROSECONDS
                else if (buffer.skipIfNextAre(DurationConstants.NANOSECOND_BYTES)) NANOSECONDS
                else throw new NumberFormatException(s"Duration format error at buffer index ${buffer.readerOffset}")

            if (length != 1) buffer.skipIfNextIs('s')
            Duration(length, timeUnit)
        } else { // parse Infinite Duration
            if (buffer.skipIfNextAre(DurationConstants.Undefined)) Duration.Undefined
            else if (buffer.skipIfNextAre(DurationConstants.Inf)) Duration.Inf
            else if (buffer.skipIfNextAre(DurationConstants.MinusInf)) Duration.MinusInf
            else if (DurationConstants.InfArray.exists(bts => buffer.skipIfNextAre(bts))) Duration.Inf
            else if (DurationConstants.MinusInfArray.exists(bts => buffer.skipIfNextAre(bts))) Duration.MinusInf
            else throw new NumberFormatException(s"Duration format error at buffer index ${buffer.readerOffset}")
        }
    }

}
