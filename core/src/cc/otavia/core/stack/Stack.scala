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

import cc.otavia.core.actor.{AbstractActor, Actor}
import cc.otavia.core.cache.Poolable
import cc.otavia.core.message.Call
import cc.otavia.core.util.Chainable

import scala.language.unsafeNulls

abstract class Stack extends Poolable {

    private var stackState: StackState = StackState.start
    private var error: Boolean         = false

    // completed promise
    private var completedHead: AbstractPromise[?] = _
    private var completedTail: AbstractPromise[?] = _

    // uncompleted promise
    private var uncompletedHead: AbstractPromise[?] = _
    private var uncompletedTail: AbstractPromise[?] = _

    // context
    private var actor: AbstractActor[?] = _

    private[core] def runtimeActor: AbstractActor[?] = actor

    private[core] def setRuntimeActor(a: AbstractActor[?]): Unit = actor = a

    final def state: StackState = stackState

    private[core] final def setState(stackState: StackState): Unit = {
        recycleCompletedPromises()
        this.stackState = stackState
    }

    private[core] def setFailed(): Unit = error = true
    private[core] def isFailed: Boolean = error

    def isDone: Boolean

    override protected def cleanInstance(): Unit = {
        recycleCompletedPromises()
        recycleUncompletedPromises()
        stackState = StackState.start
        error = false
        actor = null
    }

    private[core] def moveCompletedPromise(completed: AbstractPromise[?]): Unit = {
        // the completed already in uncompleted chain. move it
        // step 1: remove it from uncompleted chain
        val pre  = completed.pre
        val next = completed.next
        completed.deChain()
        pre match
            case null =>
                next match
                    case null =>
                        uncompletedHead = null
                        uncompletedTail = null
                    case nextNode: Chainable =>
                        nextNode.cleanPre()
                        uncompletedHead = nextNode.asInstanceOf[AbstractPromise[?]]
            case preNode: Chainable =>
                next match
                    case null =>
                        preNode.cleanNext()
                        uncompletedTail = preNode.asInstanceOf[AbstractPromise[?]]
                    case nextNode: Chainable => preNode.next = nextNode

        // step 2: add completed to completed chain
        if (completedHead == null) {
            completedHead = completed
            completedTail = completed
        } else {
            val oldTail = completedTail
            oldTail.next = completed
            completedTail = completed
        }
    }

    private[core] def addUncompletedPromise(uncompleted: AbstractPromise[?]): Unit = {
        if (uncompletedHead == null) {
            uncompletedHead = uncompleted
            uncompletedTail = uncompleted
        } else {
            val oldTail = uncompletedTail
            oldTail.next = uncompleted
            uncompleted.pre = oldTail
            uncompletedTail = uncompleted
        }
    }

    private[core] def addUncompletedPromiseIterator(iterator: PromiseIterator): Unit = {
        if (uncompletedHead == null) {
            uncompletedHead = iterator.head
            uncompletedTail = iterator.tail
        } else {
            uncompletedTail.next = iterator.head
            iterator.head.next = uncompletedTail
            uncompletedTail = iterator.tail
        }
        iterator.clean()
    }

    private[core] def hasUncompletedPromise: Boolean = uncompletedHead != null

    private[core] def hasCompletedPromise: Boolean = completedHead != null

    private[core] def completedPromiseCount: Int = {
        var cursor: Chainable = completedHead
        var count             = 0
        while (cursor != null) {
            cursor = cursor.next
            count += 1
        }
        count
    }

    private[core] def uncompletedPromiseCount: Int = {
        var cursor: Chainable = uncompletedHead
        var count             = 0
        while (cursor != null) {
            cursor = cursor.next
            count += 1
        }
        count
    }

    private[core] final def recycleCompletedPromises(): Unit = {
        completedTail = null
        while (completedHead != null) {
            val promise = completedHead
            completedHead = promise.next.asInstanceOf[AbstractPromise[?]]
            promise.recycle()
        }
    }

    private def recycleUncompletedPromises(): Unit = {
        uncompletedTail = null
        while (uncompletedHead != null) {
            val promise = uncompletedHead
            uncompletedHead = promise.next.asInstanceOf[AbstractPromise[?]]
            uncompletedHead.cleanPre()
            promise.recycle()
        }
    }

    private[core] def uncompletedPromises(): PromiseIterator = {
        val iterator = PromiseIterator(uncompletedHead, uncompletedTail)
        uncompletedHead = null
        uncompletedTail = null
        iterator
    }

}
