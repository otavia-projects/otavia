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

/** A Polygon is a planar Surface representing a multisided geometry. It is defined by a single exterior boundary and
 *  zero or more interior boundaries, where each interior boundary defines a hole in the Polygon.
 *
 *  @param lineStrings
 */
class Polygon(@BeanProperty var lineStrings: List[LineString]) extends Geometry()

object Polygon {
    def copy(polygon: Polygon): Polygon = {
        val p = Polygon(polygon.lineStrings)
        p.setSRID(polygon.getSRID)
        p
    }
}
