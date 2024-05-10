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

/** SSL/TLS protocols */
object SslProtocols {

    /** SSL v2 Hello
     *
     *  @deprecated
     *    SSLv2Hello is no longer secure. Consider using [[TLS_v1_2]] or [[TLS_v1_3]]
     */
    @deprecated
    val SSL_v2_HELLO = "SSLv2Hello"

    /** SSL v2
     *  @deprecated
     *    SLv2 is no longer secure. Consider using [[TLS_v1_2]] or [[TLS_v1_3]]
     */
    @deprecated
    val SSL_v2 = "SSLv2"

    /** SSLv3
     *  @deprecated
     *    SSLv3 is no longer secure. Consider using [[TLS_v1_2]] or [[TLS_v1_3]]
     */
    @deprecated
    val SSL_v3 = "SSLv3"

    /** TLS v1
     *  @deprecated
     *    TLSv1 is no longer secure. Consider using [[TLS_v1_2]] or [[TLS_v1_3]]
     */
    @deprecated
    val TLS_v1 = "TLSv1"

    /** TLS v1.1
     *  @deprecated
     *    TLSv1.1 is no longer secure. Consider using [[TLS_v1_2]] or [[TLS_v1_3]]
     */
    @deprecated
    val TLS_v1_1 = "TLSv1.1"

    /** TLS v1.2 */
    val TLS_v1_2 = "TLSv1.2"

    /** TLS v1.3 */
    val TLS_v1_3 = "TLSv1.3"

}
