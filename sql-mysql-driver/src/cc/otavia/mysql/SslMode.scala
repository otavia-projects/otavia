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

import scala.language.unsafeNulls

/** This parameter specifies the desired security state of the connection to the server. More information can be found
 *  in <a href="https://dev.mysql.com/doc/refman/8.0/en/connection-options.html#option_general_ssl-mode">MySQL Reference
 *  Manual</a>
 *
 *  @param value
 *    model string
 */
enum SslMode(val value: String) {

    /** establish an unencrypted connection. */
    case DISABLED extends SslMode("disabled")

    /** establish an encrypted connection if the server supports encrypted connections, falling back to an unencrypted
     *  connection if an encrypted connection cannot be established.
     */
    case PREFERRED extends SslMode("preferred")

    /** establish an encrypted connection if the server supports encrypted connections. The connection attempt fails if
     *  an encrypted connection cannot be established.
     */
    case REQUIRED extends SslMode("required")

    /** Like REQUIRED, but additionally verify the server Certificate Authority (CA) certificate against the configured
     *  CA certificates. The connection attempt fails if no valid matching CA certificates are found.
     */
    case VERIFY_CA extends SslMode("verify_ca")

    /** Like VERIFY_CA, but additionally perform host name identity verification by checking the host name the client
     *  uses for connecting to the server against the identity in the certificate that the server sends to the client.
     */
    case VERIFY_IDENTITY extends SslMode("verify_identity")

}

object SslMode {
    def of(value: String): SslMode = value.trim.toLowerCase match
        case "disabled"        => DISABLED
        case "preferred"       => PREFERRED
        case "required"        => REQUIRED
        case "verify_ca"       => VERIFY_CA
        case "verify_identity" => VERIFY_IDENTITY
        case _ => throw new IllegalArgumentException(s"Could not find an appropriate SSL mode for the value [$value]")
}
