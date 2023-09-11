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

/** A MultiPoint is a geometry collection composed of Point elements. The points are not connected or ordered in any
 *  way.
 *
 *  @param points
 */
class MultiPoint(@BeanProperty var points: List[Point]) extends Geometry()

object MultiPoint {
    def copy(multiPoint: MultiPoint): MultiPoint = {
        val mp = MultiPoint(multiPoint.points)
        mp.setSRID(multiPoint.getSRID)
        mp
    }
}
