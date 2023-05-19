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

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater
import java.util.concurrent.{Executor, Executors, ThreadFactory}
import scala.concurrent.duration.{MILLISECONDS, TimeUnit}
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
    tickDuration: Long,
    unit: TimeUnit,
    ticksPerWheel: Int,
    leakDetection: Boolean,
    maxPendingTimeouts: Long,
    private val taskExecutor: Executor
) extends InternalTimer {

    private val logger: Logger = Logger.getLogger(getClass, system)

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

    override def newTimeout(task: TimerTask, delay: Long, unit: TimeUnit): Timeout = ???

    override def stop: Set[Timeout] = ???

}

object HashedWheelTimer {

    private final class HashedWheelTimeout(val timer: HashedWheelTimer, val task: TimerTask, private val deadline: Long)
        extends Timeout
        with Runnable {

        import HashedWheelTimeout.*

        private val logger: Logger = Logger.getLogger(getClass, timer.system)

        @volatile private var status = ST_INIT

        // remainingRounds will be calculated and set by Worker.transferTimeoutsToBuckets() before the
        // HashedWheelTimeout will be added to the correct HashedWheelBucket.
        var remainingRounds = 0L

        // This will be used to chain timeouts in HashedWheelTimerBucket via a double-linked-list.
        // As only the workerThread will act on it there is no need for synchronization / volatile.
        val next: HashedWheelTimer.HashedWheelTimeout = null
        val prev: HashedWheelTimer.HashedWheelTimeout = null

        // The bucket to which the timeout was added
        val bucket: HashedWheelTimer.HashedWheelBucket = null

        override def cancel: Boolean = ???

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
                if (logger.isWarnEnabled)
                    logger.warn(
                      s"An exception was thrown while submit ${task.getClass.getSimpleName} for execution.",
                      t
                    )
        }

        override def run(): Unit = try {
            task.run(this)
        } catch {
            case t: Throwable =>
                if (logger.isWarnEnabled)
                    logger.warn(s"An exception was thrown by ${task.getClass.getSimpleName}.", t)
        }

    }

    private object HashedWheelTimeout {

        private val ST_INIT      = 0
        private val ST_CANCELLED = 1
        private val ST_EXPIRED   = 2

        private val STATE_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(classOf[HashedWheelTimer.HashedWheelTimeout], "status")

    }

    private final class HashedWheelBucket {

        // Used for the linked-list datastructure
        private var head: HashedWheelTimer.HashedWheelTimeout = _
        private var tail: HashedWheelTimer.HashedWheelTimeout = _

    }

}
