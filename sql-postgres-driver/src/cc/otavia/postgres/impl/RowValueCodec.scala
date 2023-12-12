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

import java.nio.charset.StandardCharsets
import java.time.*
import java.time.format.DateTimeFormatter.{ISO_LOCAL_DATE, ISO_LOCAL_TIME}
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoUnit
import scala.language.unsafeNulls

object RowValueCodec {

    def textDecodeBOOL(index: Int, len: Int, buffer: Buffer): Boolean = buffer.indexIs('t', index)

    def binaryDecodeBOOL(index: Int, len: Int, buffer: Buffer): Boolean = buffer.getBoolean(index)

    def textDecodeINT2(index: Int, len: Int, buffer: Buffer): Short = buffer.getStringAsLong(index, len).toShort

    def binaryDecodeINT2(index: Int, len: Int, buffer: Buffer): Short = buffer.getShort(index)

    def textDecodeINT4(index: Int, len: Int, buffer: Buffer): Int = buffer.getStringAsLong(index, len).toInt

    def binaryDecodeINT4(index: Int, len: Int, buffer: Buffer): Int = buffer.getInt(index)

    def textDecodeINT8(index: Int, len: Int, buffer: Buffer): Long = buffer.getStringAsLong(index, len)

    def binaryDecodeINT8(index: Int, len: Int, buffer: Buffer): Long = buffer.getLong(index)

    def textDecodeFLOAT4(index: Int, len: Int, buffer: Buffer): Float = buffer.getStringAsDouble(index, len).toFloat

    def binaryDecodeFLOAT4(index: Int, len: Int, buffer: Buffer): Float = buffer.getFloat(index)

    def textDecodeFLOAT8(index: Int, len: Int, buffer: Buffer): Double = buffer.getStringAsDouble(index, len)

    def binaryDecodeFLOAT8(index: Int, len: Int, buffer: Buffer): Double = buffer.getDouble(index)

    def textDecodeCHAR(index: Int, len: Int, buffer: Buffer): String = buffer.getCharSequence(index, len).toString

    def binaryDecodeCHAR(index: Int, len: Int, buffer: Buffer): String = buffer.getCharSequence(index, len).toString

    def textDecodeVARCHAR(index: Int, len: Int, buffer: Buffer): String = buffer.getCharSequence(index, len).toString

    def binaryDecodeVARCHAR(index: Int, len: Int, buffer: Buffer): String = buffer.getCharSequence(index, len).toString

    def textDecodeBPCHAR(index: Int, len: Int, buffer: Buffer): String = buffer.getCharSequence(index, len).toString

    def binaryDecodeBPCHAR(index: Int, len: Int, buffer: Buffer): String = buffer.getCharSequence(index, len).toString

    def textDecodeTEXT(index: Int, len: Int, buffer: Buffer): String = buffer.getCharSequence(index, len).toString

    def binaryDecodeTEXT(index: Int, len: Int, buffer: Buffer): String = buffer.getCharSequence(index, len).toString

    def textDecodeNAME(index: Int, len: Int, buffer: Buffer): String = buffer.getCharSequence(index, len).toString

    def binaryDecodeNAM(index: Int, len: Int, buffer: Buffer): String = buffer.getCharSequence(index, len).toString

    def textDecodeDATE(index: Int, len: Int, buffer: Buffer): LocalDate = {
        val cs = buffer.getCharSequence(index, len).toString
        cs match
            case "infinity"  => LocalDate.MAX
            case "-infinity" => LocalDate.MIN
            case _           => LocalDate.parse(cs)
    }

    def textDecodeTIME(index: Int, len: Int, buffer: Buffer): LocalTime = {
        val cs = buffer.getCharSequence(index, len).toString
        LocalTime.parse(cs)
    }

    def textDecodeTIMETZ(index: Int, len: Int, buffer: Buffer): OffsetTime = {
        val cs = buffer.getCharSequence(index, len).toString
        OffsetTime.parse(cs, TIMETZ_FORMAT)
    }

    def textDecodeTIMESTAMP(index: Int, len: Int, buffer: Buffer): LocalDateTime = {
        val cs = buffer.getCharSequence(index, len).toString
        cs match
            case "infinity"  => LocalDateTime.MAX
            case "-infinity" => LocalDateTime.MIN
            case _           => LocalDateTime.parse(cs, TIMESTAMP_FORMAT)
    }

    def binaryDecodeTIMESTAMP(index: Int, len: Int, buffer: Buffer): LocalDateTime = {
        val value = LOCAL_DATE_TIME_EPOCH.plus(buffer.getLong(index), ChronoUnit.MICROS)
        ???
    }

    def textDecodeBYTEA(index: Int, len: Int, buffer: Buffer): Array[Byte] = if (isHexFormat(index, len, buffer)) {
        // hex format
        // Shift 2 bytes: skip \x prolog
        val bytes = new Array[Byte](len - 2)
        var i     = 0
        while (i < len - 2) {
            val b0 = decodeHexChar(buffer.getByte(index + 2 + i))
            val b1 = decodeHexChar(buffer.getByte(index + 3 + i))
            bytes(i) = (b0 * 16 + b1).toByte
            i += 1
        }
        bytes
    } else {
        // escape format

        ???
    }

    def textDecodeTIMESTAMPTZ(index: Int, len: Int, buffer: Buffer): OffsetDateTime = {
        val cs = buffer.getCharSequence(index, len).toString
        cs match
            case "infinity"  => OffsetDateTime.MAX
            case "-infinity" => OffsetDateTime.MIN
            case _           => OffsetDateTime.parse(cs, TIMESTAMPTZ_FORMAT)
    }

    private def decodeHexChar(ch: Byte): Byte = (((ch & 0x1f) + ((ch >> 6) * 0x19) - 0x10) & 0x0f).toByte

    private def isHexFormat(index: Int, len: Int, buffer: Buffer): Boolean =
        len >= 2 && buffer.indexAre(HEX_PREFIX, index)

    private val TIMETZ_FORMAT = new DateTimeFormatterBuilder().parseCaseInsensitive
        .append(ISO_LOCAL_TIME)
        .appendOffset("+HH:mm", "00:00")
        .toFormatter

    private val TIMESTAMP_FORMAT = new DateTimeFormatterBuilder().parseCaseInsensitive
        .append(ISO_LOCAL_DATE)
        .appendLiteral(' ')
        .append(ISO_LOCAL_TIME)
        .toFormatter

    private val TIMESTAMPTZ_FORMAT =
        new DateTimeFormatterBuilder().append(TIMESTAMP_FORMAT).appendOffset("+HH:mm", "00:00").toFormatter

    private val LOCAL_DATE_TIME_EPOCH = LocalDateTime.of(2000, 1, 1, 0, 0, 0)

    private val HEX_PREFIX = "\\x".getBytes(StandardCharsets.US_ASCII)

}
