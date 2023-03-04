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

import io.otavia.core.actor.{AbstractActor, Actor}
import io.otavia.core.cache.Poolable
import io.otavia.core.message.Call
import io.otavia.core.util.Chainable

import scala.language.unsafeNulls

object Stack {
    class UncompletedPromiseIterator(private var head: Promise[?]) extends Iterator[Promise[?]] {

        override def hasNext: Boolean = head != null

        override def next(): Promise[?] = {
            val promise = head
            head = promise.next.asInstanceOf[Promise[?] | Null]
            promise.dechain()
            promise
        }

        def nextCast[P <: Promise[?]](): P = next().asInstanceOf[P]

    }

}

abstract class Stack extends Poolable {

    private var state          = StackState.initialState
    private var error: Boolean = false

    // completed promise
    private var headPromise: Promise[?] = _
    private var tailPromise: Promise[?] = _

    // uncompleted promise
    private var uhead: Promise[?] = _
    private var utail: Promise[?] = _

    // context
    private var actor: AbstractActor[?] = _

    private[core] def runtimeActor: AbstractActor[?]             = actor
    private[core] def setRuntimeActor(a: AbstractActor[?]): Unit = actor = a

    def stackState: StackState = state
    private[core] def setState(stackState: StackState): Unit = if (this.state != stackState) {
        recycleAllPromises()
        this.state = stackState
    }

    private[core] def setFailed(): Unit = error = true
    private[core] def isFailed: Boolean = error

    override protected def cleanInstance(): Unit = {
        recycleAllPromises()
        state = StackState.initialState
        error = false
        actor = null
    }

    private[core] def addCompletedPromise(completed: Promise[?]): Unit = {
        // the completed already in uncompleted chain. move it
        // step 1: remove it from uncompleted chain
        val pre  = completed.pre
        val next = completed.next
        completed.dechain()
        pre match
            case null =>
                next match
                    case null                =>
                    case nextNode: Chainable => nextNode.cleanPre()
            case preNode: Chainable =>
                next match
                    case null                => preNode.cleanNext()
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

    private[core] def addUncompletedPromise(uncompleted: Promise[?]): Unit = {
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

    private[core] def recycleAllPromises(): Unit = while (headPromise != null) {
        val promise = headPromise
        if (headPromise == tailPromise) tailPromise = null
        headPromise = promise.next.asInstanceOf[Promise[?] | Null]
        promise.recycle()
    }

    private[core] def uncompletedIterator(): Stack.UncompletedPromiseIterator = {
        val iterator = new Stack.UncompletedPromiseIterator(uhead)
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
        super.cleanInstance()
        msg = null
    }

    def isDone: Boolean

}
