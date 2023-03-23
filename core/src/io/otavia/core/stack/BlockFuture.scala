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

package io.otavia.core.stack

import io.otavia.core.actor.{AbstractActor, Actor}
import io.otavia.core.channel.Channel
import io.otavia.core.reactor.BlockFutureCompletedEvent

import java.util.concurrent.Callable

trait BlockFuture[V] extends Future[V] {

    override private[core] def promise: BlockPromise[V] = this.asInstanceOf[BlockPromise[V]]

}

object BlockFuture {
    def apply[V](func: () => V): BlockFuture[V] = new BlockPromise(func)
}

class BlockPromise[V](func: () => V) extends Promise[V] with BlockFuture[V] with Runnable {

    private var parent: Actor[?] | Channel = _

    private var callback: BlockPromise[V] => Unit = _

    def owner: Actor[?] | Channel = ???

    override def setSuccess(result: V): Promise[V] = ???

    override def setFailure(cause: Throwable): Promise[V] = ???

    override def future: Future[V] = ???

    override def canTimeout: Boolean = ???

    override def setStack(s: Stack): Unit = ???

    override def actorStack: Stack = ???

    override def recycle(): Unit = ???

    override protected def cleanInstance(): Unit = ???

    override def isSuccess: Boolean = ???

    override def isFailed: Boolean = ???

    override def isDone: Boolean = ???

    override def getNow: V = ???

    override def cause: Option[Throwable] = ???

    override def causeUnsafe: Throwable = ???

    override def run(): Unit = {

        try {
            val v = func()
            setSuccess(v)
        } catch {
            case cause: Throwable => setFailure(cause)
        }

        val eventableAddress = owner match
            case actor: AbstractActor[?] => actor.self
            case channel: Channel        => channel.executorAddress

        eventableAddress.inform(BlockFutureCompletedEvent(this))
    }

    def onCompleted(task: BlockPromise[V] => Unit): Unit = callback = task

}
