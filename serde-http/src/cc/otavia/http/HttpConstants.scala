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
    val SP = 32

    /** Horizontal tab */
    val HT = 9

    /** Carriage return */
    val CR = 13

    /** Equals '=' */
    val EQUALS = 61

    /** Line feed character */
    val LF = 10

    /** Colon ':' */
    val COLON = 58

    /** Semicolon ';' */
    val SEMICOLON = 59

    /** Comma ',' */
    val COMMA = 44

    /** Double quote '"' */
    val DOUBLE_QUOTE = '"'

    /** Default character set (UTF-8) */
    val DEFAULT_CHARSET: Charset = StandardCharsets.UTF_8

    /** Horizontal space */
    val SP_CHAR: Char = SP.toChar

}
