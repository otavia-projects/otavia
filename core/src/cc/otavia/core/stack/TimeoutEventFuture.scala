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

import cc.otavia.core.message.AskTimeoutEvent

import scala.language.unsafeNulls

trait TimeoutEventFuture extends Future[AskTimeoutEvent] {

    override private[core] def promise: TimeoutEventPromise = this.asInstanceOf[TimeoutEventPromise]

}

object TimeoutEventFuture {

    private[stack] val pool = new PromiseObjectPool[TimeoutEventPromise] {
        override protected def newObject(): TimeoutEventPromise = new TimeoutEventPromise()
    }

    def apply(): TimeoutEventFuture = pool.get()

}

private[core] class TimeoutEventPromise extends AbstractPromise[AskTimeoutEvent] with TimeoutEventFuture {

    private var event: AskTimeoutEvent = _
    override def setSuccess(result: AskTimeoutEvent): Promise[AskTimeoutEvent] = {
        event = result
        this
    }

    override def setFailure(cause: Throwable): Promise[AskTimeoutEvent] = this

    override def future: Future[AskTimeoutEvent] = this

    override def recycle(): Unit = TimeoutEventFuture.pool.recycle(this)

    override protected def cleanInstance(): Unit = {
        event = null
        super.cleanInstance()
    }

    override def isSuccess: Boolean = event ne null

    override def isFailed: Boolean = false

    override def isDone: Boolean = event ne null

    override def getNow: AskTimeoutEvent = event

    override def cause: Option[Throwable] = None

    override def causeUnsafe: Throwable = throw new UnsupportedOperationException()

}
