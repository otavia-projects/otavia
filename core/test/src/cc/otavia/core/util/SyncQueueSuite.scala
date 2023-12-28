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

package cc.otavia.core.util

import org.scalatest.funsuite.AnyFunSuiteLike

import java.util.concurrent.atomic.AtomicLong

class SyncQueueSuite extends AnyFunSuiteLike {

    import SyncQueueSuite.*

    private val TOTAL = 1_000_000

    test("mpsc op") {
        val queue   = new SyncQueue[Node]()
        val counter = new AtomicLong(0)

        (0 until 8).foreach(i =>
            val thread = new Thread(new Runnable {
                override def run(): Unit = {
                    var i = 0
                    while (i < TOTAL) {
                        queue.enqueue(new Node)
                        counter.incrementAndGet()
                        i += 1
                    }
                }
            })
            thread.start()
        )

        Thread.sleep(10)

        var counter2 = 0L
        while (counter2 < TOTAL * 8) {
            if (queue.nonEmpty) {
                val node = queue.dequeue()
                counter2 += 1
                assert(node != null)
            }
        }

        assert(counter.get() == counter2)
        assert(counter.get() == TOTAL * 8)
    }

}

object SyncQueueSuite {
    class Node extends Nextable
}
