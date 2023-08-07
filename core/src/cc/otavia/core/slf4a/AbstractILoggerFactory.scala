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

package cc.otavia.core.slf4a

import cc.otavia.core.ioc.Module
import cc.otavia.core.system.ActorSystem

abstract class AbstractILoggerFactory extends ILoggerFactory {

    @volatile private var loaded: Boolean = false

    def module: Module

    override def getLogger(name: String, system: ActorSystem): AbstractLogger = {
        val logger = getLogger0(name, system)
        
        if (!loaded) {
            system.loadModule(module)
            loaded = true
        }

        module.addListener(logger)

        logger
    }

    def getLogger0(name: String, system: ActorSystem): AbstractLogger

}
