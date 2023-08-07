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

import cc.otavia.core.actor.{Actor, ActorFactory}
import cc.otavia.core.address.Address
import cc.otavia.core.message.Call
import cc.otavia.core.system.ActorSystem

import java.util.concurrent.ConcurrentHashMap
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.language.unsafeNulls
import scala.reflect.ClassTag

private[core] class BeanManager(val system: ActorSystem) {

    private val beans      = new ConcurrentHashMap[String, Bean]()
    private val qualifiers = new ConcurrentHashMap[String, String]()
    private val superTypes = new ConcurrentHashMap[String, mutable.Buffer[String]]()

    def register(
        clz: Class[?],
        address: Address[?],
        qualifier: Option[String] = None,
        primary: Boolean = false
    ): Unit = {
        val bean = Bean(clz, address, primary)
        beans.put(bean.name, bean)

        qualifier match
            case Some(name) => qualifiers.put(name, bean.name)
            case None       =>

        for (sp <- bean.superClasses() if sp != bean.name) {
            var value: mutable.Buffer[String] = null

            if (!superTypes.containsKey(sp)) value = new ArrayBuffer[String]() else value = superTypes.get(sp)

            value.addOne(bean.name)
        }
    }

    def getBean(qualifier: String, clz: Class[?]): Address[?] = {
        if (qualifiers.containsKey(qualifier)) {
            val name = qualifiers.get(qualifier)
            val bean = beans.get(name)
            if (clz.isAssignableFrom(bean.clz)) bean.address else throw new IllegalStateException()
        } else throw new IllegalArgumentException()
    }

    def getBean(clz: Class[?]): Address[?] = {
        val spName = clz.getName
        if (superTypes.containsKey(spName)) {
            val seq = superTypes.get(spName)
            if (seq.length == 1) {
                val bean = beans.get(seq.head)
                bean.address
            } else {
                var bean: Bean = null

                for (name <- seq) {
                    val b = beans.get(name)
                    if (b.primary) {
                        if (bean == null) bean = b else throw new IllegalStateException()
                    }
                }

                if (bean != null) bean.address else throw new IllegalStateException()
            }

        } else throw new IllegalStateException()
    }

    def count: Int = beans.size()

}
