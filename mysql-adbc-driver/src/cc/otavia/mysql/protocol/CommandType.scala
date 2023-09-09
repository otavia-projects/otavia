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

object CommandType {

    /*
     https://dev.mysql.com/doc/dev/mysql-server/latest/page_protocol_com_field_list.html
     */
    val COM_QUIT             = 0x01
    val COM_INIT_DB          = 0x02
    val COM_QUERY            = 0x03
    val COM_STATISTICS       = 0x09
    val COM_DEBUG            = 0x0d
    val COM_PING             = 0x0e
    val COM_CHANGE_USER      = 0x11
    val COM_RESET_CONNECTION = 0x1f
    val COM_SET_OPTION       = 0x1b

    // Prepared Statements
    val COM_STMT_PREPARE        = 0x16
    val COM_STMT_EXECUTE        = 0x17
    val COM_STMT_FETCH          = 0x1c
    val COM_STMT_CLOSE          = 0x19
    val COM_STMT_RESET          = 0x1a
    val COM_STMT_SEND_LONG_DATA = 0x18

    /*
      Deprecated commands
     */
    @deprecated val COM_FIELD_LIST   = 0x04
    @deprecated val COM_REFRESH      = 0x07
    @deprecated val COM_PROCESS_INFO = 0x0a
    @deprecated val COM_PROCESS_KILL = 0x0c

}
