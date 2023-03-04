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

import io.otavia.core.message.ExceptionMessage

/** User interface for class [[ChannelReplyPromise]] */
sealed trait ChannelReplyFuture extends Future[AnyRef]

object ChannelReplyFuture {
    def apply(): ChannelReplyFuture = ChannelReplyPromise()
}

class ChannelReplyPromise private () extends Promise[AnyRef] with ChannelReplyFuture {

    override def recycle(): Unit = ChannelReplyPromise.objectPool.recycle(this)

    override def setSuccess(result: AnyRef): Promise[AnyRef] = ???

    override def setFailure(cause: Throwable): Promise[AnyRef] = ???

    override def future: Future[AnyRef] = ???

    override def canTimeout: Boolean = ???

    override def isSuccess: Boolean = ???

    override def isFailed: Boolean = ???

    override def isDone: Boolean = ???

    override def getNow: AnyRef = ???

    override def cause: Option[Throwable] = ???

    override def causeUnsafe: Throwable = ???

    override protected def cleanInstance(): Unit = ???

}

object ChannelReplyPromise {

    private val objectPool = new PromiseObjectPool[ChannelReplyPromise] {
        override protected def newObject(): ChannelReplyPromise = new ChannelReplyPromise()
    }

    def apply(): ChannelReplyPromise = objectPool.get()

}
