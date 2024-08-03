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
import cc.otavia.datatype.Money

import java.math.{BigInteger, BigDecimal as JBigDecimal}
import java.time.{Duration as JDuration, *}
import java.util.{Currency, Locale, UUID}
import scala.concurrent.duration.Duration

/** serialize/deserialize base types helper trait */
trait SerdeOps {
    this: Serde[?] =>

    //// primary types ops

    // serialize ops

    /** Serialize [[Byte]] value to [[Buffer]].
     *  @param byte
     *    Value.
     *  @param out
     *    Output [[Buffer]].
     *  @return
     *    This [[Serde]] instance.
     */
    protected def serializeByte(byte: Byte, out: Buffer): this.type

    /** Serialize [[Boolean]] value to [[Buffer]].
     *  @param boolean
     *    Value.
     *  @param out
     *    Output [[Buffer]].
     *  @return
     *    This [[Serde]] instance.
     */
    protected def serializeBoolean(boolean: Boolean, out: Buffer): this.type

    /** Serialize [[Char]] value to [[Buffer]].
     *  @param char
     *    Value.
     *  @param out
     *    Output [[Buffer]].
     *  @return
     *    This [[Serde]] instance.
     */
    protected def serializeChar(char: Char, out: Buffer): this.type

    /** Serialize [[Short]] value to [[Buffer]].
     *  @param short
     *    Value.
     *  @param out
     *    Output [[Buffer]].
     *  @return
     *    This [[Serde]] instance.
     */
    protected def serializeShort(short: Short, out: Buffer): this.type

    /** Serialize [[Int]] value to [[Buffer]].
     *  @param int
     *    Value.
     *  @param out
     *    Output [[Buffer]].
     *  @return
     *    This [[Serde]] instance.
     */
    protected def serializeInt(int: Int, out: Buffer): this.type

    /** Serialize [[Long]] value to [[Buffer]].
     *  @param long
     *    Value.
     *  @param out
     *    Output [[Buffer]].
     *  @return
     *    This [[Serde]] instance.
     */
    protected def serializeLong(long: Long, out: Buffer): this.type

    /** Serialize [[Float]] value to [[Buffer]].
     *  @param float
     *    Value.
     *  @param out
     *    Output [[Buffer]].
     *  @return
     *    This [[Serde]] instance.
     */
    protected def serializeFloat(float: Float, out: Buffer): this.type

    /** Serialize [[Double]] value to [[Buffer]].
     *  @param double
     *    Value.
     *  @param out
     *    Output [[Buffer]].
     *  @return
     *    This [[Serde]] instance.
     */
    protected def serializeDouble(double: Double, out: Buffer): this.type

    /** Serialize [[String]] value to [[Buffer]].
     *  @param string
     *    Value.
     *  @param out
     *    Output [[Buffer]].
     *  @return
     *    This [[Serde]] instance.
     */
    protected def serializeString(string: String, out: Buffer): this.type

    // deserialize ops

    /** Deserialize [[Byte]] value from [[Buffer]].
     *  @param in
     *    Input [[Buffer]]
     *  @return
     *    [[Byte]] value.
     */
    protected def deserializeByte(in: Buffer): Byte

    /** Deserialize [[Boolean]] value from [[Buffer]].
     *  @param in
     *    Input [[Buffer]]
     *  @return
     *    [[Boolean]] value.
     */
    protected def deserializeBoolean(in: Buffer): Boolean

    /** Deserialize [[Char]] value from [[Buffer]].
     *
     *  @param in
     *    Input [[Buffer]]
     *  @return
     *    [[Char]] value.
     */
    protected def deserializeChar(in: Buffer): Char

    /** Deserialize [[Short]] value from [[Buffer]].
     *
     *  @param in
     *    Input [[Buffer]]
     *  @return
     *    [[Short]] value.
     */
    protected def deserializeShort(in: Buffer): Short

    /** Deserialize [[Int]] value from [[Buffer]].
     *
     *  @param in
     *    Input [[Buffer]]
     *  @return
     *    [[Int]] value.
     */
    protected def deserializeInt(in: Buffer): Int

