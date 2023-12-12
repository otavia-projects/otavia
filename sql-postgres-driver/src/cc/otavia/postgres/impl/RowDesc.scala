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

package cc.otavia.postgres.impl

import cc.otavia.postgres.RowDecodeException

import scala.collection.mutable.ArrayBuffer
import scala.collection.{IndexedSeqView, mutable}
import scala.language.unsafeNulls

private[postgres] class RowDesc {

    private var len: Int                              = 0
    private var colCapacity: Int                      = 16
    private val cols: mutable.ArrayBuffer[ColumnDesc] = new ArrayBuffer[ColumnDesc]()

    init()

    private def init(): Unit = {
        0 until colCapacity foreach (i => cols.addOne(new ColumnDesc()))
    }

    def setLength(len: Int): Unit = {
        this.len = len
        if (len > colCapacity) {
            0 until (len - colCapacity) foreach (i => cols.addOne(new ColumnDesc()))
            colCapacity = len
        } else if (len < colCapacity) {
            0 until (colCapacity - len) foreach (i => apply(i + len).clear())
        }
    }

    def length: Int = len

    def apply(index: Int): ColumnDesc = {
        val col = cols(index)
        col
    }

    def columnNames: IndexedSeqView[String] = cols.view.slice(0, len).map(_.name)

    def columns: IndexedSeqView[ColumnDesc] = cols.view.slice(0, len)

    def queryColumnIndex(columnName: String): Int = {
        var index                  = 0
        var found: Boolean         = false
        var columnDesc: ColumnDesc = null
        while (!found && index < len) {
            val desc = cols(index)
            if (desc.name == columnName) {
                found = true
                columnDesc = desc
            }
            index += 1
        }
        if (columnDesc != null) index
        else
            throw new RowDecodeException(
              s"column name [$columnName] not found, column names can be ${columnNames.mkString("[", ", ", "]")}"
            )
    }

}
