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

package cc.otavia.sql

import cc.otavia.sql.net.ProxyOptions

import java.net.{InetSocketAddress, SocketAddress}
import scala.beans.BeanProperty
import scala.collection.mutable
import scala.language.unsafeNulls

class ConnectOptions {

    import ConnectOptions.*

    // client options
    @BeanProperty var connectTimeout: Int          = DEFAULT_CONNECT_TIMEOUT
    var trustAll: Boolean                          = DEFAULT_TRUST_ALL
    var metricsName: String                        = DEFAULT_METRICS_NAME
    var proxyOptions: ProxyOptions                 = _
    var localAddress: String                       = _
    var nonProxyHosts: mutable.ArrayBuffer[String] = mutable.ArrayBuffer.empty[String]

    // network options
    private var reconnectAttempts: Int                                       = DEFAULT_RECONNECT_ATTEMPTS
    private var reconnectInterval: Long                                      = DEFAULT_RECONNECT_INTERVAL
    @BeanProperty var hostnameVerificationAlgorithm: String                  = DEFAULT_HOSTNAME_VERIFICATION_ALGORITHM
    @BeanProperty var applicationLayerProtocols: mutable.ArrayBuffer[String] = mutable.ArrayBuffer.empty[String]

    // database options
    @BeanProperty var host: String                       = _
    @BeanProperty var port: Int                          = _
    @BeanProperty var user: String                       = _
    @BeanProperty var password: String                   = _
    @BeanProperty var database: String                   = _
    @BeanProperty var cachePreparedStatements: Boolean   = DEFAULT_CACHE_PREPARED_STATEMENTS
    @BeanProperty var preparedStatementCacheMaxSize: Int = DEFAULT_PREPARED_STATEMENT_CACHE_MAX_SIZE

    @BeanProperty var preparedStatementCacheSqlFilter: String => Boolean = (sql: String) =>
        sql.length < DEFAULT_PREPARED_STATEMENT_CACHE_SQL_LIMIT

    var properties = new mutable.HashMap[String, String]()

    /** Copy constructor
     *
     *  @param other
     *    the options to copy
     */
    def this(other: ConnectOptions) = {
        this()
        init(other)
    }

    protected def init(other: ConnectOptions): Unit = {
        // client options

        // network options
        reconnectAttempts = other.reconnectAttempts
        reconnectInterval = other.reconnectInterval
        hostnameVerificationAlgorithm = other.hostnameVerificationAlgorithm
        other.applicationLayerProtocols.foreach(protocol => applicationLayerProtocols.append(protocol))

        // database options
        host = other.host
        port = other.port
        user = other.user
        password = other.password
        database = other.database
        cachePreparedStatements = other.cachePreparedStatements
        preparedStatementCacheMaxSize = other.preparedStatementCacheMaxSize
        preparedStatementCacheSqlFilter = other.preparedStatementCacheSqlFilter
        if (other.properties != null) {
            for ((key, value) <- other.properties) {
                properties.put(key, value)
            }
        }
    }

    def socketAddress: SocketAddress = new InetSocketAddress(host, port)

}

object ConnectOptions {

    // client options default
    /** The default value of connect timeout = 60000 ms */
    val DEFAULT_CONNECT_TIMEOUT: Int = 60000

    /** The default value of whether all servers (SSL/TLS) should be trusted = false */
    val DEFAULT_TRUST_ALL: Boolean = false

    /** The default value of the client metrics = "": */
    val DEFAULT_METRICS_NAME: String = ""

    // network options default
    /** The default value for reconnect attempts = 0 */
    val DEFAULT_RECONNECT_ATTEMPTS: Int = 0

    /** The default value for reconnect interval = 1000 ms */
    val DEFAULT_RECONNECT_INTERVAL: Long = 1000

    /** Default value to determine hostname verification algorithm hostname verification (for SSL/TLS) = "" */
    val DEFAULT_HOSTNAME_VERIFICATION_ALGORITHM: String = ""

    // database options default
    val DEFAULT_CACHE_PREPARED_STATEMENTS: Boolean      = false
    val DEFAULT_PREPARED_STATEMENT_CACHE_MAX_SIZE: Int  = 256
    val DEFAULT_PREPARED_STATEMENT_CACHE_SQL_LIMIT: Int = 2048

}
