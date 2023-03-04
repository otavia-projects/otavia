package io.otavia.core.stack

import io.otavia.core.cache.{ActorThreadLocal, Poolable, ThreadIsolationObjectPool, ThreadLocalTimer}
import io.otavia.core.system.ActorThread
import io.otavia.core.timer.TimeoutTrigger

import java.util.concurrent.TimeUnit

abstract class PromiseObjectPool[P <: Promise[?]] extends ThreadIsolationObjectPool[P] {

    private val threadLocal = new ActorThreadLocal[Poolable.SingleThreadPoolableHolder[P]] {

        override protected def initialValue(): Poolable.SingleThreadPoolableHolder[P] =
            new Poolable.SingleThreadPoolableHolder[P]()

        override protected def initialTimeoutTrigger: Option[TimeoutTrigger] =
            Some(TimeoutTrigger.DelayPeriod(60, 60, TimeUnit.SECONDS, TimeUnit.SECONDS))

        override protected def handleTimeout(registerId: Long, threadLocalTimer: ThreadLocalTimer): Unit = {
            if ((System.nanoTime() - threadLocalTimer.recentlyGetTime) / (1000 * 1000 * 1000) > 30) {
                val holder = this.get()
                if (holder.size > 100) holder.clean(100)
            }
        }

    }

    override protected def holder(): Poolable.SingleThreadPoolableHolder[P] =
        if (ActorThread.currentThreadIsActorThread) threadLocal.get()
        else
            throw new IllegalStateException(
              "PerActorThreadObjectPool can not be used in thread which is not ActorThread, " +
                  "maby you can use PerThreadObjectPool"
            )

    override def dropIfRecycleNotByCreated: Boolean = false

}
