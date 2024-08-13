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

package cc.otavia.sql.statement

import cc.otavia.core.message.{Ask, Reply}
import cc.otavia.sql.{Row, RowDecoder, RowSet}

sealed trait SimpleQuery[T <: Reply] extends Ask[T] {

    def sql: String

    override def toString: String = sql

}

object SimpleQuery {

    def fetchOne[R <: Row](sql: String)(using decoder: RowDecoder[R]): ExecuteQuery[R] =
        new ExecuteQuery(sql, decoder)

    def fetchAll[R <: Row](sql: String)(using decoder: RowDecoder[R]): ExecuteQueries[R] =
        new ExecuteQueries[R](sql, decoder)

    def update(sql: String): SimpleQuery[ModifyRows] = new ExecuteUpdate(sql)

    def delete(sql: String): SimpleQuery[ModifyRows] = new ExecuteUpdate(sql)

    def insert(sql: String): SimpleQuery[ModifyRows] = new ExecuteUpdate(sql)

    def create(sql: String): SimpleQuery[ModifyRows] = new ExecuteUpdate(sql)

    private[otavia] class ExecuteUpdate(override val sql: String) extends SimpleQuery[ModifyRows]

    private[otavia] class ExecuteQuery[R <: Row](override val sql: String, val decoder: RowDecoder[R])
        extends SimpleQuery[R]

    private[otavia] class ExecuteQueries[R <: Row](override val sql: String, val decoder: RowDecoder[R])
        extends SimpleQuery[RowSet[R]]

}
