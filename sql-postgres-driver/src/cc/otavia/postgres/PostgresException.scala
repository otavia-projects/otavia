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

import cc.otavia.sql.DatabaseException
import cc.otavia.postgres.PostgresException.formatMessage

import scala.language.unsafeNulls

/** PostgreSQL error including all <a href="https://www.postgresql.org/docs/current/protocol-error-fields.html">fields
 *  of the ErrorResponse message</a> of the PostgreSQL frontend/backend protocol.
 *
 *  @param errorMessage
 *    the primary human-readable error message (<a
 *    href="https://www.postgresql.org/docs/current/protocol-error-fields.html">'M' field</a>)
 *  @param severity
 *    the severity: ERROR, FATAL, or PANIC, or a localized translation of one of these (<a
 *    href="https://www.postgresql.org/docs/current/protocol-error-fields.html">'S' field</a>)
 *  @param code
 *    use [[sqlState]] instead. the SQLSTATE code for the error (<a
 *    href="https://www.postgresql.org/docs/current/protocol-error-fields.html">'S' field</a>, <a
 *    href="https://www.postgresql.org/docs/current/errcodes-appendix.html">value list</a>), it is never localized
 *  @param detail
 *    an optional secondary error message carrying more detail about the problem (<a
 *    href="https://www.postgresql.org/docs/current/protocol-error-fields.html">'D' field</a>), a newline indicates
 *    paragraph break.
 *  @param hint
 *    an optional suggestion (advice) what to do about the problem (<a
 *    href="https://www.postgresql.org/docs/current/protocol-error-fields.html">'H' field</a>), a newline indicates
 *    paragraph break.
 *  @param position
 *    a decimal ASCII integer, indicating an error cursor position as an index into the original query string. (<a
 *    href="https://www.postgresql.org/docs/current/protocol-error-fields.html">'P' field</a>). The first character has
 *    index 1, and positions are measured in characters not bytes.
 *  @param internalPosition
 *    a decimal ASCII integer, indicating an error cursor position (<a
 *    href="https://www.postgresql.org/docs/current/protocol-error-fields.html">'p' field</a>) as an index into the
 *    internally generated command (see 'q' field).
 *  @param internalQuery
 *    the text of a failed internally-generated command (<a
 *    href="https://www.postgresql.org/docs/current/protocol-error-fields.html">'q' field</a>).
 *  @param where
 *    an indication of the context in which the error occurred (<a
 *    href="https://www.postgresql.org/docs/current/protocol-error-fields.html">'W' field</a>). Presently this includes
 *    a call stack traceback of active procedural language functions and internally-generated queries. The trace is one
 *    entry per line, most recent first.
 *  @param file
 *    file name of the source-code location where the error was reported (<a
 *    href="https://www.postgresql.org/docs/current/protocol-error-fields.html">'F' field</a>).
 *  @param line
 *    line number of the source-code location where the error was reported (<a
 *    href="https://www.postgresql.org/docs/current/protocol-error-fields.html">'L' field</a>).
 *  @param routine
 *    name of the source-code routine reporting the error (<a
 *    href="https://www.postgresql.org/docs/current/protocol-error-fields.html">'R' field</a>).
 *  @param schema
 *    if the error was associated with a specific database object, the name of the schema containing that object, if any
 *    (<a href="https://www.postgresql.org/docs/current/protocol-error-fields.html">'s' field</a>).
 *  @param table
 *    if the error was associated with a specific table, the name of the table (<a
 *    href="https://www.postgresql.org/docs/current/protocol-error-fields.html">'t' field</a>).
 *  @param column
 *    if the error was associated with a specific table column, the name of the column (<a
 *    href="https://www.postgresql.org/docs/current/protocol-error-fields.html">'c' field</a>).
 *  @param dataType
 *    if the error was associated with a specific data type, the name of the data type (<a
 *    href="https://www.postgresql.org/docs/current/protocol-error-fields.html">'d' field</a>).
 *  @param constraint
 *    if the error was associated with a specific constraint, the name of the constraint (<a
 *    href="https://www.postgresql.org/docs/current/protocol-error-fields.html">'n' field</a>).
 */
case class PostgresException(
    errorMessage: String | Null,
    severity: String | Null,
    code: String | Null,
    detail: String | Null,
    hint: String | Null,
    position: String | Null,
    internalPosition: String | Null,
    internalQuery: String | Null,
    where: String | Null,
    file: String | Null,
    line: String | Null,
    routine: String | Null,
    schema: String | Null,
    table: String | Null,
    column: String | Null,
    dataType: String | Null,
    constraint: String | Null
) extends DatabaseException(formatMessage(errorMessage, severity, code), 0, code)

object PostgresException {

    private def formatMessage(errorMessage: String | Null, severity: String | Null, code: String | Null): String = {
        val sb = new StringBuilder()
        if (severity != null) sb.append(severity).append(":")
        if (errorMessage != null) {
            if (sb.nonEmpty) sb.append(" ")
            sb.append(errorMessage)
        }
        if (code != null) {
            if (sb.nonEmpty) sb.append(" ")
            sb.append("(").append(code).append(")")
        }
        sb.toString()
    }

    def apply(
        errorMessage: String | Null,
        severity: String | Null,
        code: String | Null,
        detail: String | Null
    ): PostgresException = new PostgresException(
      errorMessage,
      severity,
      code,
      detail,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null
    )

}
