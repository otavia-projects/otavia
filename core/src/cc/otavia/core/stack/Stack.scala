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

    private var stackState     = StackState.start
    private var error: Boolean = false

    // completed promise
    private var headPromise: AbstractPromise[?] = _
    private var tailPromise: AbstractPromise[?] = _

    // uncompleted promise
    private var uhead: AbstractPromise[?] = _
    private var utail: AbstractPromise[?] = _

    // context
    private var actor: AbstractActor[?] = _

    private[core] def runtimeActor: AbstractActor[?] = actor

    private[core] def setRuntimeActor(a: AbstractActor[?]): Unit = actor = a

    def state: StackState = stackState

    private[core] def setState(stackState: StackState): Unit = if (this.stackState != stackState) {
        recycleCompletedPromises()
        this.stackState = stackState
    }

    private[core] def setFailed(): Unit = error = true
    private[core] def isFailed: Boolean = error

    override protected def cleanInstance(): Unit = {
        recycleCompletedPromises()
        stackState = StackState.start
        error = false
        actor = null
    }

    private[core] def addCompletedPromise(completed: AbstractPromise[?]): Unit = {
        // the completed already in uncompleted chain. move it
        // step 1: remove it from uncompleted chain
        val pre  = completed.pre
        val next = completed.next
        completed.deChain()
        pre match
            case null =>
                next match
                    case null =>
                        uhead = null
                        utail = null
                    case nextNode: Chainable =>
                        nextNode.cleanPre()
                        uhead = nextNode.asInstanceOf[AbstractPromise[?]]
            case preNode: Chainable =>
                next match
                    case null =>
                        preNode.cleanNext()
                        utail = preNode.asInstanceOf[AbstractPromise[?]]
                    case nextNode: Chainable => preNode.next = nextNode

        // step 2: add completed to completed chain
        if (headPromise == null) {
            headPromise = completed
            tailPromise = completed
        } else {
            val oldTail = tailPromise
            oldTail.next = completed
            completed.pre = oldTail
            tailPromise = completed
        }
    }

    private[core] def addUncompletedPromise(uncompleted: AbstractPromise[?]): Unit = {
        if (uhead == null) {
            uhead = uncompleted
            utail = uncompleted
        } else {
            val oldTail = utail
            oldTail.next = uncompleted
            uncompleted.pre = oldTail
            utail = uncompleted
        }
    }

    private[core] def addUncompletedPromiseIterator(iterator: PromiseIterator): Unit = {
        if (uhead == null) {
            uhead = iterator.head
            utail = iterator.tail
        } else {
            utail.next = iterator.head
            iterator.head.next = utail
            utail = iterator.tail
        }
        iterator.clean()
    }

    private[core] def hasUncompletedPromise: Boolean = uhead != null

    private[core] def hasCompletedPromise: Boolean = headPromise != null

    private[core] def completedPromiseCount: Int = {
        var cursor: Chainable = headPromise
        var count             = 0
        while (cursor != null) {
            cursor = cursor.next
            count += 1
        }
        count
    }

    private[core] def uncompletedPromiseCount: Int = {
        var cursor: Chainable = uhead
        var count             = 0
        while (cursor != null) {
            cursor = cursor.next
            count += 1
        }
        count
    }

    private[core] def recycleCompletedPromises(): Unit = while (headPromise != null) {
        val promise = headPromise
        if (headPromise == tailPromise) tailPromise = null
        headPromise = promise.next.asInstanceOf[AbstractPromise[?] | Null]
        promise.recycle()
    }

    private[core] def uncompletedPromises(): PromiseIterator = {
        val iterator = new PromiseIterator(uhead, utail)
        uhead = null
        utail = null
        iterator
    }

}

abstract class ActorStack extends Stack {

    private var msg: Call                    = _
    private[core] def setCall(c: Call): Unit = msg = c

    def call: Call = msg

    override protected def cleanInstance(): Unit = {
        msg = null
        super.cleanInstance()
    }

    def isDone: Boolean

}
