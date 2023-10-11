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

package cc.otavia.mysql.protocol

/* Type of column definition
 *  https://dev.mysql.com/doc/dev/mysql-server/latest/binary__log__types_8h.html#aab0df4798e24c673e7686afce436aa85
 */
object ColumnType {

    val MYSQL_TYPE_DECIMAL: Short     = 0x00
    val MYSQL_TYPE_TINY: Short        = 0x01
    val MYSQL_TYPE_SHORT: Short       = 0x02
    val MYSQL_TYPE_LONG: Short        = 0x03
    val MYSQL_TYPE_FLOAT: Short       = 0x04
    val MYSQL_TYPE_DOUBLE: Short      = 0x05
    val MYSQL_TYPE_NULL: Short        = 0x06
    val MYSQL_TYPE_TIMESTAMP: Short   = 0x07
    val MYSQL_TYPE_LONGLONG: Short    = 0x08
    val MYSQL_TYPE_INT24: Short       = 0x09
    val MYSQL_TYPE_DATE: Short        = 0x0a
    val MYSQL_TYPE_TIME: Short        = 0x0b
    val MYSQL_TYPE_DATETIME: Short    = 0x0c
    val MYSQL_TYPE_YEAR: Short        = 0x0d
    val MYSQL_TYPE_VARCHAR: Short     = 0x0f
    val MYSQL_TYPE_BIT: Short         = 0x10
    val MYSQL_TYPE_JSON: Short        = 0xf5
    val MYSQL_TYPE_NEWDECIMAL: Short  = 0xf6
    val MYSQL_TYPE_ENUM: Short        = 0xf7
    val MYSQL_TYPE_SET: Short         = 0xf8
    val MYSQL_TYPE_TINY_BLOB: Short   = 0xf9
    val MYSQL_TYPE_MEDIUM_BLOB: Short = 0xfa
    val MYSQL_TYPE_LONG_BLOB: Short   = 0xfb
    val MYSQL_TYPE_BLOB: Short        = 0xfc
    val MYSQL_TYPE_VAR_STRING: Short  = 0xfd
    val MYSQL_TYPE_STRING: Short      = 0xfe
    val MYSQL_TYPE_GEOMETRY: Short    = 0xff

    /*
      Internal to MySQL Server
     */
    private val MYSQL_TYPE_NEWDATE: Int    = 0x0e
    private val MYSQL_TYPE_TIMESTAMP2: Int = 0x11
    private val MYSQL_TYPE_DATETIME2: Int  = 0x12
    private val MYSQL_TYPE_TIME2: Int      = 0x13

}
