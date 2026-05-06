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

class FutureDispatcherOpsSuite extends AnyFunSuite {

    private class TestDispatcher extends FutureDispatcher {
        def pushPromise(promise: MessagePromise[?]): Unit = push(promise)
        def popPromise(id: Long): MessagePromise[?]      = pop(id)
        def containsId(id: Long): Boolean                = contains(id)
        def size: Int                                    = pendingPromiseCount
    }

    private def newPromise(id: Long): MessagePromise[?] = {
        val p = new MessagePromise[Nothing]()
        p.setId(id)
        p
    }

    // ---- push / pop basic ----

    test("push and pop single promise") {
        val d = new TestDispatcher
        val p = newPromise(42)

        d.pushPromise(p)
        assert(d.size == 1)
        assert(d.containsId(42))

        val retrieved = d.popPromise(42)
        assert(retrieved ne null)
        assert(retrieved.id == 42)
        assert(d.size == 0)
        assert(!d.containsId(42))
    }

    test("pop non-existent id returns null") {
        val d = new TestDispatcher
        assert(d.popPromise(999) eq null)
    }

    test("push and pop multiple promises") {
        val d   = new TestDispatcher
        val ids = Seq(1L, 2L, 3L, 4L, 5L)

        ids.foreach(id => d.pushPromise(newPromise(id)))
        assert(d.size == 5)

        ids.foreach(id => assert(d.containsId(id)))

        // pop in reverse order
        ids.reverse.foreach { id =>
            val p = d.popPromise(id)
            assert(p ne null, s"promise for id=$id should exist")
            assert(p.id == id)
        }
        assert(d.size == 0)
    }

    test("pop in same order as push") {
        val d   = new TestDispatcher
        val ids = Seq(10L, 20L, 30L)

        ids.foreach(id => d.pushPromise(newPromise(id)))

        ids.foreach { id =>
            val p = d.popPromise(id)
            assert(p ne null)
            assert(p.id == id)
        }
        assert(d.size == 0)
    }

    // ---- hash collision (same bucket) ----

    test("handles hash collisions correctly") {
        val d  = new TestDispatcher
        val p1 = newPromise(0)
        val p2 = newPromise(16) // same bucket if table size = 16 (mask = 15, 0 & 15 = 0, 16 & 15 = 0)

        d.pushPromise(p1)
        d.pushPromise(p2)

        assert(d.containsId(0))
        assert(d.containsId(16))

        val r1 = d.popPromise(0)
        assert(r1.id == 0)
        assert(d.containsId(16))

        val r2 = d.popPromise(16)
        assert(r2.id == 16)
        assert(d.size == 0)
    }

    // ---- expansion (resize up) ----

    test("expands table when exceeding load factor") {
        val d = new TestDispatcher
        // initial table size = 16, threshold = 12
        // push 14 promises to trigger expansion
        val promises = (0 until 14).map(i => newPromise(i * 100L))
        promises.foreach(d.pushPromise)

        // all should be retrievable after expansion
        promises.foreach { p =>
            assert(d.containsId(p.id))
        }

        assert(d.size == 14)
    }

    // ---- shrinkage ----

    test("shrinks table when usage drops below threshold") {
        val d = new TestDispatcher
        // Fill beyond initial capacity
        val promises = (0 until 60).map(i => newPromise(i * 100L))
        promises.foreach(d.pushPromise)

        assert(d.size == 60)

        // Pop most of them to trigger shrinkage (need < table.length / 8)
        promises.drop(2).foreach { p =>
            val r = d.popPromise(p.id)
            assert(r ne null, s"should find promise id=${p.id}")
        }

        assert(d.size == 2)
        assert(d.containsId(0))
        assert(d.containsId(100))
    }

    // ---- contains ----

    test("contains returns false for non-existent id") {
        val d = new TestDispatcher
        d.pushPromise(newPromise(1))
        assert(!d.containsId(2))
    }

    // ---- pendingPromiseCount ----

    test("pendingPromiseCount tracks accurate count") {
        val d = new TestDispatcher
        assert(d.size == 0)

        for (i <- 1 to 20) {
            d.pushPromise(newPromise(i))
            assert(d.size == i)
        }

        for (i <- 1 to 10) {
            d.popPromise(i)
            assert(d.size == 20 - i)
        }
    }

}
