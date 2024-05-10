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

import javax.net.ssl.SSLEngine

/** Provides a means to filter the supplied cipher suite based upon the supported and default cipher suites. */
trait CipherSuiteFilter {

    /** Filter the requested ciphers based upon other cipher characteristics.
     *
     *  @param ciphers
     *    The requested ciphers
     *  @param default
     *    The default recommended ciphers for the current [[SSLEngine]] as determined by otavia
     *  @param supported
     *    The supported ciphers for the current [[SSLEngine]]
     *  @return
     *    The filter list of ciphers. Must not return null.
     */
    def filterCipherSuites(ciphers: Option[Seq[String]], default: Array[String], supported: Set[String]): Array[String]
}
