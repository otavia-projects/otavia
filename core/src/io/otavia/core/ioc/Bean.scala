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

package io.otavia.core.ioc

import io.otavia.core.actor.{AbstractActor, Actor, ActorFactory}
import io.otavia.core.address.Address

import scala.collection.mutable
import scala.language.unsafeNulls

class Bean(val clz: Class[?], val address: Address[?], val primary: Boolean = false) {

    private val superCls = Bean.getSupers(clz)

    def superClasses(): mutable.Set[String] = superCls

    def name: String = clz.getName

}

object Bean {

    private val actorClass         = classOf[Actor[?]]
    private val abstractActorClass = classOf[AbstractActor[?]]

    private def getSupers(clz: Class[?]): mutable.Set[String] = {
        val set = mutable.Set.empty[String]
        estimate(clz, set)
        set
    }

    private def estimate(clz: Class[?], set: mutable.Set[String]): Unit = {
        if (clz.isInterface && actorClass.isAssignableFrom(clz) && clz.getTypeParameters.isEmpty) {
            set.add(clz.getName)
            clz.getInterfaces.foreach { intf => estimate(intf, set) }
        } else if (abstractActorClass.isAssignableFrom(clz) && clz.getTypeParameters.isEmpty) {
            set.add(clz.getName)
            val sp = clz.getSuperclass
            if (sp != null) {
                estimate(sp, set)
            }
            clz.getInterfaces.foreach { intf => estimate(intf, set) }
        }
    }

    def apply(clz: Class[?], address: Address[?], primary: Boolean): Bean = new Bean(clz, address, primary)

}
