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

/** Base class for database failures.
 *  @param message
 *  @param errorCode
 *    Database specific error code.
 *  @param sqlState
 *    SQL State (XOPEN or SQL:2003 conventions).
 *  @param cause
 */
abstract class DatabaseException(
    message: String | Null,
    val errorCode: Int,
    val sqlState: String,
    cause: SQLException | Null
) extends RuntimeException(message, cause) {

    def this(message: String, errorCode: Int, sqlState: String) = this(message, errorCode, sqlState, null)

}
