package cc.otavia.core.stack.helper

import cc.otavia.core.cache.Poolable
import cc.otavia.core.stack.{StackState, StackStatePool, TimeoutEventFuture}

import scala.language.unsafeNulls

class TimeoutState extends StackState with Poolable {

    private var stateId: Int           = 0
    private var fu: TimeoutEventFuture = _

    def future: TimeoutEventFuture = fu

    override def id: Int = stateId

    override protected def cleanInstance(): Unit = {
        stateId = 0
        fu = null
    }

    override def recycle(): Unit = TimeoutState.pool.recycle(this)

}

object TimeoutState {

    private val pool = new StackStatePool[TimeoutState] {
        override protected def newObject(): TimeoutState = new TimeoutState()
    }

    def apply(): TimeoutState = {
        val instance = pool.get()
        val future   = TimeoutEventFuture()
        instance.fu = future
        instance
    }

    def apply(id: Int): TimeoutState = {
        val instance = pool.get()
        val future   = TimeoutEventFuture()
        instance.stateId = id
        instance.fu = future
        instance
    }

}
