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

import cc.otavia.core.cache.Poolable
import cc.otavia.core.timer.Timer

import scala.annotation.tailrec
import scala.language.unsafeNulls

/** An abstract class for [[Promise]] */
abstract class AbstractPromise[V] extends Promise[V] with Future[V] with Poolable {

    protected var stack: Stack = _
    protected var aid: Long    = -1

    private var tid: Long = Timer.INVALID_TIMEOUT_REGISTER_ID

    protected var result: AnyRef   = _
    protected var error: Throwable = _

    def setTimeoutId(id: Long): Unit = tid = id

    def timeoutId: Long = tid

    final override def canTimeout: Boolean = tid != Timer.INVALID_TIMEOUT_REGISTER_ID

    final override def actorStack: Stack        = stack
    final override def setStack(s: Stack): Unit = stack = s

    final def id: Long              = aid
    final def setId(id: Long): Unit = aid = id

    override def isSuccess: Boolean = result ne null

    override def isFailed: Boolean = error ne null

    override def getNow: V = if (result == null && error == null) throw new IllegalStateException("not completed yet")
    else if (error != null) throw error
    else result.asInstanceOf[V]

    override def cause: Option[Throwable] = if (result == null && error == null)
        throw new IllegalStateException("not completed yet")
    else Option(error)

    override def causeUnsafe: Throwable = if (result == null && error == null)
        throw new IllegalStateException("not completed yet")
    else if (result != null) throw new IllegalStateException("the future is success")
    else error

    override protected def cleanInstance(): Unit = {
        stack = null
        aid = -1
        tid = Timer.INVALID_TIMEOUT_REGISTER_ID
        result = null
        error = null
    }

}
