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

package cc.otavia.core.channel.inflight

import cc.otavia.core.cache.ThreadLocalTimer
import cc.otavia.core.cache.{Poolable, ThreadLocalTimer, TimeoutThreadIsolationObjectPool}
import cc.otavia.core.stack.{ChannelReplyFuture, ChannelReplyPromise}
import cc.otavia.core.system.ActorThread
import cc.otavia.core.timer.TimeoutTrigger

import java.util.concurrent.TimeUnit
import scala.language.unsafeNulls

class FutureEntry extends Poolable { // future is chained in stack

    private var promise: ChannelReplyPromise = _
    private var barrier: Boolean             = false

    def setPromise(future: ChannelReplyFuture): Unit = {
        this.promise = future.promise
    }
    def channelReplyPromise: ChannelReplyPromise = this.promise

    def setBarrier(value: Boolean): Unit = this.barrier = value
    def isBarrier: Boolean               = barrier

    override def recycle(): Unit = FutureEntry.pool.recycle(this)

    override protected def cleanInstance(): Unit = {
        promise = null
        barrier = false
    }

}

object FutureEntry {

    private val pool = new TimeoutThreadIsolationObjectPool[FutureEntry] {

        override protected def initialTimeoutTrigger: Option[TimeoutTrigger] =
            Some(TimeoutTrigger.DelayPeriod(180, 180, TimeUnit.SECONDS, TimeUnit.SECONDS))

        override protected def handleTimeout(registerId: Long, threadLocalTimer: ThreadLocalTimer): Unit = {
            val duration = System.nanoTime() - threadLocalTimer.recentlyGetTime
            if (duration / (1000 * 1000 * 1000) > 180) {
                val h = holder()
                if (h.size > 2) h.clean(2)
            }
        }

        override def dropIfRecycleNotByCreated: Boolean = false

        override protected def newObject(): FutureEntry = new FutureEntry()

    }

    def apply(): FutureEntry = pool.get()

    def apply(promise: ChannelReplyPromise, barrier: Boolean = false): FutureEntry = {
        val entry = FutureEntry()
        entry.setPromise(promise)
        entry.setBarrier(barrier)
        entry
    }

}
