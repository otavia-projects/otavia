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

import org.scalatest.funsuite.AnyFunSuite

class SingleThreadPoolableHolderSuite extends AnyFunSuite {

    import SingleThreadPoolableHolderSuite.*

    test("pop push clean") {
        val holder = new SingleThreadPoolableHolder[EmptyPoolable]()

        0 until 10_000 foreach { _ => holder.push(new EmptyPoolable) }

        0 until holder.maxSize foreach { _ => holder.pop() }

        assert(holder.size == 0)

        0 until 10_000 foreach { _ => holder.push(new EmptyPoolable) }

        assert(holder.size == math.min(10_000, holder.maxSize))

        holder.clean(10)

        assert(holder.size == 10)
    }

}

object SingleThreadPoolableHolderSuite {
    class EmptyPoolable extends Poolable {

        override protected def cleanInstance(): Unit = {}

        override def recycle(): Unit = {}

    }
}
