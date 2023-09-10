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

package cc.otavia.adbc.net

import scala.beans.BeanProperty

/** Proxy options for a net client or a net client. */
class ProxyOptions {

    import ProxyOptions.*

    @BeanProperty var host: String     = DEFAULT_HOST
    @BeanProperty var port: Int        = DEFAULT_PORT
    @BeanProperty var username: String = _
    @BeanProperty var password: String = _

    @BeanProperty var `type`: ProxyType = DEFAULT_TYPE

    /** Copy constructor.
     *
     *  @param other
     *    the options to copy
     */
    def this(other: ProxyOptions) = {
        this()
        host = other.host
        port = other.port
        username = other.username
        password = other.password
        `type` = other.`type`
    }

}

object ProxyOptions {

    /** The default proxy type (HTTP) */
    val DEFAULT_TYPE: ProxyType = ProxyType.HTTP

    /** The default port for proxy connect = 3128 3128 is the default port for e.g. Squid */
    val DEFAULT_PORT: Int = 3128

    /** The default hostname for proxy connect = "localhost" */
    val DEFAULT_HOST: String = "localhost"

}
