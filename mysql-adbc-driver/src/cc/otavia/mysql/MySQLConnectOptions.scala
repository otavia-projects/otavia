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

package cc.otavia.mysql

import cc.otavia.adbc.ConnectOptions
import cc.otavia.buffer.Buffer

import scala.beans.BeanProperty
import scala.language.unsafeNulls

class MySQLConnectOptions extends ConnectOptions {

    import MySQLConnectOptions.*

    @BeanProperty var collation: String                          = _
    @BeanProperty var charset: String                            = DEFAULT_CHARSET
    @BeanProperty var useAffectedRows: Boolean                   = DEFAULT_USE_AFFECTED_ROWS
    @BeanProperty var sslMode: SslMode                           = DEFAULT_SSL_MODE
    @BeanProperty var serverRsaPublicKeyPath: String             = _
    @BeanProperty var serverRsaPublicKeyValue: Buffer            = _
    @BeanProperty var characterEncoding: String                  = DEFAULT_CHARACTER_ENCODING
    @BeanProperty var pipeliningLimit: Int                       = DEFAULT_PIPELINING_LIMIT
    @BeanProperty var authenticationPlugin: AuthenticationPlugin = AuthenticationPlugin.DEFAULT

    host = DEFAULT_HOST
    port = DEFAULT_PORT
    user = DEFAULT_USER
    password = DEFAULT_PASSWORD
    database = DEFAULT_SCHEMA

    for ((key, value) <- DEFAULT_CONNECTION_ATTRIBUTES) properties.put(key, value)

}

object MySQLConnectOptions {

    val DEFAULT_HOST: String                               = "localhost"
    val DEFAULT_PORT: Int                                  = 3306
    val DEFAULT_USER: String                               = "root"
    val DEFAULT_PASSWORD: String                           = ""
    val DEFAULT_SCHEMA: String                             = ""
    val DEFAULT_CHARSET: String                            = "utf8mb4"
    val DEFAULT_USE_AFFECTED_ROWS: Boolean                 = false
    val DEFAULT_CONNECTION_ATTRIBUTES: Map[String, String] = Map("_client_name" -> "otavia-mysql-client")
    val DEFAULT_SSL_MODE: SslMode                          = SslMode.DISABLED
    val DEFAULT_CHARACTER_ENCODING: String                 = "UTF-8"
    val DEFAULT_PIPELINING_LIMIT: Int                      = 1

}
