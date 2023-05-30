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

import io.otavia.core.actor.{AbstractActor, ChannelsActor}
import io.otavia.core.channel.Channel
import io.otavia.core.timer.Timer

import scala.collection.mutable
import scala.language.unsafeNulls

trait ChannelFuture extends Future[Channel] {

    override private[core] def promise: ChannelPromise = this.asInstanceOf[ChannelPromise]

}

object ChannelFuture {
    def apply(): ChannelFuture = ChannelPromise()
}

private[core] class ChannelPromise extends Promise[Channel] with ChannelFuture {

    private var stack: Stack                             = _
    private var channel: Channel                         = _
    private var throwable: Throwable                     = _
    private var completedFunc: () => Unit                = _
    private var listeners: mutable.Queue[ChannelPromise] = _
    private var callback: ChannelPromise => Unit         = _ => {}
    private var failure: ChannelPromise => Unit          = _ => {}
    private var tid: Long                                = Timer.INVALID_TIMEOUT_REGISTER_ID

    def addListener(listener: ChannelPromise): Unit = {
        if (listeners == null) listeners = mutable.Queue.empty
        listeners.enqueue(listener)
    }

    def addListener(listener: BlockPromise[?]): Unit = {}

    def setTimeoutId(id: Long): Unit = tid = id

    def timeoutId: Long = tid

    private def onCompleted(): Unit = {
        val channelsActor = stack.runtimeActor.asInstanceOf[ChannelsActor[?]]
        channelsActor.receiveFuture(this)
    }

    override def setSuccess(result: Channel): Promise[Channel] = ???

    override def setFailure(cause: Throwable): Promise[Channel] =
        ??? // TODO: bug if completed immediately, current stack is not execute completed, then execute to next state

    override def future: Future[Channel] = ???

    override def canTimeout: Boolean = tid != Timer.INVALID_TIMEOUT_REGISTER_ID

    override def setStack(s: Stack): Unit = ???

    override def actorStack: Stack = ???

    override def recycle(): Unit = ???

    override protected def cleanInstance(): Unit = ???

    override def isSuccess: Boolean = ???

    override def isFailed: Boolean = ???

    override def isDone: Boolean = ???

    override def getNow: Channel = ???

    override def cause: Option[Throwable] = ???

    override def causeUnsafe: Throwable = ???

    def execute(task: ChannelPromise => Unit): Unit = task(this)

    def onSuccess(task: ChannelPromise => Unit): Unit = callback = task

    def onFailure(task: ChannelPromise => Unit): Unit = failure = task

}

object ChannelPromise {

    private val pool = new PromiseObjectPool[ChannelPromise] {
        override protected def newObject(): ChannelPromise = new ChannelPromise()
    }

    def apply(): ChannelPromise = pool.get()

}
