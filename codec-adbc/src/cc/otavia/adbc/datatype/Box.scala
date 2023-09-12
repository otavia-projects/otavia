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

package cc.otavia.adbc.datatype

import scala.beans.BeanProperty
import scala.language.unsafeNulls

/** Rectangular box data type in Postgres represented by pairs of [[Point]]s that are opposite corners of the box. */
class Box(
    @BeanProperty var upperRightCorner: Point = new Point(),
    @BeanProperty var lowerLeftCorner: Point = new Point()
) extends Geometry() {

    override def equals(obj: Any): Boolean =
        if (this == obj) true
        else if (obj == null || getClass != obj.getClass) false
        else {
            val box = obj.asInstanceOf[Box]
            if (
              box.lowerLeftCorner == lowerLeftCorner && box.upperRightCorner == upperRightCorner &&
              box.getSRID == srid
            ) true
            else false
        }

    override def hashCode(): Int = {
        31 * upperRightCorner.hashCode() + lowerLeftCorner.hashCode() + srid.hashCode()
    }

}
