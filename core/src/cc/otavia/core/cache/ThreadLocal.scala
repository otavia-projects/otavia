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

package cc.otavia.core.cache

import cc.otavia.core.system.ActorThread
import cc.otavia.core.timer.{TimeoutTrigger, Timer}

import scala.language.unsafeNulls

/** A special variant of [[ThreadLocal]] that yields higher access performance when accessed from a [[ActorThread]].
 *
 *  Internally, a [[ThreadLocal]] uses a constant index in an array, instead of using hash code and hash table, to look
 *  for a variable. Although seemingly very subtle, it yields slight performance advantage over using a hash table, and
 *  it is useful when accessed frequently.
 *
 *  To take advantage of this thread-local variable, your thread must be a [[ActorThread]]. By default, all actors and
 *  channel are running by [[ActorThread]].
 *  @see
 *    [[java.lang.ThreadLocal]]
 *  @tparam V
 *    the type of the thread-local variable
 */
abstract class ThreadLocal[V] extends TimeoutResource {

    private var initial: Boolean                           = false
    private var threadLocalTimers: Array[ThreadLocalTimer] = _

    @volatile private var triggered: Boolean = false

    private final def initialIfNot(thread: ActorThread): Unit =
        if (initial) {} else syncInit(thread) // Reducing cpu branch prediction errors.

    private[cache] final def threadIndex(): Int = {
        val thread = ActorThread.currentThread()
        initialIfNot(thread)
        thread.index
    }

    private def syncInit(thread: ActorThread): Unit = this.synchronized {
        val len = thread.parent.size
        if (!initial) {
            doInitial(len)
            initialTimeoutTrigger match
                case Some(value) =>
                    if (
                      value.isInstanceOf[TimeoutTrigger.DelayPeriod] ||
                      value.isInstanceOf[TimeoutTrigger.FirstTimePeriod]
                    ) thread.system.registerLongLifeThreadLocal(this)
                case None =>
            threadLocalTimers = new Array[ThreadLocalTimer](len)
            initial = true
        }
    }

    private[cache] def doInitial(len: Int): Unit

    /** Returns true if the thread local variable has been initial, otherwise false. */
    final def isInitial: Boolean = initial

    /** Returns the initial value for this thread-local variable. */
    protected def initialValue(): V

    /** Returns the current value for the current thread */
    def get(): V

    final private[cache] def updateGetTime(index: Int): Unit = if (isSupportTimeout) {
        val threadLocalTimer = threadLocalTimers(index)
        threadLocalTimer.updateGetTime()
    }

    /** Returns the current value for the current thread if it exists, null otherwise. */
    def getIfExists: V | Null

    /** Set the value for the current thread. */
    def set(v: V): Unit

    private[cache] def updateSetTime(): Unit = if (isSupportTimeout) {
        val threadLocalTimer = threadLocalTimers(ActorThread.currentThread().index)
        threadLocalTimer.updateSetTime()
    }

    /** Returns true if and only if this thread-local variable is set. */
    def isSet: Boolean

    /** Sets the value to uninitialized for the specified thread local map. After this, any subsequent call to get()
     *  will trigger a new call to initialValue().
     */
    def remove(): Unit

    /** Cancel the local variable timer task. */
    def cancelTimer(): Unit = if (isSupportTimeout) {
        val thread           = ActorThread.currentThread()
        val index            = thread.index
        val threadLocalTimer = threadLocalTimers(index)
        thread.system.timer.cancelTimerTask(threadLocalTimer.registerId)
        threadLocalTimers(index) = null
    }

    /** Invoked when this thread local variable is removed by remove(). Be aware that remove() is not guaranteed to be
     *  called when the `Thread` completes which means you can not depend on this for cleanup of the resources in the
     *  case of `Thread` completion.
     */
    protected def onRemoval(value: V): Unit = {}

    final protected def initialTimer(): Unit = {
        initialTimeoutTrigger match
            case Some(trigger) =>
                triggered = true
                val thread           = ActorThread.currentThread()
                val system           = thread.system
                val threadLocalTimer = new ThreadLocalTimer(this)
                val id = system.timer.registerResourceTimeout(
                  trigger,
                  thread.actorThreadAddress,
                  threadLocalTimer
                )
                threadLocalTimer.updateRegisterId(id)
                threadLocalTimers(thread.index) = threadLocalTimer
            case None =>
                threadLocalTimers = null
    }

    /** Whether this [[ThreadLocal]] support timeout. */
    private final def isSupportTimeout: Boolean = triggered

}

object ThreadLocal {

    val EMPTY: Array[AnyRef] = Array.empty

}