    /** Deserialize [[Long]] value from [[Buffer]].
     *
     *  @param in
     *    Input [[Buffer]]
     *  @return
     *    [[Long]] value.
     */
    protected def deserializeLong(in: Buffer): Long

    /** Deserialize [[Float]] value from [[Buffer]].
     *
     *  @param in
     *    Input [[Buffer]]
     *  @return
     *    [[Float]] value.
     */
    protected def deserializeFloat(in: Buffer): Float

    /** Deserialize [[Double]] value from [[Buffer]].
     *
     *  @param in
     *    Input [[Buffer]]
     *  @return
     *    [[Double]] value.
     */
    protected def deserializeDouble(in: Buffer): Double

    /** Deserialize [[String]] value from [[Buffer]].
     *
     *  @param in
     *    Input [[Buffer]]
     *  @return
     *    [[String]] value.
     */
    protected def deserializeString(in: Buffer): String

    //// math type ops

    // serialize ops

    /** Serialize [[BigInt]] value to [[Buffer]].
     *
     *  @param bigInt
     *    Value.
     *  @param out
     *    Output [[Buffer]].
     *  @return
     *    This [[Serde]] instance.
     */
    protected def serializeBigInt(bigInt: BigInt, out: Buffer): this.type

    /** Serialize [[BigDecimal]] value to [[Buffer]].
     *
     *  @param bigDecimal
     *    Value.
     *  @param out
     *    Output [[Buffer]].
     *  @return
     *    This [[Serde]] instance.
     */
    protected def serializeBigDecimal(bigDecimal: BigDecimal, out: Buffer): this.type

    /** Serialize [[BigInteger]] value to [[Buffer]].
     *
     *  @param bigInteger
     *    Value.
     *  @param out
     *    Output [[Buffer]].
     *  @return
     *    This [[Serde]] instance.
     */
    protected def serializeBigInteger(bigInteger: BigInteger, out: Buffer): this.type

    /** Serialize [[JBigDecimal]] value to [[Buffer]].
     *
     *  @param bigDecimal
     *    Value.
     *  @param out
     *    Output [[Buffer]].
     *  @return
     *    This [[Serde]] instance.
     */
    protected def serializeJBigDecimal(bigDecimal: JBigDecimal, out: Buffer): this.type

    // deserialize ops

    /** Deserialize [[BigInt]] value from [[Buffer]].
     *
     *  @param in
     *    Input [[Buffer]]
     *  @return
     *    [[BigInt]] value.
     */
    protected def deserializeBigInt(in: Buffer): BigInt

    /** Deserialize [[BigDecimal]] value from [[Buffer]].
     *
     *  @param in
     *    Input [[Buffer]]
     *  @return
     *    [[BigDecimal]] value.
     */
    protected def deserializeBigDecimal(in: Buffer): BigDecimal

    /** Deserialize [[BigInteger]] value from [[Buffer]].
     *
     *  @param in
     *    Input [[Buffer]]
     *  @return
     *    [[BigInteger]] value.
     */
    protected def deserializeBigInteger(in: Buffer): BigInteger

    /** Deserialize [[JBigDecimal]] value from [[Buffer]].
     *
     *  @param in
     *    Input [[Buffer]]
     *  @return
     *    [[JBigDecimal]] value.
     */
    protected def deserializeJBigDecimal(in: Buffer): JBigDecimal

    //// time type ops

    // serialize ops

    /** Serialize [[JDuration]] value to [[Buffer]].
     *
     *  @param duration
     *    Value.
     *  @param out
     *    Output [[Buffer]].
     *  @return
     *    This [[Serde]] instance.
     */
    protected def serializeJDuration(duration: JDuration, out: Buffer): this.type

    /** Serialize [[Duration]] value to [[Buffer]].
     *
     *  @param duration
     *    Value.
     *  @param out
     *    Output [[Buffer]].
     *  @return
     *    This [[Serde]] instance.
     */
    protected def serializeDuration(duration: Duration, out: Buffer): this.type

    /** Serialize [[Instant]] value to [[Buffer]].
     *
     *  @param instant
     *    Value.
     *  @param out
     *    Output [[Buffer]].
     *  @return
     *    This [[Serde]] instance.
     */
    protected def serializeInstant(instant: Instant, out: Buffer): this.type

