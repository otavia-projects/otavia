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

package cc.otavia.buffer

object BufferUtils {

    private val INTEGER_TRIM: Array[Byte] = Array(' ', '+')

    final def writeShortAsString(short: Short, buffer: Buffer): Unit = {
        ???
    }

    final def writeIntAsString(int: Int, buffer: Buffer): Unit = {
        ???
    }

    final def writeLongAsString(long: Long, buffer: Buffer): Unit = {
        ???
    }

    final def readStringAsShort(buffer: Buffer, len: Int): Short = {
        var length = len
        while (buffer.skipIfNexts(INTEGER_TRIM)) length -= 1
        val minus = buffer.skipIfNext('-')
        if (minus) length -= 1
        var res: Short = 0
        while {
            val b  = buffer.readByte
            val ok = b >= '0' && b <= '9'
            if (ok) {
                res = (res * 10 + (b - '0')).toShort
                length -= 1
            }
            length > 0
        } do ()

        if (minus) (-res).toShort else res
    }

    /** Parses the string content stored in the buffer as a signed integer in the radix specified by the third argument.
     *  The characters in the string must all be digits of the specified radix (as determined by whether
     *  Character.digit(char, int) returns a nonnegative value), except that the first character may be an ASCII minus
     *  sign '-' ('\u002D') to indicate a negative value or an ASCII plus sign '+' ('\u002B') to indicate a positive
     *  value. The resulting integer value is returned.
     *
     *  An exception of type [[NumberFormatException]] is thrown if any of the following situations occurs:
     *
     *  this method fork form JDK [[Integer.parseInt]]
     *
     *  @param buffer
     *    string number which stored in the [[Buffer]].
     *  @param len
     *    string number content length.
     *  @param radix
     *    the radix to be used while parsing the string.
     *  @throws NumberFormatException
     *    if the string content does not contain a parsable int.
     *
     *  @return
     *    the integer represented by the string content in the specified radix.
     */
    @throws[NumberFormatException]
    final def readStringAsInt(buffer: Buffer, len: Int, radix: Int = 10): Int = {
        if (radix < Character.MIN_RADIX)
            throw new NumberFormatException(s"radix $radix less than Character.MIN_RADIX")

        if (radix > Character.MAX_RADIX)
            throw new NumberFormatException(s"radix $radix greater than Character.MAX_RADIX")

        var negative = false
        var i        = 0
        var limit    = -Integer.MAX_VALUE

        if (len > 0) {
            val firstByte = buffer.getByte(buffer.readerOffset)
            if (firstByte < '0') { // Possible leading "+" or "-"
                if (firstByte == '-') {
                    negative = true
                    limit = Integer.MIN_VALUE
                } else if (firstByte != '+') {
                    throw new NumberFormatException(
                      s"For input string: \"${buffer.readCharSequence(len)}\" under radix $radix"
                    )
                }

                if (len == 1)
                    throw new NumberFormatException(s"For input string \"${buffer.readByte}\" under radix $radix")

                i += 1
            }
            val multmin = limit / radix
            var result  = 0
            while (i < len) {
                val digit = Character.digit(buffer.getByte(buffer.readerOffset + i), radix)
                i += 1
                if (digit < 0 || result < multmin) {
                    throw new NumberFormatException(
                      s"For input string: \"${buffer.readCharSequence(len)}\" under radix $radix"
                    )
                }
                result *= radix
                if (result < limit + digit)
                    throw new NumberFormatException(
                      s"For input string: \"${buffer.readCharSequence(len)}\" under radix $radix"
                    )

                result -= digit
            }

            buffer.skipReadableBytes(len)

            if (negative) result else -result

        } else throw new NumberFormatException(s"string length must be positive: len = $len")
    }

    final def readStringAsLong(buffer: Buffer, len: Int): Long = ???

}
