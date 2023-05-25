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
import io.otavia.core.address.{ActorAddress, ActorThreadAddress}
import io.otavia.core.message.{Event, ResourceTimeoutEvent}
import io.otavia.core.system.ActorThread.{GC_PEER_ROUND, ST_RUNNING, ST_STARTING, ST_WAITING}
import io.otavia.core.system.monitor.ActorThreadMonitor
import io.otavia.core.util.SystemPropertyUtil

import java.lang.ref.{PhantomReference, ReferenceQueue, SoftReference, WeakReference}
import java.util.concurrent.{ConcurrentLinkedQueue, CopyOnWriteArraySet}
import scala.collection.mutable
import scala.language.unsafeNulls

class ActorThread(private[core] val system: ActorSystem) extends Thread() {

    private val id = system.pool.nextThreadId()

    private val channelLaterTasks: mutable.ArrayDeque[Runnable] = mutable.ArrayDeque.empty

    private val manager = new HouseManager(this)

    private val eventQueue                  = new ConcurrentLinkedQueue[Event]()
    private val address: ActorThreadAddress = new ActorThreadAddress(this)

    private val referenceQueue = new ReferenceQueue[ActorAddress[?]]()
    private val refSet         = new CopyOnWriteArraySet[AddressPhantomReference]()

    @volatile private var status: Int = ST_STARTING

    setName(s"otavia-actor-thread-$index")

    def parent: ActorThreadPool = system.pool

    def index: Int = id

    def houseManager: HouseManager = manager

    private[core] def currentRunningActor(): Actor[?] = ???

    def laterTasks: mutable.ArrayDeque[Runnable] = channelLaterTasks

    def cleanChannelTask(): Unit = if (channelLaterTasks.nonEmpty) {
        // TODO: log warn
        channelLaterTasks.clear()
    }

    def actorThreadAddress: ActorThreadAddress = address

    private[core] def createActorHouse(): ActorHouse = {
        val house = new ActorHouse(manager)
        house
    }

    private[system] def registerAddressRef(address: ActorAddress[?]): Unit = {
        val ref = new AddressPhantomReference(address, referenceQueue)
        refSet.add(ref)
    }

    /** Stop [[Actor]] witch need be gc. */
    private def stopActor(): Int = {
        var count    = 0
        var continue = true
        while (count < GC_PEER_ROUND && continue) {
            referenceQueue.poll match
                case null => continue = false
                case ref: AddressPhantomReference =>
                    ref.finalizeResources()
                    ref.clear()
                    refSet.remove(ref)
                    count += 1
                case reference: PhantomReference[?] => reference.clear()
        }
        count
    }

    private[core] def notifyThread(): Unit = {
        if (status == ST_WAITING) this.synchronized(this.notify())
    }

    private[core] def putEvent(event: Event): Unit = {
        eventQueue.offer(event)
        if (status == ST_WAITING) this.synchronized(this.notify())
    }

    private[core] def putEvents(events: Seq[Event]): Unit = {
        events.foreach(event => eventQueue.offer(event))
        if (status == ST_WAITING) this.synchronized(this.notify())
    }

    override def run(): Unit = {
        status = ST_RUNNING

        var spinStart: Long  = System.nanoTime()
        var emptyTimes: Long = 0
        var gc               = false
        while (true) {
            var success: Boolean = false
            // run current thread tasks
            val stops    = this.stopActor()
            val runHouse = manager.run()
            val runEvent = this.runThreadEvent()

            if (stops > 0 || runHouse || runEvent) success = true

            val currentNanoTime = System.nanoTime()

            if (success) {
                spinStart = currentNanoTime
                emptyTimes = 0
                gc = false
            } else {
                emptyTimes += 1
                if (emptyTimes >= 20 && currentNanoTime - spinStart > 100 * 1000) {
                    if (manager.steal()) { emptyTimes = 10 }
                }
            }

            if (emptyTimes > 60 && currentNanoTime - spinStart > 10 * 1000 * 1000) {
                this.suspendThread(20)
                status = ST_RUNNING
                if (currentNanoTime - spinStart > 1000 * 1000 * 1000 && !gc) {
                    System.gc()
                    gc = true
                    println(s"$getName GC")
                }
            }
        }
    }

    final private def suspendThread(millis: Long = 50): Unit = {
        this.synchronized {
            status = ST_WAITING
            this.wait(millis)
        }
    }

    final private def runThreadEvent(): Boolean = {
        if (!eventQueue.isEmpty) {
            val event = eventQueue.poll().asInstanceOf[ResourceTimeoutEvent]

            event.cache.parent.handleTimeout(event.registerId, event.cache)
            true
        } else false
    }

    def monitor(): ActorThreadMonitor = ActorThreadMonitor(eventQueue.size(), manager.monitor())

}

object ActorThread {

    private val GC_PEER_ROUND_DEFAULT = 64

    private val GC_PEER_ROUND = SystemPropertyUtil.getInt("io.otavia.core.stop.size", GC_PEER_ROUND_DEFAULT)

    /** Status of [[ActorThread]]: starting to loop schedule. */
    private val ST_STARTING: Int = 0

    /** Status of [[ActorThread]]: task is running. */
    private val ST_RUNNING: Int = 1

    /** Status of [[ActorThread]]: no task to run, so thread is waiting. */
    private val ST_WAITING: Int = 2

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
