/*
 * Copyright 2022 Yan Kun <yan_kun_1992@foxmail.com>
 *
 * This file fork from netty.
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

package cc.otavia.handler.ssl

import java.security.Provider

/** An enumeration of SSL/TLS protocol providers. */
enum SslProvider {

    /** JDK's default implementation. */
    case JDK
}

object SslProvider {

    def isAlpnSupported(provider: SslProvider): Boolean = provider match
        case SslProvider.JDK => true // JDK supported ALPN after JDK 9

    /** Returns true if the specified [[SslProvider]] supports <a href="https://tools.ietf.org/html/rfc8446">TLS 1.3</a>
     *  , false otherwise.
     */
    def isTlsv13Supported(sslProvider: SslProvider, provider: Option[Provider] = None): Boolean = sslProvider match
        case SslProvider.JDK => ???

    def isTlsv13EnabledByDefault(sslProvider: SslProvider, provider: Provider): Boolean = sslProvider match
        case SslProvider.JDK => ???

}
