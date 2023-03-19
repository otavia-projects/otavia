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

import scala.language.unsafeNulls

trait DefaultFuture[V] extends Future[V] {

    private[core] override def promise: DefaultPromise[V] = this.asInstanceOf[DefaultPromise[V]]

}

object DefaultFuture {
    def apply[V](): DefaultFuture[V] = DefaultPromise()
}

class DefaultPromise[V] extends Promise[V] with DefaultFuture[V] {

    private var stack: Stack                  = _
    private var value: Any                    = _
    private var throwable: Throwable          = _
    private var completedFunc: () => Unit     = _
    private var downstream: DefaultPromise[V] = _

    def setStack(s: Stack): Unit = stack = s

    def actorStack: Stack = stack

    def isOnStack: Boolean = stack != null

    def onCompleted(func: () => Unit): Unit = completedFunc = func

    def setDownstream(down: DefaultPromise[V]): Unit = downstream = down

    override def setSuccess(result: V): Promise[V] = {
        value = result
        this
    }

    override def setFailure(cause: Throwable): Promise[V] = {
        throwable = cause
        this
    }

    override def future: Future[V] = this

    final override def canTimeout: Boolean = false

    override def isSuccess: Boolean = if (value != null) true else false

    override def isFailed: Boolean = if (throwable != null) true else false

    override def isDone: Boolean = (value != null) || (throwable != null)

    override def getNow: V = if (value == null && throwable == null)
        throw new IllegalStateException("not completed yet")
    else if (throwable != null) throw throwable
    else value.asInstanceOf[V]

    override def cause: Option[Throwable] = if (value == null && throwable == null)
        throw new IllegalStateException("not completed yet")
    else Option(throwable)

    override def causeUnsafe: Throwable = if (value == null && throwable == null)
        throw new IllegalStateException("not completed yet")
    else if (value != null) throw new IllegalStateException("the future is success")
    else throwable

    override def recycle(): Unit = DefaultPromise.pool.recycle(this)

    override protected def cleanInstance(): Unit = {
        value = null
        stack = null
        throwable = null
    }

}

object DefaultPromise {

    private val pool = new PromiseObjectPool[DefaultPromise[?]] {
        override protected def newObject(): DefaultPromise[_] = new DefaultPromise[Nothing]
    }

    def apply[V](): DefaultPromise[V] = pool.get().asInstanceOf[DefaultPromise[V]]

}
