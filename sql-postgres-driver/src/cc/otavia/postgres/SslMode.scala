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

import scala.language.unsafeNulls

/** The different values for the sslmode parameter provide different levels of protection. See more information in <a
 *  href= "https://www.postgresql.org/docs/current/libpq-ssl.html#LIBPQ-SSL-PROTECTION">Protection Provided in Different
 *  Modes</a>.
 */
enum SslMode(val value: String) {

    /** only try a non-SSL connection. */
    case DISABLE extends SslMode("disable")

    /** first try a non-SSL connection; if that fails, try an SSL connection. */
    case ALLOW extends SslMode("allow")

    /** first try an SSL connection; if that fails, try a non-SSL connection. */
    case PREFER extends SslMode("prefer")

    /** only try an SSL connection. If a root CA file is present, verify the certificate in the same way as if verify-ca
     *  was specified.
     */
    case REQUIRE extends SslMode("require")

    /** only try an SSL connection, and verify that the server certificate is issued by a trusted certificate authority
     *  (CA).
     */
    case VERIFY_CA extends SslMode("verify-ca")

    /** only try an SSL connection, verify that the server certificate is issued by a trusted CA and that the requested
     *  server host name matches that in the certificate.
     */
    case VERIFY_FULL extends SslMode("verify-full")

}

object SslMode {
    def of(value: String): SslMode =
        value.trim.toLowerCase match
            case "disable"     => DISABLE
            case "allow"       => ALLOW
            case "prefer"      => PREFER
            case "require"     => REQUIRE
            case "verify-ca"   => VERIFY_CA
            case "verify-full" => VERIFY_FULL
            case _ =>
                throw new IllegalArgumentException(s"Could not find an appropriate SSL mode for the value [$value]")
}
