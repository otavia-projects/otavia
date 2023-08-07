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

package cc.otavia.core.system

import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}

class TilingThreadSelector(private val threads: Array[ActorThread]) extends ThreadSelector {

    private val selector = new AtomicInteger(0)

    override def select(): ActorThread = {
        var current = selector.get()
        while (!selector.compareAndSet(current, (current + 1) % threads.length)) current = selector.get()
        threads((current + 1) % threads.length)
    }

    override def select(num: Int): Seq[ActorThread] = {
        val mod = num % threads.length
        val fac = num / threads.length

        var current = selector.get()
        while (!selector.compareAndSet(current, (current + mod) % threads.length)) current = selector.get()
        val parts = for (idx <- current until current + mod) yield threads(idx % threads.length)
        if (fac > 0) {
            (0 until fac)
                .map { _ =>
                    val part = new Array[ActorThread](threads.length)
                    System.arraycopy(threads, 0, part, 0, threads.length)
                    part
                }
                .reduce(_ ++ _) ++ parts
        } else parts

    }

}
