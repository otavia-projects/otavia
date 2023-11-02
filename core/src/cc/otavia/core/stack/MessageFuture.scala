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

import cc.otavia.core.message.{ExceptionMessage, Reply}
import cc.otavia.core.timer.Timer

import scala.annotation.tailrec
import scala.language.unsafeNulls

/** User interface for class [[MessagePromise]]
 *
 *  @tparam R
 *    type of [[Reply]]
 */
sealed trait MessageFuture[+R <: Reply] extends Future[R] {
    private[core] override def promise: MessagePromise[Reply] = this.asInstanceOf[MessagePromise[Reply]]

}

object MessageFuture {

    private[stack] val pool = new PromisePool[MessagePromise[?]] {
        override protected def newObject(): MessagePromise[?] = new MessagePromise[Nothing]()
    }

    def apply[R <: Reply](): MessageFuture[R] = pool.get().asInstanceOf[MessagePromise[R]]

}

final private[core] class MessagePromise[R <: Reply]() extends AbstractPromise[R] with MessageFuture[R] {

    /** Used by [[FutureDispatcher]], this class is see as Node in hashmap */
    private[stack] var hashNext: MessagePromise[?] = _

    @tailrec
    private[stack] def findNode(id: Long): MessagePromise[?] =
        if (id == aid) this else if (hashNext eq null) null else hashNext.findNode(id)

    override def future: MessageFuture[R] = this

    override def recycle(): Unit = MessageFuture.pool.recycle(this)

    override protected def cleanInstance(): Unit = {
        hashNext = null
        super.cleanInstance()
    }

    override def setSuccess(result: AnyRef): Unit = this.result = result

    override def setFailure(cause: Throwable): Unit = error = cause

}
