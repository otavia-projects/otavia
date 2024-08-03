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

package cc.otavia.buffer.constant

import java.nio.charset.StandardCharsets
import scala.language.unsafeNulls

object DurationConstants {

    val Undefined: Array[Byte] = "Duration.Undefined".getBytes(StandardCharsets.US_ASCII)
    val Inf: Array[Byte]       = "Duration.Inf".getBytes(StandardCharsets.US_ASCII)
    val MinusInf: Array[Byte]  = "Duration.MinusInf".getBytes(StandardCharsets.US_ASCII)

    val DAY_BYTES: Array[Byte]         = "day".getBytes(StandardCharsets.US_ASCII)
    val HOUR_BYTES: Array[Byte]        = "hour".getBytes(StandardCharsets.US_ASCII)
    val MINUTE_BYTES: Array[Byte]      = "minute".getBytes(StandardCharsets.US_ASCII)
    val SECOND_BYTES: Array[Byte]      = "second".getBytes(StandardCharsets.US_ASCII)
    val MILLISECOND_BYTES: Array[Byte] = "millisecond".getBytes(StandardCharsets.US_ASCII)
    val MICROSECOND_BYTES: Array[Byte] = "microsecond".getBytes(StandardCharsets.US_ASCII)
    val NANOSECOND_BYTES: Array[Byte]  = "nanosecond".getBytes(StandardCharsets.US_ASCII)

    val InfArray: Array[Array[Byte]] = Array(
      "Inf".getBytes(StandardCharsets.US_ASCII),
      "PlusInf".getBytes(StandardCharsets.US_ASCII),
      "+Inf".getBytes(StandardCharsets.US_ASCII)
    )

    val MinusInfArray: Array[Array[Byte]] =
        Array("MinusInf".getBytes(StandardCharsets.US_ASCII), "-Inf".getBytes(StandardCharsets.US_ASCII))

}
