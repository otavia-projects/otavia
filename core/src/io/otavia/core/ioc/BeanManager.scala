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

import io.otavia.core.actor.{Actor, ActorFactory}
import io.otavia.core.address.Address
import io.otavia.core.message.Call
import io.otavia.core.system.ActorSystem

import java.util.concurrent.ConcurrentHashMap
import scala.reflect.ClassTag

private[core] class BeanManager(val system: ActorSystem) {

    private val beans      = new ConcurrentHashMap[String, Bean]()
    private val qualifiers = new ConcurrentHashMap[String, String]()

    def register(
        clz: Class[?],
        factory: ActorFactory[?],
        num: Int,
        qualifier: Option[String] = None,
        primary: Boolean = false
    ): Unit = {
        val bean = Bean(clz, factory, num, primary)
        beans.put(bean.name, bean)

        qualifier match
            case None        =>
            case Some(value) => qualifiers.put(value, bean.name)
    }

    def register(entry: BeanEntry): Unit = {
        val bean = Bean(entry.beanClz, entry.factory, entry.num, entry.primary)
        beans.put(bean.name, bean)

        entry.qualifier match
            case Some(value) => qualifiers.put(value, bean.name)
            case None        =>
    }

    def getBean(qualifier: String): Address[?] = ???

}
