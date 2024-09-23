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

import java.math.BigInteger
import scala.language.unsafeNulls

trait BufferNumberUtils extends BufferNumberBaseUtils {

    final def readStringAsBigInt(buffer: Buffer): BigInt = {
        ???
    }

    final def writeBigIntAsString(buffer: Buffer, bigInt: BigInt): Unit = if (bigInt.isValidLong)
        writeLongAsString(buffer, bigInt.longValue)
    else writeBigInteger(buffer, bigInt.bigInteger, null)

    final def writeBigIntegerAsString(buffer: Buffer, num: BigInteger): Unit = writeBigInteger(buffer, num, null)

    private def writeBigInteger(buffer: Buffer, x: BigInteger, ss: Array[BigInteger]): Unit = {
        val bitLen = x.bitLength
        if (bitLen < 64) writeLongAsString(buffer, x.longValue)
        else {
            val n   = calculateTenPow18SquareNumber(bitLen)
            val ss1 = if (ss eq null) BufferConstants.getTenPow18Squares(n) else ss
            val qr  = x.divideAndRemainder(ss1(n))
            writeBigInteger(buffer, qr(0), ss1)
            writeBigIntegerRemainder(buffer, qr(1), n - 1, ss1)
        }
    }

    private def writeBigIntegerRemainder(buffer: Buffer, x: BigInteger, n: Int, ss: Array[BigInteger]): Unit =
        if (n < 0) write18Digits(buffer, Math.abs(x.longValue), BufferConstants.digits)
        else {
            val qr = x.divideAndRemainder(ss(n))
            writeBigIntegerRemainder(buffer, qr(0), n - 1, ss)
            writeBigIntegerRemainder(buffer, qr(1), n - 1, ss)
        }

    private def writeBigDecimal(buffer: Buffer, x: BigInteger, scale: Int, block: Int, ss: Array[BigInteger]): Long = {
        val bitLen = x.bitLength
        if (bitLen < 64) {
            val v   = x.longValue
            val pos = buffer.writerOffset
            writeLongAsString(buffer, v)
            val lastPos = buffer.writerOffset
            val digits  = (v >> 63).toInt + lastPos - pos
            val dotOff  = scale.toLong - block
            val exp     = (digits - 1) - dotOff
            if (scale >= 0 && exp >= -6) {} else {}
        } else {
            val n   = calculateTenPow18SquareNumber(bitLen)
            val ss1 = if (ss eq null) BufferConstants.getTenPow18Squares(n) else ss
            val qr  = x.divideAndRemainder(ss1(n))
            val exp = writeBigDecimal(buffer, qr(0), scale, (18 << n) + block, ss1)
            //            writeBigDecimalRemainder(qr(1), scale, blockScale, n - 1, ss1)
            exp
        }
        ???
    }

    private def calculateTenPow18SquareNumber(bitLen: Int): Int = {
        // Math.max((x.bitLength * Math.log(2) / Math.log(1e18)).toInt - 1, 1)
        val m = Math.max((bitLen * 71828554L >> 32).toInt - 1, 1)
        31 - java.lang.Integer.numberOfLeadingZeros(m)
    }

    private def insertDotWithZeroes(buffer: Buffer, digits: Int, pad: Int, lastPos: Int): Int = {
        var pos    = lastPos + pad + 1
        val numPos = pos - digits
        val off    = pad + 2

        ???
    }

    

}
