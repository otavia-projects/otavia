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

import cc.otavia.postgres.protocol.{DataFormat, DataType}

import java.sql.JDBCType
import scala.language.unsafeNulls

private[postgres] class ColumnDesc {

    var name: String           = _
    var relationId             = 0
    var dataType: DataType     = _
    var dataFormat: DataFormat = _
    var relationAttributeNo    = 0
    var length                 = 0
    var typeModifier           = 0

    def isArray: Boolean = dataType.array

    def typeName: String = dataType.toString

    def jdbcType: JDBCType = dataType.jdbcType

    def clear(): Unit = {
        name = null
        relationId = 0
        dataType = null
        dataFormat = null
        relationAttributeNo = 0
        length = 0
        typeModifier = 0
    }

    override def toString: String = if (name != null) s"Column(name=$name, type=${dataType})" else "NULL"

}
