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

trait Statement {}

object Statement {

    case class ModifyRows(rows: Int) extends Reply

    case class ExecuteUpdate(sql: String) extends Ask[ModifyRows]

    class ExecuteQuery[R <: Row](val sql: String, val decoder: RowDecoder[R]) extends Ask[R]

    object ExecuteQuery {
        def apply[R <: Row](sql: String)(using decoder: RowDecoder[R]): ExecuteQuery[R] =
            new ExecuteQuery(sql, decoder)
    }

    case class ExecuteQueries[R <: Row](sql: String, decoder: RowDecoder[R]) extends Ask[RowSet[R]]

    case class Cursor(id: Int)                                         extends Reply
    class ExecuteCursor[R <: Row](sql: String, decoder: RowDecoder[R]) extends Ask[Cursor]

    case class CursorRow[R <: Row](row: R, cursorId: Int) extends Notice
    case class CursorEnd(cursorId: Int)                   extends Notice

    trait PrepareQuery[T <: Reply] extends Ask[T] {

        def sql: String
        def bind: Product | Seq[Product]
        def isBatch: Boolean

    }

    type BIND = Product | Seq[Product]

    object PrepareQuery {

        def fetchOne[R <: Row](sql: String, bind: BIND)(using decoder: RowDecoder[R]): PrepareFetchOneQuery[R] =
            new PrepareFetchOneQuery(sql, bind, decoder)
        def fetchAll[R <: Row](sql: String, bind: BIND)(using decoder: RowDecoder[R]) =
            new PrepareFetchAllQuery[R](sql, bind, decoder)
        def update(sql: String, bind: BIND) = new PrepareUpdate(sql, bind)
        def insert(sql: String, bind: BIND) = new PrepareUpdate(sql, bind)

    }

    class PrepareFetchOneQuery[R <: Row](
        override val sql: String,
        override val bind: Product | Seq[Product],
        val decoder: RowDecoder[R]
    ) extends PrepareQuery[R] {
        override def isBatch: Boolean = bind.isInstanceOf[Seq[?]]
    }

    class PrepareFetchAllQuery[R <: Row](
        override val sql: String,
        override val bind: Product | Seq[Product],
        val decoder: RowDecoder[R]
    ) extends PrepareQuery[R] {
        override def isBatch: Boolean = bind.isInstanceOf[Seq[?]]
    }

    class PrepareUpdate(override val sql: String, override val bind: Product | Seq[Product])
        extends PrepareQuery[ModifyRows] {
        override def isBatch: Boolean = bind.isInstanceOf[Seq[?]]
    }

}