    /** Serialize [[LocalDate]] value to [[Buffer]].
     *
     *  @param localDate
     *    Value.
     *  @param out
     *    Output [[Buffer]].
     *  @return
     *    This [[Serde]] instance.
     */
    protected def serializeLocalDate(localDate: LocalDate, out: Buffer): this.type

    /** Serialize [[LocalDateTime]] value to [[Buffer]].
     *
     *  @param localDateTime
     *    Value.
     *  @param out
     *    Output [[Buffer]].
     *  @return
     *    This [[Serde]] instance.
     */
    protected def serializeLocalDateTime(localDateTime: LocalDateTime, out: Buffer): this.type

    /** Serialize [[LocalTime]] value to [[Buffer]].
     *
     *  @param localTime
     *    Value.
     *  @param out
     *    Output [[Buffer]].
     *  @return
     *    This [[Serde]] instance.
     */
    protected def serializeLocalTime(localTime: LocalTime, out: Buffer): this.type

    /** Serialize [[MonthDay]] value to [[Buffer]].
     *
     *  @param monthDay
     *    Value.
     *  @param out
     *    Output [[Buffer]].
     *  @return
     *    This [[Serde]] instance.
     */
    protected def serializeMonthDay(monthDay: MonthDay, out: Buffer): this.type

    /** Serialize [[OffsetDateTime]] value to [[Buffer]].
     *
     *  @param offsetDateTime
     *    Value.
     *  @param out
     *    Output [[Buffer]].
     *  @return
     *    This [[Serde]] instance.
     */
    protected def serializeOffsetDateTime(offsetDateTime: OffsetDateTime, out: Buffer): this.type

    /** Serialize [[OffsetTime]] value to [[Buffer]].
     *
     *  @param offsetTime
     *    Value.
     *  @param out
     *    Output [[Buffer]].
     *  @return
     *    This [[Serde]] instance.
     */
    protected def serializeOffsetTime(offsetTime: OffsetTime, out: Buffer): this.type

    /** Serialize [[Period]] value to [[Buffer]].
     *
     *  @param period
     *    Value.
     *  @param out
     *    Output [[Buffer]].
     *  @return
     *    This [[Serde]] instance.
     */
    protected def serializePeriod(period: Period, out: Buffer): this.type

    /** Serialize [[Year]] value to [[Buffer]].
     *
     *  @param year
     *    Value.
     *  @param out
     *    Output [[Buffer]].
     *  @return
     *    This [[Serde]] instance.
     */
    protected def serializeYear(year: Year, out: Buffer): this.type

    /** Serialize [[YearMonth]] value to [[Buffer]].
     *
     *  @param yearMonth
     *    Value.
     *  @param out
     *    Output [[Buffer]].
     *  @return
     *    This [[Serde]] instance.
     */
    protected def serializeYearMonth(yearMonth: YearMonth, out: Buffer): this.type

    /** Serialize [[ZonedDateTime]] value to [[Buffer]].
     *
     *  @param zonedDateTime
     *    Value.
     *  @param out
     *    Output [[Buffer]].
     *  @return
     *    This [[Serde]] instance.
     */
    protected def serializeZonedDateTime(zonedDateTime: ZonedDateTime, out: Buffer): this.type

    /** Serialize [[ZoneId]] value to [[Buffer]].
     *
     *  @param zoneId
     *    Value.
     *  @param out
     *    Output [[Buffer]].
     *  @return
     *    This [[Serde]] instance.
     */
    protected def serializeZoneId(zoneId: ZoneId, out: Buffer): this.type

    /** Serialize [[ZoneOffset]] value to [[Buffer]].
     *
     *  @param zoneOffset
     *    Value.
     *  @param out
     *    Output [[Buffer]].
     *  @return
     *    This [[Serde]] instance.
     */
    protected def serializeZoneOffset(zoneOffset: ZoneOffset, out: Buffer): this.type

    // deserialize

    /** Deserialize [[JDuration]] value from [[Buffer]].
     *
     *  @param in
     *    Input [[Buffer]]
     *  @return
     *    [[JDuration]] value.
     */
    protected def deserializeJDuration(in: Buffer): JDuration

