/*
 * Copyright 2022 Yan Kun <yan_kun_1992@foxmail.com>
 *
 * This file fork from netty.
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

package io.otavia.core.timer

import io.otavia.core.slf4a.Logger
import io.otavia.core.system.ActorSystem
import io.otavia.core.timer.HashedWheelTimer.*
import io.otavia.core.timer.HashedWheelTimer.HashedWheelBucket.LONG_DEADLINE
import io.otavia.core.util.{Chainable, Nextable, Platform, SpinLockQueue}

import java.util.concurrent.atomic.{AtomicIntegerFieldUpdater, AtomicLong}
import java.util.concurrent.{CountDownLatch, Executor, Executors, ThreadFactory}
import scala.collection.mutable
import scala.concurrent.duration.{Deadline, MILLISECONDS, TimeUnit}
import scala.language.unsafeNulls

/** A [[InternalTimer]] optimized for approximated I/O timeout scheduling.
 *  ===Tick Duration===
 *  As described with 'approximated', this timer does not execute the scheduled [[TimerTask]] on time.
 *  [[HashedWheelTimer]], on every tick, will check if there are any [[TimerTask]]s behind the schedule and execute
 *  them.
 *
 *  You can increase or decrease the accuracy of the execution timing by specifying smaller or larger tick duration in
 *  the constructor. In most network applications, I/O timeout does not need to be accurate. Therefore, the default tick
 *  duration is 100 milliseconds and you will not need to try different configurations in most cases.
 *  ===Ticks per Wheel (Wheel Size)===
 *  [[HashedWheelTimer]] maintains a data structure called 'wheel'. To put simply, a wheel is a hash table of
 *  [[TimerTask]]s whose hash function is 'dead line of the task'. The default number of ticks per wheel (i.e. the size
 *  of the wheel) is 512. You could specify a larger value if you are going to schedule a lot of timeouts.
 *  ===Do not create many instances.===
 *  [[HashedWheelTimer]] creates a new thread whenever it is instantiated and started. Therefore, you should make sure
 *  to create only one instance and share it across your application. One of the common mistakes, that makes your
 *  application unresponsive, is to create a new instance for every connection.
 *  ===Implementation Details===
 *  [[HashedWheelTimer]] is based on <a href="https://cseweb.ucsd.edu/users/varghese/">George Varghese</a> and Tony
 *  Lauck's paper, <a href="https://cseweb.ucsd.edu/users/varghese/PAPERS/twheel.ps.Z">'Hashed and Hierarchical Timing
 *  Wheels: data structures to efficiently implement a timer facility'</a>. More comprehensive slides are located <a
 *  href="https://www.cse.wustl.edu/~cdgill/courses/cs6874/TimingWheels.ppt">here</a>.
 *
 *  @constructor
 *    Creates a new [[HashedWheelTimer]].
 *  @param system
 *    [[ActorSystem]] of the [[HashedWheelTimer]] belong.
 *  @param threadFactory
 *    a [[ThreadFactory]] that creates a background [[Thread]] which is dedicated to [[TimerTask]] execution.
 *  @param tickDuration
 *    the duration between tick
 *  @param unit
 *    the time unit of the [[tickDuration]]
 *  @param ticksPerWheel
 *    the size of the wheel
 *  @param leakDetection
 *    `true` if leak detection should be enabled always, if false it will only be enabled if the worker thread is not a
 *    daemon thread.
 *  @param maxPendingTimeouts
 *    The maximum number of pending timeouts after which call to [[newTimeout]] will result in
 *    [[java.util.concurrent.RejectedExecutionException]] being thrown. No maximum pending timeouts limit is assumed if
 *    this value is 0 or negative.
 *  @param taskExecutor
 *    The [[Executor]] that is used to execute the submitted [[TimerTask]]s. The caller is responsible to shutdown the
 *    [[Executor]] once it is not needed anymore.
 *  @throws NullPointerException
 *    if either of [[threadFactory]] and [[unit]] is null
 *  @throws IllegalArgumentException
 *    if either of [[tickDuration]] and [[ticksPerWheel]] is &lt;= 0
 */
