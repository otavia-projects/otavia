package cc.otavia.core.cache

import cc.otavia.core.system.ActorThread

abstract class PerActorThreadObjectPool[T <: Poolable](override val dropIfRecycleNotByCreated: Boolean = false)
    extends ThreadIsolationObjectPool[T] {

    private val threadLocal: ActorThreadLocal[Poolable.SingleThreadPoolableHolder[T]] =
        () => new Poolable.SingleThreadPoolableHolder[T]()

    override protected def holder(): Poolable.SingleThreadPoolableHolder[T] = {
        if (ActorThread.currentThreadIsActorThread) threadLocal.get()
        else
            throw new IllegalStateException(
              "PerActorThreadObjectPool can not be used in thread which is not ActorThread, " +
                  "maby you can use PerThreadObjectPool"
            )
    }

}
