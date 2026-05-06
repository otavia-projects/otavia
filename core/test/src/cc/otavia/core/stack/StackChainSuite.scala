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

import org.scalatest.funsuite.AnyFunSuite

class StackChainSuite extends AnyFunSuite {

    private class TestStack extends Stack {
        var done = false
        override def isDone: Boolean            = done
        override def recycle(): Unit            = {}
        override protected def cleanInstance(): Unit = super.cleanInstance()
        def reset(): Unit = cleanInstance()
    }

    private def newStack: TestStack = {
        val s = new TestStack
        s.setState(StackState.start)
        s
    }

    private def newPromise(id: Long): MessagePromise[?] = {
        val p = new MessagePromise[Nothing]()
        p.setId(id)
        p
    }

    // ---- addUncompletedPromise / hasUncompletedPromise ----

    test("empty stack has no uncompleted promise") {
        val stack = newStack
        assert(!stack.hasUncompletedPromise)
        assert(!stack.hasCompletedPromise)
    }

    test("addUncompletedPromise: single promise") {
        val stack   = newStack
        val promise = newPromise(1)
        stack.addUncompletedPromise(promise)

        assert(stack.hasUncompletedPromise)
        assert(!stack.hasCompletedPromise)
        assert(stack.uncompletedPromiseCount == 1)
    }

    test("addUncompletedPromise: multiple promises maintain order") {
        val stack = newStack
        val p1    = newPromise(1)
        val p2    = newPromise(2)
        val p3    = newPromise(3)

        stack.addUncompletedPromise(p1)
        stack.addUncompletedPromise(p2)
        stack.addUncompletedPromise(p3)

        assert(stack.uncompletedPromiseCount == 3)
    }

    // ---- moveCompletedPromise ----

    test("moveCompletedPromise: single promise moves from uncompleted to completed") {
        val stack   = newStack
        val promise = newPromise(1)
        stack.addUncompletedPromise(promise)

        stack.moveCompletedPromise(promise)

        assert(!stack.hasUncompletedPromise)
        assert(stack.hasCompletedPromise)
        assert(stack.completedPromiseCount == 1)
    }

    test("moveCompletedPromise: head of chain") {
        val stack = newStack
        val p1    = newPromise(1)
        val p2    = newPromise(2)
        val p3    = newPromise(3)

        stack.addUncompletedPromise(p1)
        stack.addUncompletedPromise(p2)
        stack.addUncompletedPromise(p3)

        stack.moveCompletedPromise(p1)

        assert(stack.uncompletedPromiseCount == 2)
        assert(stack.completedPromiseCount == 1)
    }

    test("moveCompletedPromise: tail of chain") {
        val stack = newStack
        val p1    = newPromise(1)
        val p2    = newPromise(2)
        val p3    = newPromise(3)

        stack.addUncompletedPromise(p1)
        stack.addUncompletedPromise(p2)
        stack.addUncompletedPromise(p3)

        stack.moveCompletedPromise(p3)

        assert(stack.uncompletedPromiseCount == 2)
        assert(stack.completedPromiseCount == 1)
    }

    test("moveCompletedPromise: middle of chain") {
        val stack = newStack
        val p1    = newPromise(1)
        val p2    = newPromise(2)
        val p3    = newPromise(3)

        stack.addUncompletedPromise(p1)
        stack.addUncompletedPromise(p2)
        stack.addUncompletedPromise(p3)

        stack.moveCompletedPromise(p2)

        assert(stack.uncompletedPromiseCount == 2)
        assert(stack.completedPromiseCount == 1)
    }

    test("moveCompletedPromise: all promises moved one by one") {
        val stack = newStack
        val p1    = newPromise(1)
        val p2    = newPromise(3)
        val p3    = newPromise(5)

        stack.addUncompletedPromise(p1)
        stack.addUncompletedPromise(p2)
        stack.addUncompletedPromise(p3)

        stack.moveCompletedPromise(p2)
        assert(stack.uncompletedPromiseCount == 2)
        assert(stack.completedPromiseCount == 1)

        stack.moveCompletedPromise(p1)
        assert(stack.uncompletedPromiseCount == 1)
        assert(stack.completedPromiseCount == 2)

        stack.moveCompletedPromise(p3)
        assert(!stack.hasUncompletedPromise)
        assert(stack.completedPromiseCount == 3)
    }

    test("moveCompletedPromise: clears pre and next pointers of moved promise") {
        val stack = newStack
        val p1    = newPromise(1)
        val p2    = newPromise(2)

        stack.addUncompletedPromise(p1)
        stack.addUncompletedPromise(p2)

        // p2 has pre=p1, next=null
        stack.moveCompletedPromise(p2)
        assert(p2.pre eq null)
        assert(p2.next eq null)
    }

    // ---- recycleCompletedPromises ----

    test("recycleCompletedPromises: clears all completed promises") {
        val stack = newStack
        val p1    = newPromise(1)
        val p2    = newPromise(2)

        stack.addUncompletedPromise(p1)
        stack.addUncompletedPromise(p2)

        stack.moveCompletedPromise(p1)
        stack.moveCompletedPromise(p2)

        assert(stack.completedPromiseCount == 2)

        stack.recycleCompletedPromises()

        assert(!stack.hasCompletedPromise)
        assert(stack.completedPromiseCount == 0)
    }

    // ---- setState recycles completed promises ----

    test("setState recycles completed promises") {
        val stack = newStack
        val p1    = newPromise(1)

        stack.addUncompletedPromise(p1)
        stack.moveCompletedPromise(p1)

        assert(stack.hasCompletedPromise)

        // setState should recycle completed promises
        stack.setState(StackState.start)
        assert(!stack.hasCompletedPromise)
    }

    // ---- suspend / getNextState ----

    test("suspend stores nextState and returns SUSPEND") {
        val stack = newStack
        val state = new StackState { override def id: Int = 42 }

        val yield_ = stack.suspend(state)
        assert(!yield_.completed)

        val retrieved = stack.getNextState
        assert(retrieved eq state)
        assert(retrieved.id == 42)
    }

    test("getNextState is one-shot: returns null on second call") {
        val stack = newStack
        stack.suspend(StackState.start)

        stack.getNextState
        val second = stack.getNextState
        assert(second eq null)
    }

    // ---- cleanInstance ----

    test("cleanInstance resets state and recycles completed promises") {
        val stack = newStack
        val p1    = newPromise(1)

        stack.addUncompletedPromise(p1)
        stack.moveCompletedPromise(p1)

        stack.reset()

        assert(stack.state eq StackState.start)
        assert(!stack.hasCompletedPromise)
        assert(!stack.hasUncompletedPromise)
    }

}