class HashedWheelTimer(
    val system: ActorSystem,
    threadFactory: ThreadFactory,
    val tickDuration: Long,
    unit: TimeUnit,
    val ticksPerWheel: Int,
    leakDetection: Boolean,
    maxPendingTimeouts: Long,
    private val taskExecutor: Executor
) extends InternalTimer {

    private[timer] val logger: Logger = Logger.getLogger(getClass, system)

    /** Creates a new [[HashedWheelTimer]].
     *
     *  @param system
     *    [[ActorSystem]] of the [[HashedWheelTimer]] belong.
     *  @param threadFactory
     *    a [[ThreadFactory]] that creates a background [[Thread]] which is dedicated to [[TimerTask]] execution.
     *  @param tickDuration
     *    the duration between tick
     *  @param unit
     *    the time unit of the [[tickDuration]]
     *  @param ticksPerWheel
     *    the size of the wheel
     *  @param leakDetection
     *    `true` if leak detection should be enabled always, if false it will only be enabled if the worker thread is
     *    not a daemon thread.
     *  @param maxPendingTimeouts
     *    The maximum number of pending timeouts after which call to [[newTimeout]] will result in
     *    [[java.util.concurrent.RejectedExecutionException]] being thrown. No maximum pending timeouts limit is assumed
     *    if this value is 0 or negative.
     *  @throws NullPointerException
     *    if either of [[threadFactory]] and [[unit]] is null
     *  @throws IllegalArgumentException
     *    if either of [[tickDuration]] and [[ticksPerWheel]] is &lt;= 0
     */
    def this(
        system: ActorSystem,
        threadFactory: ThreadFactory,
        tickDuration: Long,
        unit: TimeUnit,
        ticksPerWheel: Int,
        leakDetection: Boolean,
        maxPendingTimeouts: Long
    ) = this(
      system,
      threadFactory,
      tickDuration,
      unit: TimeUnit,
      ticksPerWheel,
      leakDetection,
      maxPendingTimeouts,
      ImmediateExecutor
    )

    /** Creates a new [[HashedWheelTimer]].
     *
     *  @param system
     *    [[ActorSystem]] of the [[HashedWheelTimer]] belong.
     *  @param threadFactory
     *    a [[ThreadFactory]] that creates a background [[Thread]] which is dedicated to [[TimerTask]] execution.
     *  @param tickDuration
     *    the duration between tick
     *  @param unit
     *    the time unit of the [[tickDuration]]
     *  @param ticksPerWheel
     *    the size of the wheel
     *  @param leakDetection
     *    `true` if leak detection should be enabled always, if false it will only be enabled if the worker thread is
     *    not a daemon thread.
     *  @throws NullPointerException
     *    if either of [[threadFactory]] and [[unit]] is null
     *  @throws IllegalArgumentException
     *    if either of [[tickDuration]] and [[ticksPerWheel]] is &lt;= 0
     */
    def this(
        system: ActorSystem,
        threadFactory: ThreadFactory,
        tickDuration: Long,
        unit: TimeUnit,
        ticksPerWheel: Int,
        leakDetection: Boolean
    ) = this(system, threadFactory, tickDuration, unit: TimeUnit, ticksPerWheel, leakDetection, -1)

    /** Creates a new [[HashedWheelTimer]].
     *
     *  @param system
     *    [[ActorSystem]] of the [[HashedWheelTimer]] belong.
     *  @param threadFactory
     *    a [[ThreadFactory]] that creates a background [[Thread]] which is dedicated to [[TimerTask]] execution.
     *  @param tickDuration
     *    the duration between tick
     *  @param unit
     *    the time unit of the [[tickDuration]]
     *  @param ticksPerWheel
     *    the size of the wheel
     *  @throws NullPointerException
     *    if either of [[threadFactory]] and [[unit]] is null
     *  @throws IllegalArgumentException
     *    if either of [[tickDuration]] and [[ticksPerWheel]] is &lt;= 0
     */
    def this(
        system: ActorSystem,
        threadFactory: ThreadFactory,
        tickDuration: Long,
        unit: TimeUnit,
        ticksPerWheel: Int
    ) = this(system, threadFactory, tickDuration, unit: TimeUnit, ticksPerWheel, true)

    /** Creates a new [[HashedWheelTimer]].
     *
     *  @param system
     *    [[ActorSystem]] of the [[HashedWheelTimer]] belong.
     *  @param threadFactory
     *    a [[ThreadFactory]] that creates a background [[Thread]] which is dedicated to [[TimerTask]] execution.
     *  @param tickDuration
     *    the duration between tick
     *  @param unit
     *    the time unit of the [[tickDuration]]
     *  @throws NullPointerException
     *    if either of [[threadFactory]] and [[unit]] is null
     *  @throws IllegalArgumentException
     *    if either of [[tickDuration]] and [[ticksPerWheel]] is &lt;= 0
     */
    def this(system: ActorSystem, threadFactory: ThreadFactory, tickDuration: Long, unit: TimeUnit) =
        this(system, threadFactory, tickDuration, unit: TimeUnit, 512)

    /** Creates a new [[HashedWheelTimer]].
     *
     *  @param system
     *    [[ActorSystem]] of the [[HashedWheelTimer]] belong.
     *  @param threadFactory
     *    a [[ThreadFactory]] that creates a background [[Thread]] which is dedicated to [[TimerTask]] execution.
     *  @throws NullPointerException
     *    if either of [[threadFactory]] and [[unit]] is null
     *  @throws IllegalArgumentException
     *    if either of [[tickDuration]] and [[ticksPerWheel]] is &lt;= 0
     */
    def this(system: ActorSystem, threadFactory: ThreadFactory) = this(system, threadFactory, 100, MILLISECONDS)

    /** Creates a new [[HashedWheelTimer]].
     *
     *  @param system
     *    [[ActorSystem]] of the [[HashedWheelTimer]] belong.
     *  @param tickDuration
     *    the duration between tick
     *  @param unit
     *    the time unit of the [[tickDuration]]
     *  @param ticksPerWheel
     *    the size of the wheel
     *  @throws NullPointerException
     *    if either of [[threadFactory]] and [[unit]] is null
     *  @throws IllegalArgumentException
     *    if either of [[tickDuration]] and [[ticksPerWheel]] is &lt;= 0
     */
    def this(system: ActorSystem, tickDuration: Long, unit: TimeUnit, ticksPerWheel: Int) =
        this(system, Executors.defaultThreadFactory(), tickDuration, unit, ticksPerWheel)

    /** Creates a new [[HashedWheelTimer]].
     *
     *  @param system
     *    [[ActorSystem]] of the [[HashedWheelTimer]] belong.
     *  @param tickDuration
     *    the duration between tick
     *  @param unit
     *    the time unit of the [[tickDuration]]
     *  @throws NullPointerException
     *    if either of [[threadFactory]] and [[unit]] is null
     *  @throws IllegalArgumentException
     *    if either of [[tickDuration]] and [[ticksPerWheel]] is &lt;= 0
     */
    def this(system: ActorSystem, tickDuration: Long, unit: TimeUnit) =
        this(system, Executors.defaultThreadFactory(), tickDuration, unit)

    /** Creates a new [[HashedWheelTimer]] with the default thread factory [[Executors.defaultThreadFactory()]], default
     *  tick duration, and default number of ticks per wheel.
     *
     *  @param system
     *    [[ActorSystem]] of the [[HashedWheelTimer]] belong.
     *  @throws NullPointerException
     *    if either of [[threadFactory]] and [[unit]] is null
     *  @throws IllegalArgumentException
     *    if either of [[tickDuration]] and [[ticksPerWheel]] is &lt;= 0
     */
    def this(system: ActorSystem) = this(system, Executors.defaultThreadFactory())

    private final val worker       = new Worker(this)
    private final val workerThread = threadFactory.newThread(worker)

    @volatile private var workerState = 0 // 0 - init, 1 - started, 2 - shut down

    private val wheel: Array[HashedWheelBucket] = createWheel(ticksPerWheel)

    private[timer] val mask = wheel.length - 1

    private val startTimeInitialized     = new CountDownLatch(1)
    private[timer] val timeouts          = new SpinLockQueue[HashedWheelTimeout]()
    private[timer] val cancelledTimeouts = new SpinLockQueue[HashedWheelTimeout]()

    private val pendingTimeouts = new AtomicLong(0)

    @volatile private[timer] var startTime = System.nanoTime()

    override def newTimeout(task: TimerTask, delay: Long, unit: TimeUnit): Timeout = {
        assert(delay > 0, "delay must large than 0")

        newTimeout0(task, delay, unit, 0, MILLISECONDS)
    }

    override def newTimeout(task: TimerTask, delay: Long, unit: TimeUnit, period: Long, punit: TimeUnit): Timeout = {
        assert(delay > 0, "delay must large than 0")
        assert(period > 0, "period must large than 0")

        newTimeout0(task, delay, unit, period, punit)
    }

    private def newTimeout0(task: TimerTask, delay: Long, unit: TimeUnit, period: Long, punit: TimeUnit): Timeout = {
        start()

        val timeout = new HashedWheelTimeout(this, task, System.nanoTime(), unit.toNanos(delay), punit.toNanos(period))

        // Add the timeout to the timeout queue which will be processed on the next tick.
        // During processing all the queued HashedWheelTimeouts will be added to the correct HashedWheelBucket.
        timeouts.enqueue(timeout)

        timeout
    }

    /** Starts the background thread explicitly. The background thread will start automatically on demand even if you
     *  did not call this method.
     *
     *  @throws IllegalStateException
     *    if this timer has been [[stop]] stopped already
     */
    final def start(): Unit = {
        if (workerState == WORKER_STATE_STARTED) { // help branch prediction

        } else {
            WORKER_STATE_UPDATER.get(this) match
                case WORKER_STATE_INIT =>
                    if (WORKER_STATE_UPDATER.compareAndSet(this, WORKER_STATE_INIT, WORKER_STATE_STARTED)) {
                        workerThread.start()
                    }
                case WORKER_STATE_STARTED =>
                case WORKER_STATE_SHUTDOWN =>
                    throw new IllegalStateException("cannot be started once stopped")
        }
    }

    override def stop: Set[Timeout] = ???

}

