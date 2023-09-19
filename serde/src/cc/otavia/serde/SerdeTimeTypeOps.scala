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

package cc.otavia.serde

import cc.otavia.buffer.Buffer

import java.time.{Instant, LocalDate, LocalDateTime, LocalTime, MonthDay, OffsetDateTime, OffsetTime, Period, Year, YearMonth, ZoneId, ZoneOffset, ZonedDateTime, Duration as JDuration}
import scala.concurrent.duration.Duration

trait SerdeTimeTypeOps {
    this: Serde[?] =>

    protected def serializeJDuration(duration: JDuration, out: Buffer): this.type

    protected def serializeDuration(duration: Duration, out: Buffer): this.type

    protected def serializeInstant(instant: Instant, out: Buffer): this.type

    protected def serializeLocalDate(localDate: LocalDate, out: Buffer): this.type

    protected def serializeLocalDateTime(localDateTime: LocalDateTime, out: Buffer): this.type

    protected def serializeLocalTime(localTime: LocalTime, out: Buffer): this.type

    protected def serializeMonthDay(monthDay: MonthDay, out: Buffer): this.type

    protected def serializeOffsetDateTime(offsetDateTime: OffsetDateTime, out: Buffer): this.type

    protected def serializeOffsetTime(offsetTime: OffsetTime, out: Buffer): this.type

    protected def serializePeriod(period: Period, out: Buffer): this.type

    protected def serializeYear(year: Year, out: Buffer): this.type

    protected def serializeYearMonth(yearMonth: YearMonth, out: Buffer): this.type

    protected def serializeZonedDateTime(zonedDateTime: ZonedDateTime, out: Buffer): this.type

    protected def serializeZoneId(zoneId: ZoneId, out: Buffer): this.type

    protected def serializeZoneOffset(zoneOffset: ZoneOffset, out: Buffer): this.type

    // deserialize

    protected def deserializeJDuration(in: Buffer): JDuration

    protected def deserializeDuration(in: Buffer): Duration

    protected def deserializeInstant(in: Buffer): Instant

    protected def deserializeLocalDate(in: Buffer): LocalDate

    protected def deserializeLocalDateTime(in: Buffer): LocalDateTime

    protected def deserializeLocalTime(in: Buffer): LocalTime

    protected def deserializeMonthDay(in: Buffer): MonthDay

    protected def deserializeOffsetDateTime(in: Buffer): OffsetDateTime

    protected def deserializeOffsetTime(in: Buffer): OffsetTime

    protected def deserializePeriod(in: Buffer): Period

    protected def deserializeYear(in: Buffer): Year

    protected def deserializeYearMonth(in: Buffer): YearMonth

    protected def deserializeZonedDateTime(in: Buffer): ZonedDateTime

    protected def deserializeZoneId(in: Buffer): ZoneId

    protected def deserializeZoneOffset(in: Buffer): ZoneOffset

}
