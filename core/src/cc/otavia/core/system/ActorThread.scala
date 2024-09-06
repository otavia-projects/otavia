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

package cc.otavia.core.system

import cc.otavia.buffer.pool.{AbstractPooledPageAllocator, DirectPooledPageAllocator, HeapPooledPageAllocator}
import cc.otavia.common.SystemPropertyUtil
import cc.otavia.core.actor.Actor
import cc.otavia.core.address.{ActorAddress, ActorThreadAddress}
import cc.otavia.core.message.{Event, ResourceTimeoutEvent}
import cc.otavia.core.reactor.IoExecutionContext
import cc.otavia.core.system.ActorThread.*
import cc.otavia.core.system.monitor.ActorThreadMonitor

import java.lang.ref.*
import java.util.SplittableRandom
import java.util.concurrent.ConcurrentLinkedQueue
import scala.collection.mutable
import scala.language.unsafeNulls

final class ActorThread(private[core] val system: ActorSystem) extends Thread() {

    private val id = system.pool.nextThreadId()

    private val channelLaterTasks: mutable.ArrayDeque[Runnable] = mutable.ArrayDeque.empty

    private val manager = new HouseManager(this)

    private val eventQueue                  = new ConcurrentLinkedQueue[Event]()
    private val address: ActorThreadAddress = new ActorThreadAddress(this)

    private val referenceQueue = new ReferenceQueue[ActorAddress[?]]()
    private val refSet         = mutable.HashSet.empty[Reference[?]]

    @volatile private var status: Int = ST_STARTING
    private val lock: AnyRef          = new Object()

    private val direct = new DirectPooledPageAllocator(
      ActorSystem.PAGE_SIZE,
      ActorSystem.ALLOCATOR_MIN_CACHE_SIZE,
      ActorSystem.ALLOCATOR_MAX_CACHE_SIZE
    )

    private val heap = new HeapPooledPageAllocator(
      ActorSystem.PAGE_SIZE,
      ActorSystem.ALLOCATOR_MIN_CACHE_SIZE,
      ActorSystem.ALLOCATOR_MAX_CACHE_SIZE
    )

    private val mutableBuffer: mutable.ArrayBuffer[AnyRef]  = mutable.ArrayBuffer.empty
    private val mutableSet: mutable.HashSet[AnyRef]         = mutable.HashSet.empty
    private val mutableMap: mutable.HashMap[AnyRef, AnyRef] = mutable.HashMap.empty

    private val rd: SplittableRandom = new SplittableRandom()

    private[core] val ioHandler = system.transportFactory.openIoHandler(system)

    setName(s"otavia-worker-$index")

    // prepare allocate buffer
    private[core] def prepared(): Unit = {
        prepared0(direct)
        prepared0(heap)
    }

    private def prepared0(allocator: AbstractPooledPageAllocator): Unit = if (allocator.cacheSize == 0) {
        val buffers = 0 until allocator.minCache map (_ => allocator.allocate())
        buffers.foreach(_.close())
    }

    /** A [[BufferAllocator]] which allocate heap memory. */
    def directAllocator: AbstractPooledPageAllocator = direct

    /** A [[BufferAllocator]] which allocate heap memory. */
    def heapAllocator: AbstractPooledPageAllocator = heap

    def parent: ActorThreadPool = system.pool

    def index: Int = id

    def random: SplittableRandom = rd

    def houseManager: HouseManager = manager

    private[core] def currentRunningActor(): Actor[?] = manager.currentRunningActor

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
    private def stopActors(): Int = {
        var count    = 0
        var continue = true
        while (count < GC_PEER_ROUND && continue) {
            val reference = referenceQueue.poll()
            if (reference != null) {
                reference.clear()
                refSet.remove(reference)
                count += 1
            } else continue = false
        }
        count
    }

    private[core] def notifyThread(): Unit = {
        if (Thread.currentThread() != this) ioHandler.wakeup()
        // if (status == ST_WAITING) lock.synchronized(lock.notify())
    }

    private[core] def putEvent(event: Event): Unit = {
        eventQueue.offer(event)
        notifyThread()
    }

