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

package cc.otavia.core.stack

import cc.otavia.core.channel.{Channel, ChannelAddress}
import cc.otavia.core.actor.{AbstractActor, ChannelsActor}
import cc.otavia.core.channel.{Channel, ChannelAddress}
import cc.otavia.core.message.ReactorEvent
import cc.otavia.core.timer.Timer

import scala.collection.mutable
import scala.language.unsafeNulls

trait ChannelFuture extends Future[ReactorEvent] {

    override private[core] def promise: ChannelPromise = this.asInstanceOf[ChannelPromise]

    def channel: ChannelAddress

}

object ChannelFuture {

    private[stack] val pool = new PromiseObjectPool[ChannelPromise] {
        override protected def newObject(): ChannelPromise = new ChannelPromise()
    }

    def apply(): ChannelFuture = pool.get()

}

private[core] class ChannelPromise extends TimeoutablePromise[ReactorEvent] with ChannelFuture {

    private var ch: Channel                              = _
    private var event: ReactorEvent                      = _
    private var throwable: Throwable                     = _
    private var listeners: mutable.Queue[ChannelPromise] = _
    private var callback: ChannelPromise => Unit         = _

    private[core] def setChannel(channel: Channel): Unit = ch = channel

    def channel: ChannelAddress = ch

    def addListener(listener: ChannelPromise): Unit = {
        if (listeners == null) listeners = mutable.Queue.empty
        listeners.enqueue(listener)
    }

    private def completed(): Unit = {
        if (callback ne null) execute(callback)
        if (stack ne null) {
            val actor = stack.runtimeActor
            actor.receiveFuture(this)
        }
    }

    override def setSuccess(result: ReactorEvent): ChannelPromise = {
        event = result
        this.completed()
        this
    }

    override def setFailure(cause: Throwable): ChannelPromise = {
        throwable = cause
        this.completed()
        this
    }

    override def future: ChannelFuture = this

    override def recycle(): Unit = ChannelFuture.pool.recycle(this)

    override protected def cleanInstance(): Unit = {
        ch = null
        event = null
        throwable = null
        callback = null
        super.cleanInstance()
    }

    override def isSuccess: Boolean = event ne null

    override def isFailed: Boolean = throwable ne null

    override def isDone: Boolean = isSuccess || isFailed

    override def getNow: ReactorEvent = if (event == null && throwable == null)
        throw new IllegalStateException("not completed yet")
    else if (throwable != null) throw throwable
    else event

    override def cause: Option[Throwable] = Option(throwable)

    override def causeUnsafe: Throwable = if (event == null && throwable == null)
        throw new IllegalStateException("not completed yet")
    else if (event != null) throw new IllegalStateException("the future is success")
    else throwable

    def execute(task: ChannelPromise => Unit): Unit = task(this)

    def onSuccess(task: ChannelPromise => Unit): Unit = ???

    def onFailure(task: ChannelPromise => Unit): Unit = ???

    def onCompleted(task: ChannelPromise => Unit): Unit = if (isDone) execute(task) else callback = task

}
