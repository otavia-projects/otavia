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

package cc.otavia.sql.datatype

import scala.beans.BeanProperty

/** Circle data type in Postgres represented by a center [[Point]] and radius. */
class Circle(@BeanProperty var centerPoint: Point = new Point(), @BeanProperty var radius: Double = 0.0)
    extends Geometry() {

    override def equals(obj: Any): Boolean = if (this == obj) true
    else if (obj == null || getClass != obj.getClass) false
    else {
        val circle = obj.asInstanceOf[Circle]
        if (circle.radius == radius && circle.centerPoint.equals(centerPoint) && circle.getSRID == srid) true else false
    }

    override def toString: String = s"Circle(${centerPoint}, $radius)"

}
