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

import io.otavia.core.slf4a.Logger
import io.otavia.core.slf4a.helpers.Util
import io.otavia.core.system.ActorSystem

import java.util.concurrent.ConcurrentLinkedQueue
import scala.language.unsafeNulls

abstract class AbstractModule extends Module {

    private val listeners = new ConcurrentLinkedQueue[ModuleListener]()

    @volatile private var ld: Boolean         = false
    @volatile private var system: ActorSystem = _

    override def loaded: Boolean = ld

    override def addListener(listener: ModuleListener): Unit = {
        if (!ld) listeners.add(listener)
        else callback(listener)
    }

    override private[core] def onLoaded(system: ActorSystem): Unit = {
        this.system = system
        ld = true
        while (!listeners.isEmpty) {
            val listener = listeners.poll()
            callback(listener)
        }
    }

    private final def callback(listener: ModuleListener): Unit = try {
        listener.onLoaded(system)
    } catch {
        case e: Throwable =>
    }

}
