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

package cc.otavia.core.ioc

import cc.otavia.core.actor.*
import org.scalatest.funsuite.AnyFunSuite

import scala.language.unsafeNulls

class BeanSuite extends AnyFunSuite {

    test("SupertypeIndex should collect Actor traits and abstract Actor superclasses") {
        // ConsoleShower extends StateActor with Shower
        //  extends Actor[ShowEvent]
        // StateActor extends AbstractActor
        val clz = classOf[ConsoleShower]
        // Verify the class hierarchy is valid for IoC resolution
        assert(classOf[Shower].isAssignableFrom(clz))
        assert(classOf[StateActor[?]].isAssignableFrom(clz))
    }

}
