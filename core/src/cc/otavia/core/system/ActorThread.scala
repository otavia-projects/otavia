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
import cc.otavia.core.transport.reactor.nio.NioHandler
import cc.otavia.core.system.monitor.ActorThreadMonitor

import java.lang.ref.*
import java.util.SplittableRandom
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable
import scala.language.unsafeNulls

/** The central execution unit of the Otavia runtime. Each ActorThread is simultaneously the IO thread (polling the OS
 *  selector for network/file events) and the actor execution thread (running user business logic).
 *
 *  Every actor is pinned to a single ActorThread for its entire lifetime. All actor on the same thread are
 *  single-threaded with respect to each other, eliminating the need for locks on intra-thread coordination.
 *
 *  The event loop has three phases per iteration:
 *    1. '''Phase 1 — IO''': Poll the selector and process IO events
 *    2. '''Phase 2 — ChannelsActor dispatch''': Fully drain IO-capable actor and pending events (no time budget)
 *    3. '''Phase 3 — StateActor dispatch''': Run business logic actor within a time budget
 */
final class ActorThread(private[core] val system: ActorSystem, private val id: Int) extends Thread() {

    private val channelLaterTasks: mutable.ArrayDeque[Runnable] = mutable.ArrayDeque.empty

    private val manager = new HouseManager(this)

    private val eventQueue                  = new ConcurrentLinkedQueue[Event]()
    private val eventQueueSize              = new AtomicInteger(0)
    private val address: ActorThreadAddress = new ActorThreadAddress(this)

    private val referenceQueue = new ReferenceQueue[ActorAddress[?]]()
    private val refSet         = mutable.HashSet.empty[Reference[?]]

    @volatile private var shuttingDown: Boolean = false

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

    // =========================================================================
    // Resource management
    // =========================================================================

    private[core] def prepared(): Unit = {
        prepared0(direct)
        prepared0(heap)
    }

    private def prepared0(allocator: AbstractPooledPageAllocator): Unit = if (allocator.cacheSize == 0) {
        val buffers = 0 until allocator.minCache map (_ => allocator.allocate())
        buffers.foreach(_.close())
    }

    /** Per-thread direct memory allocator. */
    def directAllocator: AbstractPooledPageAllocator = direct

    /** Per-thread heap memory allocator. */
    def heapAllocator: AbstractPooledPageAllocator = heap

    def parent: ActorThreadPool = system.pool

    def index: Int = id

    def random: SplittableRandom = rd

    def houseManager: HouseManager = manager

    private[core] def currentRunningActor(): Actor[?] = manager.currentRunningActor

    def laterTasks: mutable.ArrayDeque[Runnable] = channelLaterTasks

    /** Clear all deferred channel tasks. Called during shutdown to release pending work. */
    def cleanChannelTask(): Unit = if (channelLaterTasks.nonEmpty) {
        // TODO: log warn
        channelLaterTasks.clear()
    }

    def actorThreadAddress: ActorThreadAddress = address

    // =========================================================================
    // Actor house management
    // =========================================================================

    private[core] def createActorHouse(): ActorHouse = {
        val house = new ActorHouse(manager)
        house.createUntypedAddress()
        house
    }

    private[system] def registerAddressRef(address: ActorAddress[?]): Unit = {
        val ref = new AddressPhantomReference(address, referenceQueue)
        refSet.add(ref)
    }

    /** Poll and clear phantom references for garbage-collected actor addresses. Returns the number of references
     *  cleared.
     */
    private def stopActors(): Int = {
        var count    = 0
        var continue = true
        while (continue) {
            val reference = referenceQueue.poll()
            if (reference != null) {
                reference.clear()
                refSet.remove(reference)
                count += 1
            } else continue = false
        }
        count
    }

    // =========================================================================
    // Cross-thread communication
    // =========================================================================

    /** Wake this thread's IO handler from another thread. Used when a cross-thread message delivery makes an actor
     *  house ready for processing.
     */
    private[core] def notifyThread(): Unit = {
        if (Thread.currentThread() != this) ioHandler.wakeup()
    }

    /** Enqueue an event for processing on this thread's Phase 2. */
    private[core] def putEvent(event: Event): Unit = {
        eventQueue.offer(event)
        eventQueueSize.incrementAndGet()
        notifyThread()
    }

