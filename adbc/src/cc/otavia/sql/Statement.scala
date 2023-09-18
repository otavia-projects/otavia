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

import cc.otavia.sql.serde.RowSerde
import cc.otavia.core.message.{Ask, Notice, Reply}

trait Statement {}

object Statement {

    case class ModifyRows(rows: Int) extends Reply

    case class ExecuteUpdate(sql: String) extends Ask[ModifyRows]

    class ExecuteQuery[R <: Row](val sql: String, val serde: RowSerde[R]) extends Ask[R]

    object ExecuteQuery {
        def apply[R <: Row](sql: String)(using serde: RowSerde[R]): ExecuteQuery[R] =
            new ExecuteQuery(sql, serde)
    }

    class ExecuteQueries[R <: Row](sql: String, serde: RowSerde[R]) extends Ask[RowSet[R]]
    object ExecuteQueries {
        def apply[R <: Row](sql: String)(using serde: RowSerde[R]): ExecuteQueries[R] =
            new ExecuteQueries(sql, serde)
    }

    case class Cursor(id: Int)                                     extends Reply
    class ExecuteCursor[R <: Row](sql: String, serde: RowSerde[R]) extends Ask[Cursor]

    case class CursorRow[R <: Row](row: R, cursorId: Int) extends Notice
    case class CursorEnd(cursorId: Int)                   extends Notice

}
