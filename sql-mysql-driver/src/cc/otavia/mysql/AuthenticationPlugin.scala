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

/** MySQL authentication plugins which can be specified at the connection start, more information could be found in <a
 *  href="https://dev.mysql.com/doc/refman/8.0/en/authentication-plugins.html">MySQL Reference Manual</a>.
 */
enum AuthenticationPlugin(val value: String) {

    /** Default authentication plugin, the client will firstly try to use the plugin name provided by the server. */
    case DEFAULT extends AuthenticationPlugin("")

    /** Authentication plugin which enables the client to send password to the server as cleartext without encryption.
     */
    case MYSQL_CLEAR_PASSWORD extends AuthenticationPlugin("mysql_clear_password")

    /** Authentication plugin which uses SHA-1 hash function to scramble the password and send it to the server. */
    case MYSQL_NATIVE_PASSWORD extends AuthenticationPlugin("mysql_native_password")

    /** Authentication plugin which uses SHA-256 hash function to scramble the password and send it to the server. */
    case SHA256_PASSWORD extends AuthenticationPlugin("sha256_password")

    /** Like [[AuthenticationPlugin.SHA256_PASSWORD]] but enables caching on the server side for better performance and
     *  with wider applicability.
     */
    case CACHING_SHA2_PASSWORD extends AuthenticationPlugin("caching_sha2_password")

}