object HashedWheelTimer {

    private val WORKER_STATE_INIT     = 0
    private val WORKER_STATE_STARTED  = 1
    private val WORKER_STATE_SHUTDOWN = 2

    private val WORKER_STATE_UPDATER = AtomicIntegerFieldUpdater.newUpdater(classOf[HashedWheelTimer], "workerState")
    private final class Worker(val timer: HashedWheelTimer) extends Runnable {

        private val unprocessedTimeouts: mutable.Set[Timeout] = mutable.HashSet.empty

        private var startTime: Long = 0

        private var tick: Long = 0

        override def run(): Unit = {

            startTime = System.nanoTime()

            while (WORKER_STATE_UPDATER.get(timer) == WORKER_STATE_STARTED) {
                val deadline = waitForNextTick()
                if (deadline > 0) {
                    val idx: Int = (tick & timer.mask).toInt
//                    processCancelledTasks()
                    val bucket = timer.wheel(idx)
                    transferTimeoutsToBuckets()
                    bucket.expireTimeouts(deadline)
                    tick += 1
                }
            }

            // Fill the unprocessedTimeouts so we can return them from stop() method.
            for (bucket <- timer.wheel) {
                bucket.clearTimeouts(unprocessedTimeouts)
            }

            while (timer.timeouts.nonEmpty) {
                val timeout: HashedWheelTimeout = timer.timeouts.dequeue()
                if (!timeout.isCancelled) unprocessedTimeouts.add(timeout)
            }

            processCancelledTasks()
        }

