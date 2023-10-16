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

import cc.otavia.core.cache.ActorThreadLocal
import cc.otavia.core.stack.PromiseIterator.threadLocal

import scala.language.unsafeNulls

private[core] class PromiseIterator private () extends Iterator[AbstractPromise[?]] {

    var head: AbstractPromise[?] = _
    var tail: AbstractPromise[?] = _

    override def hasNext: Boolean = {
        val has = head != null
        if (!has) tail = null
        has
    }

    override def next(): AbstractPromise[?] = {
        val promise = head
        head = promise.next.asInstanceOf[AbstractPromise[?] | Null]
        promise.deChain()
        promise
    }

    def nextCast[P <: AbstractPromise[?]](): P = next().asInstanceOf[P]

    def clean(): Unit = {
        head = null
        tail = null
    }

}

private[core] object PromiseIterator {

    def apply(head: AbstractPromise[?], tail: AbstractPromise[?]): PromiseIterator = {
        val iterator = threadLocal.get()
        iterator.head = head
        iterator.tail = tail
        iterator
    }

    private val threadLocal = new ActorThreadLocal[PromiseIterator] {
        override protected def initialValue(): PromiseIterator = new PromiseIterator()
    }

}
