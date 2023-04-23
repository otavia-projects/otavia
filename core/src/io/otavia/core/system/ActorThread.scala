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
import io.otavia.core.system.ActorThread.GC_PEER_ROUND
import io.otavia.core.util.SystemPropertyUtil

import java.util.concurrent.CopyOnWriteArraySet
import scala.collection.mutable
import scala.ref.ReferenceQueue

class ActorThread(private[core] val system: ActorSystem) extends Thread() {

    private val id                                              = system.pool.nextThreadId()
    private val address: ActorThreadAddress                     = new ActorThreadAddress()
    private val channelLaterTasks: mutable.ArrayDeque[Runnable] = mutable.ArrayDeque.empty

    private val houseQueueHolder = new HouseQueueHolder(this)

    private val referenceQueue = new ReferenceQueue[ActorHouse]()

    private val refSet = new CopyOnWriteArraySet[ActorHousePhantomRef]()

    setName(s"otavia-actor-thread-$index")

    def parent: ActorThreadPool = system.pool

    def index: Int = id

    private[core] def currentRunningActor(): Actor[?] = ???

    def laterTasks: mutable.ArrayDeque[Runnable] = channelLaterTasks

    def cleanChannelTask(): Unit = if (channelLaterTasks.nonEmpty) {
        // TODO: log warn
        channelLaterTasks.clear()
    }

    def actorThreadAddress: ActorThreadAddress = address

    private[core] def createActorHouse(): ActorHouse = {
        val house = new ActorHouse(houseQueueHolder)
        registerHouseRef(house)
        house
    }

    private def registerHouseRef(house: ActorHouse): Unit = {
        val ref = new ActorHousePhantomRef(house, referenceQueue)
        refSet.add(ref)
    }

    /** Stop [[Actor]] witch need be gc. */
    private def stopActor(): Unit = {
        var count    = 0
        var continue = true
        while (count < GC_PEER_ROUND && continue) {
            referenceQueue.poll match
                case None => continue = false
                case Some(ref) =>
                    refSet.remove(ref)
                    ref.clear()
                    count += 1
        }
        if (count > 0) System.gc()
    }

    override def run(): Unit = {
        ???
    }

}

object ActorThread {

    private val GC_PEER_ROUND_DEFAULT = 64

    private val GC_PEER_ROUND = SystemPropertyUtil.getInt("io.otavia.core.stop.size", GC_PEER_ROUND_DEFAULT)

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
