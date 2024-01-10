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

import cc.otavia.core.actor.{AbstractActor, ChannelsActor}
import cc.otavia.core.channel.inflight.QueueMapEntity
import cc.otavia.core.channel.{Channel, ChannelAddress}
import cc.otavia.core.message.ReactorEvent
import cc.otavia.core.timer.Timer

import scala.collection.mutable
import scala.language.unsafeNulls

trait ChannelFuture extends Future[AnyRef] {

    override private[core] def promise: ChannelPromise = this.asInstanceOf[ChannelPromise]

    def channel: ChannelAddress

}

object ChannelFuture {

    private[stack] val pool = new PromisePool[ChannelPromise] {
        override protected def newObject(): ChannelPromise = new ChannelPromise()
    }

    def apply(): ChannelFuture = pool.get()

}

final private[core] class ChannelPromise extends AbstractPromise[AnyRef] with ChannelFuture with QueueMapEntity {

    private var ch: Channel                      = _
    private var ask: AnyRef                      = _
    private var callback: ChannelPromise => Unit = _

    private var msgId: Long      = -1
    private var barrier: Boolean = false

    def setMessageId(id: Long): Unit = this.msgId = id

    def messageId: Long = msgId

    override def entityId: Long = msgId

    def setAsk(ask: AnyRef): Unit = this.ask = ask

    def getAsk(): AnyRef = {
        val v = ask
        v
    }

    def setBarrier(barrier: Boolean): Unit = this.barrier = barrier

    def isBarrier: Boolean = barrier

    private[core] def setChannel(channel: Channel): Unit = ch = channel

    def channel: ChannelAddress = ch

    private def completed(): Unit = {
        if (callback ne null) execute(callback)
        if (stack ne null) {
            val actor = stack.runtimeActor
            actor.receiveFuture(this)
        } else recycle()
    }

    override def setSuccess(result: AnyRef): Unit = {
        this.result = result
        this.completed()
    }

    override def setFailure(cause: Throwable): Unit = {
        error = cause
        this.completed()
    }

    override def future: ChannelFuture = this

    override def recycle(): Unit = ChannelFuture.pool.recycle(this)

    override protected def cleanInstance(): Unit = {
        ch = null
        ask = null
        callback = null
        msgId = -1
        barrier = false
        super.cleanInstance()
    }

    private def execute(task: ChannelPromise => Unit): Unit = task(this)

    def onCompleted(task: ChannelPromise => Unit): Unit = if (isDone) execute(task) else callback = task

}
