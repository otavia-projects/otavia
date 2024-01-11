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

import cc.otavia.core.message.{Ask, Notice, Reply}
import cc.otavia.sql.Statement.ExecuteUpdate

trait Statement {}

object Statement {

    case class ModifyRows(rows: Int) extends Reply

    private[otavia] class ExecuteUpdate(override val sql: String) extends SimpleQuery[ModifyRows]

    private[otavia] class ExecuteQuery[R <: Row](override val sql: String, val decoder: RowDecoder[R])
        extends SimpleQuery[R]

    private[otavia] class ExecuteQueries[R <: Row](override val sql: String, val decoder: RowDecoder[R])
        extends SimpleQuery[RowSet[R]]

    case class Cursor(id: Int)                                         extends Reply
    class ExecuteCursor[R <: Row](sql: String, decoder: RowDecoder[R]) extends Ask[Cursor]

    case class CursorRow[R <: Row](row: R, cursorId: Int) extends Notice
    case class CursorEnd(cursorId: Int)                   extends Notice

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

    }

    sealed trait PrepareQuery[T <: Reply] extends Ask[T] {

        def sql: String
        def bind: Product | Seq[Product]
        final def isBatch: Boolean    = bind.isInstanceOf[Seq[?]]
        override def toString: String = sql

    }

    object PrepareQuery {

        def fetchOne[R <: Row](sql: String, bind: Product = EmptyTuple)(using decoder: RowDecoder[R]): PrepareQuery[R] =
            new PrepareFetchOneQuery(sql, bind, decoder)
        def fetchAll[R <: Row](sql: String, bind: Product = EmptyTuple)(using
            decoder: RowDecoder[R]
        ): PrepareQuery[RowSet[R]] =
            new PrepareFetchAllQuery(sql, bind, decoder)
        def update(sql: String, bind: Product | Seq[Product]): PrepareQuery[ModifyRows] = new PrepareUpdate(sql, bind)
        def insert(sql: String, bind: Product | Seq[Product]): PrepareQuery[ModifyRows] = new PrepareUpdate(sql, bind)
        def delete(sql: String, bind: Product | Seq[Product] = EmptyTuple): PrepareQuery[ModifyRows] =
            new PrepareUpdate(sql, bind)

    }

    private[otavia] class PrepareFetchOneQuery[R <: Row](
        override val sql: String,
        override val bind: Product | Seq[Product],
        val decoder: RowDecoder[R]
    ) extends PrepareQuery[R]

    private[otavia] class PrepareFetchAllQuery[R <: Row](
        override val sql: String,
        override val bind: Product | Seq[Product],
        val decoder: RowDecoder[R]
    ) extends PrepareQuery[RowSet[R]]

    private[otavia] class PrepareUpdate(override val sql: String, override val bind: Product | Seq[Product])
        extends PrepareQuery[ModifyRows]

}