        private def transferTimeoutsToBuckets(): Unit = {
            // transfer only max. 100000 timeouts per tick to prevent a thread to stale the workerThread when it just
            // adds new timeouts in a loop.
            var i = 0
            while (i < 100000 && timer.timeouts.nonEmpty) {
                val timeout: HashedWheelTimeout = timer.timeouts.dequeue()

                if (timeout.state != HashedWheelTimeout.ST_CANCELLED) {
                    val deadline = timeout.createTime - startTime + timeout.delay
                    putBucket(timeout, deadline)
                }
                i += 1
            }
        }

        def putBucket(timeout: HashedWheelTimeout, deadline: Long): Unit = {
            val calculated = deadline / timer.tickDuration
            timeout.remainingRounds = (calculated - tick) / timer.wheel.length

            val ticks        = math.max(calculated, tick)
            val stopIdx: Int = (ticks & timer.mask).toInt

            val bucket = timer.wheel(stopIdx)
            bucket.addTimeout(timeout)
        }

        private def processCancelledTasks(): Unit = {
            while (timer.cancelledTimeouts.nonEmpty) {
                val timeout: HashedWheelTimeout = timer.cancelledTimeouts.dequeue()
                try timeout.remove()
                catch {
                    case t: Throwable =>
                        if (timer.logger.isWarnEnabled)
                            timer.logger.warn("An exception was thrown while process a cancellation task", t)
                }
            }
        }

