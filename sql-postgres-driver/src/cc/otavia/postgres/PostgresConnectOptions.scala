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

package cc.otavia.postgres

import cc.otavia.sql.ConnectOptions

import scala.beans.BeanProperty

class PostgresConnectOptions extends ConnectOptions {

    import PostgresConnectOptions.*

    @BeanProperty var pipeliningLimit: Int    = DEFAULT_PIPELINING_LIMIT
    @BeanProperty var sslMode: SslMode        = DEFAULT_SSL_MODE
    @BeanProperty var useLayer7Proxy: Boolean = DEFAULT_USE_LAYER_7_PROXY

    host = DEFAULT_HOST
    port = DEFAULT_PORT
    user = DEFAULT_USER
    password = DEFAULT_PASSWORD
    database = DEFAULT_DATABASE

    for ((key, value) <- DEFAULT_PROPERTIES) properties.put(key, value)

}

object PostgresConnectOptions {

    private val DEFAULT_HOST                  = "localhost"
    private val DEFAULT_PORT                  = 5432
    private val DEFAULT_DATABASE              = "db"
    private val DEFAULT_USER                  = "user"
    private val DEFAULT_PASSWORD              = "pass"
    private val DEFAULT_PIPELINING_LIMIT: Int = 102400
    private val DEFAULT_SSL_MODE: SslMode     = SslMode.DISABLE
    private val DEFAULT_USE_LAYER_7_PROXY     = false
    private val DEFAULT_PROPERTIES: Map[String, String] = Map(
      "application_name"   -> "otavia-pg-client",
      "client_encoding"    -> "utf8",
      "DateStyle"          -> "ISO",
      "extra_float_digits" -> "2"
    )

}
