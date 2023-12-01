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

import scala.beans.BeanProperty
import scala.language.unsafeNulls

private[postgres] class Response {

    private var severity: String         = _
    private var code: String             = _
    private var message: String          = _
    private var detail: String           = _
    private var hint: String             = _
    private var position: String         = _
    private var internalPosition: String = _
    private var internalQuery: String    = _
    private var where: String            = _
    private var file: String             = _
    private var line: String             = _
    private var routine: String          = _
    private var schema: String           = _
    private var table: String            = _
    private var column: String           = _
    private var dataType: String         = _
    private var constraint: String       = _

    def getSeverity: String = severity

    def setSeverity(severity: String): Unit = {
        this.severity = severity
    }

    def getCode: String = code

    def setCode(code: String): Unit = {
        this.code = code
    }

    def getMessage: String = message

    def setMessage(message: String): Unit = {
        this.message = message
    }

    def getDetail: String = detail

    def setDetail(detail: String): Unit = {
        this.detail = detail
    }

    def getHint: String = hint

    def setHint(hint: String): Unit = {
        this.hint = hint
    }

    def getPosition: String = position

    def setPosition(position: String): Unit = {
        this.position = position
    }

    def getWhere: String = where

    def setWhere(where: String): Unit = {
        this.where = where
    }

    def getFile: String = file

    def setFile(file: String): Unit = {
        this.file = file
    }

    def getLine: String = line

    def setLine(line: String): Unit = {
        this.line = line
    }

    def getRoutine: String = routine

    def setRoutine(routine: String): Unit = {
        this.routine = routine
    }

    def getSchema: String = schema

    def setSchema(schema: String): Unit = {
        this.schema = schema
    }

    def getTable: String = table

    def setTable(table: String): Unit = {
        this.table = table
    }

    def getColumn: String = column

    def setColumn(column: String): Unit = {
        this.column = column
    }

    def getDataType: String = dataType

    def setDataType(dataType: String): Unit = {
        this.dataType = dataType
    }

    def getConstraint: String = constraint

    def setConstraint(constraint: String): Unit = {
        this.constraint = constraint
    }

    def getInternalPosition: String = internalPosition

    def setInternalPosition(internalPosition: String): Unit = {
        this.internalPosition = internalPosition
    }

    def getInternalQuery: String = internalQuery

    def setInternalQuery(internalQuery: String): Unit = {
        this.internalQuery = internalQuery
    }

    def clear(): Unit = {
        severity = null
        code = null
        message = null
        detail = null
        hint = null
        position = null
        internalPosition = null
        internalQuery = null
        where = null
        file = null
        line = null
        routine = null
        schema = null
        table = null
        column = null
        dataType = null
        constraint = null
    }

    def toExecption(): PostgresException = {
        val exception = PostgresException(
          message,
          severity,
          code,
          detail,
          hint,
          position,
          internalPosition,
          internalQuery,
          where,
          file,
          line,
          routine,
          schema,
          table,
          column,
          dataType,
          constraint
        )
        clear()
        exception
    }

}
