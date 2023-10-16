package cc.otavia.core.stack

import cc.otavia.core.cache.Poolable.SingleThreadPoolableHolder
import cc.otavia.core.cache.{ActorThreadLocal, Poolable, ResourceTimer, ThreadIsolationObjectPool, ThreadLocalTimer}
import cc.otavia.core.system.ActorThread
import cc.otavia.core.timer.TimeoutTrigger

import java.util.concurrent.TimeUnit

abstract class PromiseObjectPool[P <: AbstractPromise[?]] extends ThreadIsolationObjectPool[P] {

    private val threadLocal = new ActorThreadLocal[SingleThreadPoolableHolder[P]] {

        override protected def initialValue(): Poolable.SingleThreadPoolableHolder[P] =
            new Poolable.SingleThreadPoolableHolder[P]()

        override protected def initialTimeoutTrigger: Option[TimeoutTrigger] =
            Some(TimeoutTrigger.DelayPeriod(60, 60, TimeUnit.SECONDS, TimeUnit.SECONDS))

        override def handleTimeout(registerId: Long, resourceTimer: ResourceTimer): Unit = {
            val threadLocalTimer: ThreadLocalTimer = resourceTimer.asInstanceOf[ThreadLocalTimer]
            if ((System.currentTimeMillis() - threadLocalTimer.recentlyGetTime) / 1000 > 30) {
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
