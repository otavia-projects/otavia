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

package io.otavia.core.system

import io.otavia.core.util.{Nextable, SpinLock}

import java.util.concurrent.atomic.AtomicInteger
import scala.language.unsafeNulls

class HouseQueue(val holder: HouseQueueHolder) {

    // for normal priority actor house
    private val readLock                   = new SpinLock()
    private val writeLock                  = new SpinLock()
    private val size                       = new AtomicInteger(0)
    @volatile private var head: ActorHouse = _
    @volatile private var tail: Nextable   = _

    // for high priority actor house
    private val highReadLock                 = new SpinLock()
    private val highWriteLock                = new SpinLock()
    private val highSize                     = new AtomicInteger(0)
    @volatile private var highHead: Nextable = _
    @volatile private var highTail: Nextable = _

    def available: Boolean = (highSize.get() > 0) || (size.get() > 0)

    def readies: Int = highSize.get() + size.get()

    def poll(): ActorHouse = {

        ???
    }

    def poll(timeout: Long): ActorHouse | Null = {
        val slice                    = if (timeout > 500) 500 else timeout
        val start                    = System.nanoTime()
        var spin: Boolean            = true
        var house: ActorHouse | Null = null
        while (spin) {
            if (highSize.get() > 0 && highReadLock.tryLock(slice)) {
                house = ???
                //
            } else if (size.get() > 0 && readLock.tryLock(slice)) {
                //
            }
            if (System.nanoTime() - start > timeout) spin = false
        }

        house
    }

}
