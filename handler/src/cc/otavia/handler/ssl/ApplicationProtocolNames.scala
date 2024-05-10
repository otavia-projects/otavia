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

/** Provides a set of protocol names used in ALPN and NPN. */
object ApplicationProtocolNames {

    /** "h2": HTTP version 2 */
    val HTTP_2 = "h2"

    /** "http/1.1": HTTP version 1.1 */
    val HTTP_1_1 = "http/1.1"

    /** "spdy/3.1": SPDY version 3.1 */
    val SPDY_3_1 = "spdy/3.1"

    /** "spdy/3": SPDY version 3 */
    val SPDY_3 = "spdy/3"

    /** "spdy/2": SPDY version 2 */
    val SPDY_2 = "spdy/2"

    /** "spdy/1": SPDY version 1 */
    val SPDY_1 = "spdy/1"

}
