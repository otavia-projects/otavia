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

/*
    https://dev.mysql.com/doc/dev/mysql-server/latest/mysql__com_8h.html#a1d854e841086925be1883e4d7b4e8cad
 */
object ServerStatusFlags {

    val SERVER_STATUS_IN_TRANS: Int             = 0x0001
    val SERVER_STATUS_AUTOCOMMIT: Int           = 0x0002
    val SERVER_MORE_RESULTS_EXISTS: Int         = 0x0008
    val SERVER_STATUS_NO_GOOD_INDEX_USED: Int   = 0x0010
    val SERVER_STATUS_NO_INDEX_USED: Int        = 0x0020
    val SERVER_STATUS_CURSOR_EXISTS: Int        = 0x0040
    val SERVER_STATUS_LAST_ROW_SENT: Int        = 0x0080
    val SERVER_STATUS_DB_DROPPED: Int           = 0x0100
    val SERVER_STATUS_NO_BACKSLASH_ESCAPES: Int = 0x0200
    val SERVER_STATUS_METADATA_CHANGED: Int     = 0x0400
    val SERVER_QUERY_WAS_SLOW: Int              = 0x0800
    val SERVER_PS_OUT_PARAMS: Int               = 0x1000
    val SERVER_STATUS_IN_TRANS_READONLY: Int    = 0x2000
    val SERVER_SESSION_STATE_CHANGED: Int       = 0x4000

}
