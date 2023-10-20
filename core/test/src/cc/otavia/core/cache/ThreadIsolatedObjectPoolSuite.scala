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

package cc.otavia.core.cache

import cc.otavia.core.cache.ThreadIsolatedObjectPoolSuite.*
import cc.otavia.core.timer.TimeoutTrigger
import org.scalatest.funsuite.AnyFunSuiteLike

import scala.collection.mutable

class ThreadIsolatedObjectPoolSuite extends ThreadIsolatedObjectPool[EmptyPoolObject] with AnyFunSuiteLike {

    override protected val timeoutTrigger: Option[TimeoutTrigger] = None

    override protected def handleTimeout(registerId: Long, threadLocalTimer: ThreadLocalTimer): Unit = {}

    override protected def newObject(): EmptyPoolObject = new EmptyPoolObject(this)

    test("get pool object") {
        val instance = this.get()

        assert(instance.notInChain)

        instance.state = 1
        instance.recycle()

        assert(instance.state == 0)
        assert(holder().size == 1)
    }

    test("loop get recycle") {
        val h = holder()

        val buffer = mutable.ArrayBuffer.empty[EmptyPoolObject]
        0 until holder().maxSize * 10 foreach { _ =>
            val instance = this.get()
            buffer.addOne(instance)
        }

        for (elem <- buffer) elem.recycle()
        buffer.clear()

        assert(holder().size == holder().maxSize)

        0 until 10 foreach { _ =>
            val instance = this.get(); instance.state = 1; instance.recycle()
        }

        assert(holder().size == holder().maxSize)

        0 until 100 foreach { _ => buffer.addOne(this.get()) }

        assert(holder().size == holder().maxSize - 100)

        for (elem <- buffer) elem.recycle()
        buffer.clear()

        assert(holder().size == holder().maxSize)

    }

}

object ThreadIsolatedObjectPoolSuite {
    class EmptyPoolObject(val pool: ObjectPool[EmptyPoolObject]) extends Poolable {

        var state: Int = 0

        override def recycle(): Unit = pool.recycle(this)

        override protected def cleanInstance(): Unit = state = 0

    }
}