    /** Enqueue multiple events for processing on this thread's Phase 2. */
    private[core] def putEvents(events: Seq[Event]): Unit = {
        events.foreach(event => eventQueue.offer(event))
        eventQueueSize.addAndGet(events.size)
        notifyThread()
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    /** Signal this thread to begin graceful shutdown. The event loop will exit after the current iteration completes. */
    private[core] def shutdown(): Unit = {
        shuttingDown = true
        ioHandler.wakeup()
    }

    // =========================================================================
    // Event loop
    // =========================================================================

    override def run(): Unit = {
        prepared()

        val ioCtx = new IoExecutionContext {
            override def canBlock: Boolean = !manager.runnable && refSet.isEmpty && eventQueueSize.get() == 0

            override def canNotBlock: Boolean = manager.runnable || eventQueueSize.get() > 0 || refSet.nonEmpty
        }

        var selectCnt = 0

        while (!confirmShutdown()) {
            // ---- Phase 1: IO (select + processSelectedKeys) ----
            val ioStartTime = System.nanoTime()
            val strategy = ioHandler.run(ioCtx)

            // Epoll bug detection: rebuild selector after excessive empty selects
            if (strategy > 0) selectCnt = 0
            else {
                selectCnt += 1
                if (unexpectedSelectorWakeup(selectCnt)) selectCnt = 0
            }

            // ---- Phase 2: IO pipeline (ChannelsActor + Events + Cleanup) ----
            manager.runChannelsActors()
            this.runThreadEvent()
            if (refSet.nonEmpty) this.stopActors()

            // ---- Phase 3: Business logic (StateActor) with time budget ----
            val now = System.nanoTime()
            val deadline = computeDeadline(ioStartTime, now, strategy)
            manager.runStateActors(deadline)

            // ---- Phase 4: Work stealing (only when idle) ----
            if (!shuttingDown) manager.trySteal()
        }
    }

    private def computeDeadline(ioStartTime: Long, now: Long, strategy: Int): Long = {
        if (ioRatio == 100) return Long.MaxValue
        val ioTime = now - ioStartTime
        if (strategy <= 0) return now + minActorBudgetNanos
        now + ioTime * (100 - ioRatio) / ioRatio
    }

    private def unexpectedSelectorWakeup(selectCnt: Int): Boolean = {
        if (Thread.interrupted()) true
        else if (SELECTOR_AUTO_REBUILD_THRESHOLD > 0 && selectCnt >= SELECTOR_AUTO_REBUILD_THRESHOLD) {
            ioHandler match {
                case nio: NioHandler => nio.rebuildSelector()
                case _               =>
            }
            true
        } else false
    }

    private def runThreadEvent(): Boolean = {
        val run = eventQueueSize.get() > 0
        while (eventQueueSize.get() > 0) {
            val event = eventQueue.poll().asInstanceOf[ResourceTimeoutEvent]
            eventQueueSize.decrementAndGet()
            event.cache.parent.handleTimeout(event.registerId, event.cache)
        }
        run
    }

    def monitor(): ActorThreadMonitor = ActorThreadMonitor(eventQueueSize.get(), manager.monitor())

    private def confirmShutdown(): Boolean = shuttingDown

}

object ActorThread {

    /** IO ratio: percentage of event loop time allocated to IO. The remaining time is allocated to StateActor
     *  execution. Default is 50 (equal time for IO and actor). Set to 100 to disable time budgeting.
     */
    private val ioRatio = SystemPropertyUtil.getInt("cc.otavia.actor.io.ratio", 50)

    /** Minimum time budget (in nanoseconds) for StateActor execution when there are no IO events. Prevents actor
     *  starvation when the system is idle from an IO perspective.
     */
    private val minActorBudgetNanos =
        SystemPropertyUtil.getInt("cc.otavia.actor.min.budget.microsecond", 500) * 1000L

    /** The number of consecutive premature Selector returns before rebuilding the Selector to work around the
     *  epoll 100% CPU bug. Set to 0 to disable auto-rebuild.
     */
    private val SELECTOR_AUTO_REBUILD_THRESHOLD =
        SystemPropertyUtil.getInt("io.otavia.selectorAutoRebuildThreshold", 512)

    /** Returns the currently executing [[ActorThread]]. Throws if the current thread is not an ActorThread. */
    def currentThread(): ActorThread = Thread.currentThread().asInstanceOf[ActorThread]

    /** Returns the [[ActorThread.index]] of the currently executing [[ActorThread]]. */
    def currentThreadIndex: Int = currentThread().index

    /** Check whether the current [[Thread]] is an [[ActorThread]]. */
    final def currentThreadIsActorThread: Boolean = Thread.currentThread().isInstanceOf[ActorThread]

    /** Borrow the current thread's scratch [[mutable.ArrayBuffer]], cast to the requested type.
     *  '''Warning''': Not reentrant. Only one buffer of any type may be in use at a time per thread.
     */
    final def threadBuffer[T]: mutable.ArrayBuffer[T] = Thread.currentThread() match
        case thread: ActorThread => thread.mutableBuffer.asInstanceOf[mutable.ArrayBuffer[T]]
        case _                   => mutable.ArrayBuffer.empty[T]

    /** Borrow the current thread's scratch [[mutable.HashSet]], cast to the requested type.
     *  '''Warning''': Not reentrant. Only one set of any type may be in use at a time per thread.
     */
    final def threadSet[T]: mutable.HashSet[T] = Thread.currentThread() match
        case thread: ActorThread => thread.mutableSet.asInstanceOf[mutable.HashSet[T]]
        case _                   => mutable.HashSet.empty[T]

    /** Borrow the current thread's scratch [[mutable.HashMap]], cast to the requested type.
     *  '''Warning''': Not reentrant. Only one map of any type may be in use at a time per thread.
     */
    final def threadMap[K, V]: mutable.HashMap[K, V] = Thread.currentThread() match
        case thread: ActorThread => thread.mutableMap.asInstanceOf[mutable.HashMap[K, V]]
        case _                   => mutable.HashMap.empty

}
