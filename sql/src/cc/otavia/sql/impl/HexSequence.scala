/*
 * Copyright 2022 Yan Kun <yan_kun_1992@foxmail.com>
 *
 * This file fork from vertx-sql-client
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

package cc.otavia.sql.impl

// format: off
/**
 * A sequence of hex values, each terminated by a zero byte.
 *
 * <p>The hex number is left padded to start with at least three 0
 * and to have at least seven digits.
 *
 * <p>After 000FFFFFFFFFFFFFFFF it will restart with 0000000.
 *
 * <p>The generated sequence:
 * <pre>
 * 0000000
 * 0000001
 * 0000002
 * ...
 * 000FFFF
 * 00010000
 * ...
 * 000FFFFF
 * 000100000
 * ...
 * 000FFFFFF
 * 0001000000
 * ...
 * 000FFFFFFFFFFFFFFFF
 * </pre>
 */
// format: on
class HexSequence {

    private var i: Long = 0

    /** @param start unsigned long for the first value returned by [[next]]() */
    def this(start: Long) = {
        this()
        i = start
    }

    /** A copy of the next hex value, terminated by a zero byte. */
    def next(): Array[Byte] = {
        var len = 3 // 3 leading zeroes
            + (64 - java.lang.Long.numberOfLeadingZeros(i) + 3) / 4 // hex characters
            + 1                                                     // tailing null byte
        len = math.max(8, len)                                      // at least 7 hex digits plus null byte
        val hex = new Array[Byte](len)
        var pos = len - 1
        hex(pos) = 0
        pos -= 1
        var n = i
        while (n != 0) {
            hex(pos) = HexSequence.toHex(n & 0xf)
            pos -= 1
            n >>>= 4
        }
        while (pos >= 0) {
            hex(pos) = '0'
            pos -= 1
        }
        i += 1
        hex
    }

}

object HexSequence {
    private def toHex(c: Long) =
        if (c < 10) ('0' + c).toByte else ('A' + c - 10).toByte
}
