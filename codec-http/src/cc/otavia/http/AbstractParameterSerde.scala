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

package cc.otavia.http

import cc.otavia.buffer.Buffer

import scala.collection.mutable
import scala.language.unsafeNulls

abstract class AbstractParameterSerde[P] extends ParameterSerde[P] {

    private var pvrs: mutable.Map[String, String] = _
    private var pmvrs: mutable.Map[String, String] = _

    override def setPathVars(vars: mutable.Map[String, String]): Unit = pvrs = vars

    override def setParams(p: mutable.Map[String, String]): Unit = pmvrs = p

    override def pathVars: mutable.Map[String, String] = pvrs

    override def params: mutable.Map[String, String] = pmvrs

}
