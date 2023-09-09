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

/** Values for the capabilities flag bitmask used by Client/Server Protocol. More information can be found in <a
 *  href="https://dev.mysql.com/doc/internals/en/capability-flags.html">MySQL Internals Manual</a> and <a
 *  href="https://dev.mysql.com/doc/dev/mysql-server/8.0.4/group__group__cs__capabilities__flags.html">MySQL Source Code
 *  Documentation</a>.
 */
object CapabilitiesFlag {

    /*
     The capability flags are used by the client and server to indicate which features they support and want to use.
     */
    val CLIENT_LONG_PASSWORD                  = 0x00000001
    val CLIENT_FOUND_ROWS                     = 0x00000002
    val CLIENT_LONG_FLAG                      = 0x00000004
    val CLIENT_CONNECT_WITH_DB                = 0x00000008
    val CLIENT_NO_SCHEMA                      = 0x00000010
    val CLIENT_COMPRESS                       = 0x00000020
    val CLIENT_ODBC                           = 0x00000040
    val CLIENT_LOCAL_FILES                    = 0x00000080
    val CLIENT_IGNORE_SPACE                   = 0x00000100
    val CLIENT_PROTOCOL_41                    = 0x00000200
    val CLIENT_INTERACTIVE                    = 0x00000400
    val CLIENT_SSL                            = 0x00000800
    val CLIENT_IGNORE_SIGPIPE                 = 0x00001000
    val CLIENT_TRANSACTIONS                   = 0x00002000
    val CLIENT_MULTI_STATEMENTS               = 0x00010000
    val CLIENT_MULTI_RESULTS                  = 0x00020000
    val CLIENT_PS_MULTI_RESULTS               = 0x00040000
    val CLIENT_PLUGIN_AUTH                    = 0x00080000
    val CLIENT_CONNECT_ATTRS                  = 0x00100000
    val CLIENT_PLUGIN_AUTH_LENENC_CLIENT_DATA = 0x00200000
    val CLIENT_CAN_HANDLE_EXPIRED_PASSWORDS   = 0x00400000
    val CLIENT_SESSION_TRACK                  = 0x00800000
    val CLIENT_DEPRECATE_EOF                  = 0x01000000
    val CLIENT_OPTIONAL_RESULTSET_METADATA    = 0x02000000
    val CLIENT_REMEMBER_OPTIONS               = 0x80000000

    /*
      Deprecated flags
     */
    @deprecated val CLIENT_RESERVED = 0x00004000
    //  @Deprecated
    // CLIENT_RESERVED2
    val CLIENT_SECURE_CONNECTION              = 0x00008000
    @deprecated val CLIENT_VERIFY_SERVER_CERT = 0x40000000

    val CLIENT_SUPPORTED_CAPABILITIES_FLAGS: Int =
        CLIENT_PLUGIN_AUTH | CLIENT_LONG_PASSWORD | CLIENT_LONG_FLAG | CLIENT_PLUGIN_AUTH_LENENC_CLIENT_DATA | CLIENT_SECURE_CONNECTION | CLIENT_PROTOCOL_41 | CLIENT_TRANSACTIONS | CLIENT_MULTI_STATEMENTS | CLIENT_MULTI_RESULTS | CLIENT_PS_MULTI_RESULTS | //    | CLIENT_SESSION_TRACK disable this it's not really used for now
            CLIENT_LOCAL_FILES

}
