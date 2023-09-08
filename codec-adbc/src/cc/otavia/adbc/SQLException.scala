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

package cc.otavia.adbc

/** An exception that provides information on a database access error or other errors.
 *
 *  Each SQLException provides several kinds of information:
 *    - a string describing the error. This is used as the Java Exception message, available via the method getMesasge.
 *    - a "SQLstate" string, which follows either the XOPEN SQLstate conventions or the SQL:2003 conventions. The values
 *      of the SQLState string are described in the appropriate spec. The DatabaseMetaData method getSQLStateType can be
 *      used to discover whether the driver returns the XOPEN type or the SQL:2003 type.
 *    - an integer error code that is specific to each vendor. Normally this will be the actual error code returned by
 *      the underlying database.
 *    - a chain to a next Exception. This can be used to provide additional error information.
 *    - the causal relationship, if any for this SQLException.
 *  @param message
 *  @param cause
 */
class SQLException(message: String | Null, cause: Throwable | Null)
    extends Exception(message, cause)
    with Iterable[Throwable] {

    private var SQLState: String = _
    private var vendorCode: Int  = 0

    private var next: SQLException = _

    def this() = this(null, null)

    def this(message: String) = this(message, null)

    def this(cause: Throwable) = this(null, cause)

    override def iterator: Iterator[Throwable] = ???

}
