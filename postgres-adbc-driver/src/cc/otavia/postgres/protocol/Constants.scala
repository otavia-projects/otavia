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

    val AUTH_TYPE_OK                 = 0
    val AUTH_TYPE_KERBEROS_V5        = 2
    val AUTH_TYPE_CLEARTEXT_PASSWORD = 3
    val AUTH_TYPE_MD5_PASSWORD       = 5
    val AUTH_TYPE_SCM_CREDENTIAL     = 6
    val AUTH_TYPE_GSS                = 7
    val AUTH_TYPE_GSS_CONTINUE       = 8
    val AUTH_TYPE_SSPI               = 9
    val AUTH_TYPE_SASL               = 10
    val AUTH_TYPE_SASL_CONTINUE      = 11
    val AUTH_TYPE_SASL_FINAL         = 12

    val ERR_OR_NOTICE_SEVERITY          = 'S'
    val ERR_OR_NOTICE_CODE              = 'C'
    val ERR_OR_NOTICE_MESSAGE           = 'M'
    val ERR_OR_NOTICE_DETAIL            = 'D'
    val ERR_OR_NOTICE_HINT              = 'H'
    val ERR_OR_NOTICE_POSITION          = 'P'
    val ERR_OR_NOTICE_INTERNAL_POSITION = 'p'
    val ERR_OR_NOTICE_INTERNAL_QUERY    = 'q'
    val ERR_OR_NOTICE_WHERE             = 'W'
    val ERR_OR_NOTICE_FILE              = 'F'
    val ERR_OR_NOTICE_LINE              = 'L'
    val ERR_OR_NOTICE_ROUTINE           = 'R'
    val ERR_OR_NOTICE_SCHEMA            = 's'
    val ERR_OR_NOTICE_TABLE             = 't'
    val ERR_OR_NOTICE_COLUMN            = 'c'
    val ERR_OR_NOTICE_DATA_TYPE         = 'd'
    val ERR_OR_NOTICE_CONSTRAINT        = 'n'

    val MSG_TYPE_BACKEND_KEY_DATA      = 'K'
    val MSG_TYPE_AUTHENTICATION        = 'R'
    val MSG_TYPE_ERROR_RESPONSE        = 'E'
    val MSG_TYPE_NOTICE_RESPONSE       = 'N'
    val MSG_TYPE_NOTIFICATION_RESPONSE = 'A'
    val MSG_TYPE_COMMAND_COMPLETE      = 'C'
    val MSG_TYPE_PARAMETER_STATUS      = 'S'
    val MSG_TYPE_READY_FOR_QUERY       = 'Z'
    val MSG_TYPE_PARAMETER_DESCRIPTION = 't'
    val MSG_TYPE_ROW_DESCRIPTION       = 'T'
    val MSG_TYPE_DATA_ROW              = 'D'
    val MSG_TYPE_PORTAL_SUSPENDED      = 's'
    val MSG_TYPE_NO_DATA               = 'n'
    val MSG_TYPE_EMPTY_QUERY_RESPONSE  = 'I'
    val MSG_TYPE_PARSE_COMPLETE        = '1'
    val MSG_TYPE_BIND_COMPLETE         = '2'
    val MSG_TYPE_CLOSE_COMPLETE        = '3'
    val MSG_TYPE_FUNCTION_RESULT       = 'V'
    val MSG_TYPE_SSL_YES               = 'S'
    val MSG_TYPE_SSL_NO                = 'N'

}
