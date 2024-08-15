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

import cc.otavia.core.message.{Ask, Notice, Reply}
import cc.otavia.sql.{Row, RowCodec}

case class Cursor(id: Int) extends Reply

class ExecuteCursor[R <: Row](sql: String, decoder: RowCodec[R]) extends Ask[Cursor]

case class CursorRow[R <: Row](row: R, cursorId: Int) extends Notice

case class CursorEnd(cursorId: Int) extends Notice
