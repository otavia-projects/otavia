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

package cc.otavia.postgres.spi

import cc.otavia.sql.{ConnectOptions, Driver, DriverFactory}
import cc.otavia.postgres.{PostgresConnectOptions, PostgresDriver, SslMode}

import java.util
import scala.collection.mutable
import scala.language.unsafeNulls

object PostgresDriverFactory extends DriverFactory {

    override def newDriver(options: ConnectOptions): Driver =
        new PostgresDriver(options.asInstanceOf[PostgresConnectOptions])

    override def driverClassName: String = "cc.otavia.postgres.PostgresDriver"

    override def parseOptions(url: String, info: Map[String, String]): ConnectOptions = {
        val pattern = getPattern("jdbc:postgresql://{host}[:{port}]/[{database}][\\?{params}]")
        val matcher = pattern.matcher(url)
        val options = new PostgresConnectOptions()

        val urlParams: mutable.Map[String, String] = mutable.HashMap.empty
        if (matcher.matches()) {
            val host     = matcher.group("host")
            val port     = matcher.group("port")
            val database = matcher.group("database")

            options.host = host
            if (port != null) options.port = port.toInt
            options.database = database

            val params = matcher.group("params")
            if (params != null) {
                val pairs = params.split("&")
                pairs.foreach { pair =>
                    val parts = pair.split("=")
                    urlParams.put(parts(0), parts(1))
                }
            }
        }

        for ((key, value) <- urlParams.toMap ++ info) {
            key match
                case "user"            => options.user = value
                case "password"        => options.password = value
                case "sslMode"         => options.sslMode = SslMode.valueOf(value)
                case "pipeliningLimit" => options.pipeliningLimit = value.toInt
                case "useLayer7Proxy"  => options.useLayer7Proxy = value.toBoolean
                case _                 => options.properties.put(key, value)
        }

        options
    }

}
