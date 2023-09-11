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

/** A LineString is a Curve with linear interpolation between points, it may represents a Line or a LinearRing.
 *
 *  @param points
 */
class LineString(@BeanProperty var points: List[Point]) extends Geometry()

object LineString {
    def copy(lineString: LineString): LineString = {
        val l = LineString(lineString.points)
        l.setSRID(lineString.getSRID)
        l
    }
}
