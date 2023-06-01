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

import io.otavia.core.cache.Poolable

import scala.annotation.tailrec
import scala.language.unsafeNulls

/** An abstract class for [[Promise]] */
abstract class AbstractPromise[V] extends Promise[V] with Poolable {

    protected var stack: Stack = _
    protected var aid: Long    = -1

    /** Used by [[FutureDispatcher]], this class is see as Node in hashmap */
    private[stack] var _next: AbstractPromise[?] = _
    @tailrec
    private[stack] final def findNode(id: Long): AbstractPromise[?] = {
        if (id == aid) this else if (_next eq null) null else _next.findNode(id)
    }

    final override def actorStack: Stack        = stack
    final override def setStack(s: Stack): Unit = stack = s

    final def id: Long              = aid
    final def setId(id: Long): Unit = aid = id

    override protected def cleanInstance(): Unit = {
        stack = null
        aid = -1
    }

    override def canTimeout: Boolean = false
    
    

}
