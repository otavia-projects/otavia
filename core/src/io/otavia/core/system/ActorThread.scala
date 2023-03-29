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

package io.otavia.core.system

import io.otavia.core.actor.Actor
import io.otavia.core.address.ActorThreadAddress

import scala.collection.mutable

class ActorThread(private[core] val system: ActorSystem, val parent: ActorThreadPool) extends Thread() {

    private val id                                              = parent.nextThreadId()
    private val address: ActorThreadAddress                     = new ActorThreadAddress()
    private val channelLaterTasks: mutable.ArrayDeque[Runnable] = mutable.ArrayDeque.empty

    setName(s"otavia-actor-thread-$index")

    def index: Int = id

    private[core] def currentRunningActor(): Actor[?] = ???

    def laterTasks: mutable.ArrayDeque[Runnable] = channelLaterTasks

    def cleanChannelTask(): Unit = if (channelLaterTasks.nonEmpty) {
        // TODO: log warn
        channelLaterTasks.clear()
    }

    def actorThreadAddress: ActorThreadAddress = address

}

object ActorThread {

    /** Returns a reference to the currently executing [[ActorThread]] object.
     *
     *  @return
     *    the currently executing thread.
     */
    def currentThread(): ActorThread = Thread.currentThread().asInstanceOf[ActorThread]

    /** Returns the [[ActorThread.index]] of the currently executing [[ActorThread]] object.
     *  @return
     *    [[ActorThread]] index
     */
    def currentThreadIndex: Int = currentThread().index

    /** Check whether the current [[Thread]] is [[ActorThread]]. */
    final def currentThreadIsActorThread: Boolean = Thread.currentThread().isInstanceOf[ActorThread]

}