    /** Deserialize [[Duration]] value from [[Buffer]].
     *
     *  @param in
     *    Input [[Buffer]]
     *  @return
     *    [[Duration]] value.
     */
    protected def deserializeDuration(in: Buffer): Duration

    /** Deserialize [[Instant]] value from [[Buffer]].
     *
     *  @param in
     *    Input [[Buffer]]
     *  @return
     *    [[Instant]] value.
     */
    protected def deserializeInstant(in: Buffer): Instant

    /** Deserialize [[LocalDate]] value from [[Buffer]].
     *
     *  @param in
     *    Input [[Buffer]]
     *  @return
     *    [[LocalDate]] value.
     */
    protected def deserializeLocalDate(in: Buffer): LocalDate

    /** Deserialize [[LocalDateTime]] value from [[Buffer]].
     *
     *  @param in
     *    Input [[Buffer]]
     *  @return
     *    [[LocalDateTime]] value.
     */
    protected def deserializeLocalDateTime(in: Buffer): LocalDateTime

    /** Deserialize [[LocalTime]] value from [[Buffer]].
     *
     *  @param in
     *    Input [[Buffer]]
     *  @return
     *    [[LocalTime]] value.
     */
    protected def deserializeLocalTime(in: Buffer): LocalTime

    /** Deserialize [[MonthDay]] value from [[Buffer]].
     *
     *  @param in
     *    Input [[Buffer]]
     *  @return
     *    [[MonthDay]] value.
     */
    protected def deserializeMonthDay(in: Buffer): MonthDay

    /** Deserialize [[OffsetDateTime]] value from [[Buffer]].
     *
     *  @param in
     *    Input [[Buffer]]
     *  @return
     *    [[OffsetDateTime]] value.
     */
    protected def deserializeOffsetDateTime(in: Buffer): OffsetDateTime

    /** Deserialize [[OffsetTime]] value from [[Buffer]].
     *
     *  @param in
     *    Input [[Buffer]]
     *  @return
     *    [[OffsetTime]] value.
     */
    protected def deserializeOffsetTime(in: Buffer): OffsetTime

    /** Deserialize [[Period]] value from [[Buffer]].
     *
     *  @param in
     *    Input [[Buffer]]
     *  @return
     *    [[Period]] value.
     */
    protected def deserializePeriod(in: Buffer): Period

    /** Deserialize [[Year]] value from [[Buffer]].
     *
     *  @param in
     *    Input [[Buffer]]
     *  @return
     *    [[Year]] value.
     */
    protected def deserializeYear(in: Buffer): Year

    /** Deserialize [[YearMonth]] value from [[Buffer]].
     *
     *  @param in
     *    Input [[Buffer]]
     *  @return
     *    [[YearMonth]] value.
     */
    protected def deserializeYearMonth(in: Buffer): YearMonth

    /** Deserialize [[ZonedDateTime]] value from [[Buffer]].
     *
     *  @param in
     *    Input [[Buffer]]
     *  @return
     *    [[ZonedDateTime]] value.
     */
    protected def deserializeZonedDateTime(in: Buffer): ZonedDateTime

    /** Deserialize [[ZoneId]] value from [[Buffer]].
     *
     *  @param in
     *    Input [[Buffer]]
     *  @return
     *    [[ZoneId]] value.
     */
    protected def deserializeZoneId(in: Buffer): ZoneId

    /** Deserialize [[ZoneOffset]] value from [[Buffer]].
     *
     *  @param in
     *    Input [[Buffer]]
     *  @return
     *    [[ZoneOffset]] value.
     */
    protected def deserializeZoneOffset(in: Buffer): ZoneOffset

    //// util type ops

    // serialize ops

    /** Serialize [[UUID]] value to [[Buffer]].
     *
     *  @param uuid
     *    Value.
     *  @param out
     *    Output [[Buffer]].
     *  @return
     *    This [[Serde]] instance.
     */
    protected def serializeUUID(uuid: UUID, out: Buffer): this.type

    /** Deserialize [[UUID]] value from [[Buffer]].
     *
     *  @param in
     *    Input [[Buffer]]
     *  @return
     *    [[UUID]] value.
     */
    protected def deserializeUUID(in: Buffer): UUID

}
