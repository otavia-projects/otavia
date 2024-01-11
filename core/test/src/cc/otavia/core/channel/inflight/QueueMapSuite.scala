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

package cc.otavia.core.channel.inflight

import org.scalatest.funsuite.AnyFunSuiteLike

import scala.language.unsafeNulls

class QueueMapSuite extends AnyFunSuiteLike {

    import QueueMapSuite.*

    test("queue op") {
        val queueMap = new QueueMap[TestQueueMapEntity]()
        0 until 10 foreach { idx =>
            val entity = new TestQueueMapEntity()
            entity.setId(idx)
            queueMap.append(entity)
        }
        assert(queueMap.size == 10)
        queueMap.remove(5)
        assert(!queueMap.contains(5))
        assert(queueMap.size == 9)
        for (idx <- 0 until 10 if idx != 5) assert(queueMap.contains(idx))

        val entity = new TestQueueMapEntity()
        entity.setId(11)
        queueMap.append(entity)

        assert(queueMap.size == 10)

        queueMap.remove(0)

        assert(queueMap.size == 9)

        queueMap.remove(11)

        assert(queueMap.size == 8)

    }

    test("remove one") {
        val queueMap = new QueueMap[TestQueueMapEntity]()
        val entity   = new TestQueueMapEntity()
        entity.setId(1)
        queueMap.append(entity)

        queueMap.remove(1)

        assert(queueMap.isEmpty)
        assert(queueMap.first == null)

    }

    test("append pop") {
        val queueMap = new QueueMap[TestQueueMapEntity]()
        0 until 10 foreach { idx =>
            val entity = new TestQueueMapEntity()
            entity.setId(idx)
            queueMap.append(entity)
        }
        assert(queueMap.pop().entityId == 0)
        assert(queueMap.size == 9)
        assert(!queueMap.contains(0))

        assert(queueMap.pop().entityId == 1)
        assert(queueMap.size == 8)
        assert(!queueMap.contains(1))

        assert(queueMap.pop().entityId == 2)
        assert(queueMap.size == 7)
        assert(!queueMap.contains(2))

        assert(queueMap.pop().entityId == 3)
        assert(queueMap.size == 6)
        assert(!queueMap.contains(3))
    }

}

object QueueMapSuite {
    class TestQueueMapEntity() extends QueueMapEntity {

        private var id: Int         = 0
        override def entityId: Long = id

        def setId(i: Int): Unit = id = i

    }
}
