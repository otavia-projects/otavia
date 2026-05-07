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

import cc.otavia.core.system.ActorSystem

import java.util.concurrent.ConcurrentLinkedQueue
import scala.language.unsafeNulls

abstract class AbstractModule extends Module {

    private val listeners = new ConcurrentLinkedQueue[ModuleListener]()

    @volatile private var ld: Boolean      = false
    @volatile private var sys: ActorSystem = _

    final override protected def system: ActorSystem = sys

    override private[core] def setSystem(sys: ActorSystem): Unit = this.sys = sys

    override def loaded: Boolean = ld

    override def addListener(listener: ModuleListener): Unit = this.synchronized {
        if !ld then listeners.add(listener)
        else callback(listener)
    }

    override private[core] def onLoaded(system: ActorSystem): Unit = {
        val toNotify = this.synchronized {
            ld = true
            val drained = new java.util.ArrayList[ModuleListener]()
            var l       = listeners.poll()
            while l != null do
                drained.add(l)
                l = listeners.poll()
            drained
        }
        while (!toNotify.isEmpty) callback(toNotify.remove(0))
    }

    private def callback(listener: ModuleListener): Unit = try {
        listener.onLoaded(system)
    } catch {
        case e: Throwable =>
            System.err.println(
              s"[IoC] ModuleListener [${listener.getClass.getName}] failed in onLoaded: ${e.getMessage}"
            )
            e.printStackTrace(System.err)
    }

}
