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

package cc.otavia.postgres.protocol

object Constants {

    val AUTH_TYPE_OK: Int                 = 0
    val AUTH_TYPE_KERBEROS_V5: Int        = 2
    val AUTH_TYPE_CLEARTEXT_PASSWORD: Int = 3
    val AUTH_TYPE_MD5_PASSWORD: Int       = 5
    val AUTH_TYPE_SCM_CREDENTIAL: Int     = 6
    val AUTH_TYPE_GSS: Int                = 7
    val AUTH_TYPE_GSS_CONTINUE: Int       = 8
    val AUTH_TYPE_SSPI: Int               = 9
    val AUTH_TYPE_SASL: Int               = 10
    val AUTH_TYPE_SASL_CONTINUE: Int      = 11
    val AUTH_TYPE_SASL_FINAL: Int         = 12

    val ERR_OR_NOTICE_SEVERITY: Byte          = 'S'
    val ERR_OR_NOTICE_CODE: Byte              = 'C'
    val ERR_OR_NOTICE_MESSAGE: Byte           = 'M'
    val ERR_OR_NOTICE_DETAIL: Byte            = 'D'
    val ERR_OR_NOTICE_HINT: Byte              = 'H'
    val ERR_OR_NOTICE_POSITION: Byte          = 'P'
    val ERR_OR_NOTICE_INTERNAL_POSITION: Byte = 'p'
    val ERR_OR_NOTICE_INTERNAL_QUERY: Byte    = 'q'
    val ERR_OR_NOTICE_WHERE: Byte             = 'W'
    val ERR_OR_NOTICE_FILE: Byte              = 'F'
    val ERR_OR_NOTICE_LINE: Byte              = 'L'
    val ERR_OR_NOTICE_ROUTINE: Byte           = 'R'
    val ERR_OR_NOTICE_SCHEMA: Byte            = 's'
    val ERR_OR_NOTICE_TABLE: Byte             = 't'
    val ERR_OR_NOTICE_COLUMN: Byte            = 'c'
    val ERR_OR_NOTICE_DATA_TYPE: Byte         = 'd'
    val ERR_OR_NOTICE_CONSTRAINT: Byte        = 'n'

    val MSG_TYPE_BACKEND_KEY_DATA: Byte      = 'K'
    val MSG_TYPE_AUTHENTICATION: Byte        = 'R'
    val MSG_TYPE_ERROR_RESPONSE: Byte        = 'E'
    val MSG_TYPE_NOTICE_RESPONSE: Byte       = 'N'
    val MSG_TYPE_NOTIFICATION_RESPONSE: Byte = 'A'
    val MSG_TYPE_COMMAND_COMPLETE: Byte      = 'C'
    val MSG_TYPE_PARAMETER_STATUS: Byte      = 'S'
    val MSG_TYPE_READY_FOR_QUERY: Byte       = 'Z'
    val MSG_TYPE_PARAMETER_DESCRIPTION: Byte = 't'
    val MSG_TYPE_ROW_DESCRIPTION: Byte       = 'T'
    val MSG_TYPE_DATA_ROW: Byte              = 'D'
    val MSG_TYPE_PORTAL_SUSPENDED: Byte      = 's'
    val MSG_TYPE_NO_DATA: Byte               = 'n'
    val MSG_TYPE_EMPTY_QUERY_RESPONSE: Byte  = 'I'
    val MSG_TYPE_PARSE_COMPLETE: Byte        = '1'
    val MSG_TYPE_BIND_COMPLETE: Byte         = '2'
    val MSG_TYPE_CLOSE_COMPLETE: Byte        = '3'
    val MSG_TYPE_FUNCTION_RESULT: Byte       = 'V'
    val MSG_TYPE_SSL_YES: Byte               = 'S'
    val MSG_TYPE_SSL_NO: Byte                = 'N'

}
