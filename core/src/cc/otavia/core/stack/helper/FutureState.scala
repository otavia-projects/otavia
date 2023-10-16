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

package cc.otavia.core.stack.helper

import cc.otavia.core.cache.*
import cc.otavia.core.cache.Poolable.SingleThreadPoolableHolder
import cc.otavia.core.message.Reply
import cc.otavia.core.stack.helper.FutureState.pool
import cc.otavia.core.stack.{ReplyFuture, StackState}
import cc.otavia.core.system.ActorThread
import cc.otavia.core.timer.TimeoutTrigger

import java.util.concurrent.TimeUnit
import scala.compiletime.erasedValue
import scala.deriving.Mirror
import scala.language.unsafeNulls
import scala.reflect.{ClassTag, TypeTest, Typeable, classTag}

class FutureState[R <: Reply] extends StackState with Poolable {

    private var stateId: Int       = 0
    private var fu: ReplyFuture[R] = _
    private var clz: Class[?]      = _

    def future: ReplyFuture[R] = fu

    def replyClass: Class[?] = clz

    override def id: Int = stateId

    override def recycle(): Unit = FutureState.pool.recycle(this)

    override protected def cleanInstance(): Unit = {
        fu = null
        stateId = 0
        clz = null
    }

}

object FutureState {

    def apply[R <: Reply: ClassTag](stateId: Int): FutureState[R] = {
        val state = pool.get().asInstanceOf[FutureState[R]]
        state.stateId = stateId
        state.fu = ReplyFuture()
        state.clz = classTag[R].runtimeClass
        state
    }

    def apply[R <: Reply: ClassTag](): FutureState[R] = {
        val state = pool.get().asInstanceOf[FutureState[R]]
        state.clz = classTag[R].runtimeClass
        state.fu = ReplyFuture()
        state
    }

    private val pool = new ThreadIsolationObjectPool[FutureState[?]] {

        private val threadLocal = new ActorThreadLocal[SingleThreadPoolableHolder[FutureState[?]]] {

            override protected def initialValue(): SingleThreadPoolableHolder[FutureState[?]] =
                new SingleThreadPoolableHolder[FutureState[?]]()

            override protected def initialTimeoutTrigger: Option[TimeoutTrigger] =
                Some(TimeoutTrigger.DelayPeriod(60, 60, TimeUnit.SECONDS, TimeUnit.SECONDS))

            override def handleTimeout(registerId: Long, resourceTimer: ResourceTimer): Unit = {
                val threadLocalTimer: ThreadLocalTimer = resourceTimer.asInstanceOf[ThreadLocalTimer]
                if ((System.currentTimeMillis() - threadLocalTimer.recentlyGetTime) / 1000 > 30) {
                    val holder = this.get()
                    if (holder.size > 10) holder.clean(10)
                }
            }

        }

        override protected def holder(): Poolable.SingleThreadPoolableHolder[FutureState[?]] =
            if (ActorThread.currentThreadIsActorThread) threadLocal.get()
            else
                throw new IllegalStateException(
                  "PerActorThreadObjectPool can not be used in thread which is not ActorThread, " +
                      "maybe you can use PerThreadObjectPool"
                )

        override protected def newObject(): FutureState[?] = new FutureState()

        override def dropIfRecycleNotByCreated: Boolean = true

    }

}
