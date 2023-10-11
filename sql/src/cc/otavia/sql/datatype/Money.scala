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

package cc.otavia.sql.datatype

import java.lang
import java.math.{BigDecimal as JBigDecimal, BigInteger as JBigInteger}
import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}
import scala.language.unsafeNulls
import scala.math.ScalaNumber

/** The PostgreSQL <a href="https://www.postgresql.org/docs/9.1/datatype-money.html">MONEY</> type.
 *
 *  This has the integer part [[integerPart]] and decimal part [[decimalPart]] of the value without loss of information.
 *
 *  @param integerPart
 *    integer part
 *  @param decimalPart
 *    decimal part
 */
case class Money private (integerPart: Long, decimalPart: Int) {

    /** @return the monetary amount as a big decimal without loss of information */
    def bigDecimalValue: BigDecimal = {
        var value = BigDecimal(integerPart) * BigDecimal(100)
        if (value >= 0) value = value + BigDecimal(decimalPart) else value = value - BigDecimal(decimalPart)
        value
    }

    /** @return the monetary amount as a double with possible loss of information */
    def doubleValue: Double = bigDecimalValue.doubleValue

}

object Money {

    def apply(integerPart: Long, decimalPart: Int): Money = {
        checkIntegerPart(integerPart)
        checkDecimalPart(decimalPart)
        new Money(integerPart, decimalPart)
    }

    def apply(value: Number): Money = {
        value match
            case value: (lang.Double | lang.Float | JBigDecimal | BigDecimal) =>
                val big             = JBigDecimal.valueOf(value.doubleValue)
                val bd: JBigInteger = big.multiply(new JBigDecimal(100)).toBigInteger
                val integerPart     = bd.divide(JBigInteger.valueOf(100)).longValueExact()
                val decimalPart     = bd.remainder(JBigInteger.valueOf(100)).abs().intValueExact()
                apply(integerPart, decimalPart)
            case _ => new Money(value.longValue(), 0)
    }

    private def checkIntegerPart(part: Long): Unit =
        if (part > INTEGER_MAX || part < INTEGER_MIN) throw new IllegalArgumentException()

    private def checkDecimalPart(part: Int): Unit =
        if (part > DECIMAL_MAX || part < DECIMAL_MIN) throw new IllegalArgumentException()

    private val DECIMAL_MAX = 99
    private val DECIMAL_MIN = 0

    private val INTEGER_MAX = Long.MaxValue / 100
    private val INTEGER_MIN = Long.MinValue / 100

}
