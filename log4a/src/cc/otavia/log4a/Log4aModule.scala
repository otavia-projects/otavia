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

package cc.otavia.log4a

import cc.otavia.core.ioc.{AbstractModule, BeanDefinition}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class Log4aModule extends AbstractModule {

    private val beans = new ArrayBuffer[BeanDefinition]()

    override def definitions: Seq[BeanDefinition] = beans.toSeq

    def addDefinition(definition: BeanDefinition): Unit = {
        beans.addOne(definition)
    }

    override def toString: String = "Log4a"

}