        /** calculate goal nanoTime from startTime and current tick number, then wait until that goal has been reached.
         *
         *  @return
         *    [[Long.MinValue]] if received a shutdown request, current time otherwise (with [[Long.MinValue]] changed
         *    by +1)
         */
        private def waitForNextTick() = {
            val deadline          = timer.tickDuration * (tick + 1)
            var ret: Long         = 0
            var continue: Boolean = true
            while (continue) {
                val currentTime = System.nanoTime() - timer.startTime
                var sleepTimeMs = (deadline - currentTime + 999999) / 1000000
                if (sleepTimeMs <= 0) {
                    ret = if (currentTime == Long.MaxValue) -Long.MaxValue else currentTime
                    continue = false
                } else {
                    // Check if we run on windows, as if thats the case we will need
                    // to round the sleepTime as workaround for a bug that only affect
                    // the JVM if it runs on windows.
                    //
                    // See https://github.com/netty/netty/issues/356
                    if (Platform.isWindows) {
                        sleepTimeMs = sleepTimeMs / 10 * 10
                        if (sleepTimeMs == 0) sleepTimeMs = 1
                    }

                    try Thread.sleep(sleepTimeMs)
                    catch {
                        case ignored: InterruptedException =>
                            if (WORKER_STATE_UPDATER.get(timer) == WORKER_STATE_SHUTDOWN) {
                                ret = Long.MinValue
                                continue = false
                            }
                    }
                }
            }
            ret
        }

    }

    final class HashedWheelTimeout(
        val timer: HashedWheelTimer,
        val task: TimerTask,
        val createTime: Long,
        val delay: Long,
        val period: Long
    ) extends Timeout
        with Runnable
        with Nextable {

        import HashedWheelTimeout.*

        @volatile private var status = ST_INIT

        // remainingRounds will be calculated and set by Worker.transferTimeoutsToBuckets() before the
        // HashedWheelTimeout will be added to the correct HashedWheelBucket.
        var remainingRounds = 0L

        // This will be used to chain timeouts in HashedWheelTimerBucket via a double-linked-list.
        // As only the workerThread will act on it there is no need for synchronization / volatile.
        var nextNode: HashedWheelTimer.HashedWheelTimeout = _
        var prevNode: HashedWheelTimer.HashedWheelTimeout = _

        // The bucket to which the timeout was added
        var bucket: HashedWheelTimer.HashedWheelBucket = _

        override def periodic: Boolean = period > 0

        override def cancel: Boolean = if (compareAndSetState(ST_INIT, ST_CANCELLED)) true else false

        def remove(): Unit = {
            ???
        }

        inline def compareAndSetState(expected: Int, state: Int): Boolean =
            STATE_UPDATER.compareAndSet(this, expected, state)

        def state: Int = status

        override def isCancelled: Boolean = status == ST_CANCELLED

        override def isExpired: Boolean = status == ST_EXPIRED

        def expire(): Unit = if (compareAndSetState(ST_INIT, ST_EXPIRED)) try {
            timer.taskExecutor.execute(this)
        } catch {
            case t: Throwable =>
                if (timer.logger.isWarnEnabled)
                    timer.logger.warn(
                      s"An exception was thrown while submit ${task.getClass.getSimpleName} for execution.",
                      t
                    )
        }

        override def run(): Unit = try {
            task.run(this)
        } catch {
            case t: Throwable =>
                if (timer.logger.isWarnEnabled)
                    timer.logger.warn(s"An exception was thrown by ${task.getClass.getSimpleName}.", t)
        }

    }