    private[core] def putEvents(events: Seq[Event]): Unit = {
        events.foreach(event => eventQueue.offer(event))
        notifyThread()
    }

//    override def run(): Unit = {
//        status = ST_RUNNING
//        var spinStart: Long  = System.currentTimeMillis()
//        var emptyTimes: Long = 0
//        var gc               = false
//        while (true) {
//
//            ioHandler.run(ioCtx)
//
//            var success: Boolean = false
//            // run current thread tasks
//            val stops    = if (refSet.isEmpty) 0 else this.stopActors()
//            val runHouse = manager.run()
//            val runEvent = this.runThreadEvent()
//
//            if (stops > 0 || runHouse) success = true
//
//            if (success) {
//                emptyTimes = 0
//                gc = false
//            } else {
//                emptyTimes += 1
//                if (emptyTimes == 1) spinStart = System.currentTimeMillis()
//                else if (emptyTimes < 20) Thread.`yield`()
//                else if (emptyTimes < 25) {
//                    if (manager.trySteal()) emptyTimes -= 1
//                } else if (emptyTimes < 50) {
//                    this.suspendThread(5)
//                } else {
//                    this.suspendThread()
//                    if (emptyTimes % 100 == 0) if (manager.trySteal()) emptyTimes = 20
//                    if (System.currentTimeMillis() - spinStart > 600) {
//                        if (ActorSystem.AGGRESSIVE_GC && !gc) {
//                            system.gc()
//                            gc = true
//                        }
//                        if (directAllocator.releasable) directAllocator.release()
//                        if (heapAllocator.releasable) heapAllocator.release()
//                    }
//                }
//            }
//        }
//    }

    override def run(): Unit = {
        status = ST_RUNNING
        val ioCtx = new IoExecutionContext {
            override def canBlock: Boolean = !manager.runnable && refSet.isEmpty && eventQueue.isEmpty
        }

        while (!confirmShutdown()) {
            // run current thread tasks
            val stops    = if (refSet.isEmpty) 0 else this.stopActors() // stop and release died actor.
            val runHouse = manager.run()                                // run actor which has been received message.
            val runEvent = this.runThreadEvent()                        // run event received by this thread.

            ioHandler.run(ioCtx)
        }

    }

    private def runLaterTasks(): Unit = {
        while (channelLaterTasks.nonEmpty) {
            val task = channelLaterTasks.removeHead()
            try task.run()
            catch {
                case t: Throwable => t.printStackTrace()
            }
        }
    }

    private def suspendThread(millis: Long = 50): Unit = {
        lock.synchronized {
            status = ST_WAITING
            lock.wait(millis)
            status = ST_RUNNING
        }
    }

    private def runThreadEvent(): Boolean = {
        if (!eventQueue.isEmpty) {
            val event = eventQueue.poll().asInstanceOf[ResourceTimeoutEvent]

            event.cache.parent.handleTimeout(event.registerId, event.cache)
            true
        } else false
    }

    def monitor(): ActorThreadMonitor = ActorThreadMonitor(eventQueue.size(), manager.monitor())

    private def confirmShutdown(): Boolean = false

}

object ActorThread {

    private val GC_PEER_ROUND_DEFAULT = 64

    private val GC_PEER_ROUND = SystemPropertyUtil.getInt("cc.otavia.core.stop.size", GC_PEER_ROUND_DEFAULT)

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

    final def threadBuffer[T]: mutable.ArrayBuffer[T] = Thread.currentThread() match
        case thread: ActorThread => thread.mutableBuffer.asInstanceOf[mutable.ArrayBuffer[T]]
        case _                   => mutable.ArrayBuffer.empty[T]

    final def threadSet[T]: mutable.HashSet[T] = Thread.currentThread() match
        case thread: ActorThread => thread.mutableSet.asInstanceOf[mutable.HashSet[T]]
        case _                   => mutable.HashSet.empty[T]

    final def threadMap[K, V]: mutable.HashMap[K, V] = Thread.currentThread() match
        case thread: ActorThread => thread.mutableMap.asInstanceOf[mutable.HashMap[K, V]]
        case _                   => mutable.HashMap.empty

}
