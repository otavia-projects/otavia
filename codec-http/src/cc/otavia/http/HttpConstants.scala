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

package cc.otavia.http

import java.nio.charset.{Charset, StandardCharsets}
import scala.language.unsafeNulls

object HttpConstants {

    /** Horizontal space */
    val SP: Byte = 32

    /** Horizontal tab */
    val HT = 9

    /** Carriage return */
    val CR = 13

    /** Equals '=' */
    val EQUALS = 61

    /** Line feed character */
    val LF = 10

    /** Colon ':' */
    val COLON: Byte = 58

    /** Semicolon ';' */
    val SEMICOLON = 59

    /** Comma ',' */
    val COMMA = 44

    /** Double quote '"' */
    val DOUBLE_QUOTE = '"'

    val EQ: Byte = '='

    val PARAM_START: Byte    = '?'
    val PARAM_SPLITTER: Byte = '&'

    /** Default character set (UTF-8) */
    val DEFAULT_CHARSET: Charset = StandardCharsets.UTF_8

    /** Horizontal space */
    val SP_CHAR: Char = SP.toChar

    /** http schema */
    val HTTP_SCHEMA: Array[Byte] = "http://".getBytes(StandardCharsets.US_ASCII)

    /** https schema */
    val HTTPS_SCHEMA: Array[Byte] = "https://".getBytes(StandardCharsets.US_ASCII)

    val HEADERS_END: Array[Byte]     = "\r\n\r\n".getBytes(StandardCharsets.US_ASCII)
    val HEADER_LINE_END: Array[Byte] = "\r\n".getBytes(StandardCharsets.US_ASCII)

    val HEADER_SPLITTER: Array[Byte] = ": ".getBytes(StandardCharsets.US_ASCII)

    val NODE_END: Array[Byte]      = "/? ".getBytes(StandardCharsets.US_ASCII)
    val NODE_SPLITTER: Array[Byte] = "/ ".getBytes(StandardCharsets.US_ASCII)

    val PATH_SPLITTER: Byte = '/'

    val PATH_END: Array[Byte]   = "? ".getBytes(StandardCharsets.US_ASCII)
    val PARAMS_END: Array[Byte] = "& ".getBytes(StandardCharsets.US_ASCII)

    val STATUS_200_OK: Array[Byte] = " 200 OK\r\n".getBytes(StandardCharsets.US_ASCII)

}