    private object HashedWheelTimeout {

        val ST_INIT      = 0
        val ST_CANCELLED = 1
        val ST_EXPIRED   = 2

        val STATE_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(classOf[HashedWheelTimer.HashedWheelTimeout], "status")

    }

    /** Bucket that stores HashedWheelTimeouts. These are stored in a linked-list like datastructure to allow easy
     *  removal of HashedWheelTimeouts in the middle. Also the HashedWheelTimeout act as nodes themself and so no extra
     *  object creation is needed.
     */
    final class HashedWheelBucket {

        // Used for the linked-list datastructure
        private var head: HashedWheelTimer.HashedWheelTimeout = _
        private var tail: HashedWheelTimer.HashedWheelTimeout = _

        private var longRemainingRounds: Long                     = LONG_DEADLINE
        private var longHead: HashedWheelTimer.HashedWheelTimeout = _
        private var longTail: HashedWheelTimer.HashedWheelTimeout = _

        /** Add [[HashedWheelTimeout]] tp this bucket. */
        def addTimeout(timeout: HashedWheelTimeout): Unit = {
            timeout.bucket = this
            if (timeout.remainingRounds < LONG_DEADLINE) {
                if (head == null) {
                    head = timeout
                    tail = timeout
                } else {
                    tail.next = timeout
                    timeout.prevNode = tail
                    tail = timeout
                }
            } else {
                timeout.remainingRounds += LONG_DEADLINE - longRemainingRounds
                if (longHead == null) {
                    longHead = timeout
                    longTail = timeout
                } else {
                    longTail.next = timeout
                    timeout.prevNode = longTail
                    longTail = timeout
                }
            }
        }

        /** Expire all [[HashedWheelTimeout]]s for the given deadline. */
        def expireTimeouts(deadline: Long): Unit = ???

        def remove(timeout: HashedWheelTimeout): HashedWheelTimeout = ???

        def removeLong(timeout: HashedWheelTimeout): HashedWheelTimeout = ???

        /** Clear this bucket and return all not expired / cancelled [[Timeout]]s. */
        def clearTimeouts(set: mutable.Set[Timeout]): Unit = ???

        private def pollTimeout(): HashedWheelTimeout = ???

    }

    object HashedWheelBucket {
        val LONG_DEADLINE = 4
    }

    private def createWheel(ticksPerWheel: Int): Array[HashedWheelBucket] = {
        if (ticksPerWheel < 1 || ticksPerWheel > 1073741824)
            throw new IllegalArgumentException(s"ticksPerWheel: $ticksPerWheel (expected: [1, 1073741824])")

        val wheels = normalizeTicksPerWheel(ticksPerWheel)
        val wheel  = new Array[HashedWheelBucket](wheels)
        wheel.indices.foreach { index =>
            wheel(index) = new HashedWheelBucket()
        }
        wheel
    }

    private def normalizeTicksPerWheel(ticksPerWheel: Int) = {
        var normalizedTicksPerWheel = 1
        while (normalizedTicksPerWheel < ticksPerWheel) normalizedTicksPerWheel <<= 1
        normalizedTicksPerWheel
    }

}
