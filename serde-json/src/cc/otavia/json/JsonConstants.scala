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

package cc.otavia.json

import java.nio.charset.StandardCharsets
import scala.language.unsafeNulls

object JsonConstants {

    val TOKEN_BLANKS: Array[Byte] = " \n\r\t".getBytes(StandardCharsets.US_ASCII)

    val TOKEN_TURE: Array[Byte]  = "true".getBytes(StandardCharsets.US_ASCII)
    val TOKEN_FALSE: Array[Byte] = "false".getBytes(StandardCharsets.US_ASCII)

    val TOKEN_NULL: Array[Byte] = "null".getBytes(StandardCharsets.US_ASCII)

    val TOKEN_COMMA: Byte        = ','
    val TOKEN_COLON: Byte        = ':'
    val TOKEN_DOUBLE_QUOTE: Byte = '"'

    val TOKEN_OBJECT_START: Byte = '{'
    val TOKEN_OBJECT_END: Byte   = '}'

    val TOKEN_ARRAY_START: Byte = '['
    val TOKEN_ARRAY_END: Byte   = ']'

    val TOKEN_NUMBERS: Array[Byte] = "1234567890".getBytes(StandardCharsets.US_ASCII)
    val TOKEN_FLOATS: Array[Byte]  = "1234567890.".getBytes(StandardCharsets.US_ASCII)
    val TOKEN_POINT: Byte          = '.'
    val TOKEN_PLUS: Byte           = '+'
    val TOKEN_MINUS: Byte          = '-'
    val TOKEN_ZERO: Byte           = '0'
    val TOKEN_NINE: Byte           = '9'

}
