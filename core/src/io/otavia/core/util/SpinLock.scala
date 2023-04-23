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

package io.otavia.core.util

import io.otavia.core.system.ActorThread

import java.util.concurrent.atomic.AtomicReference
import scala.language.unsafeNulls

private[core] class SpinLock {

    private val holder = new AtomicReference[Thread](null)

    def lock(): Unit = {
        val thread = Thread.currentThread()
        while (!holder.compareAndSet(null, thread)) {} // spin until get lock
    }

    def unlock(): Unit = {
        assert(Thread.currentThread() == holder.get(), "Unlock thread is not the lock holder")
        holder.set(null)
    }

    def isLock: Boolean = holder.get() != null

    def isLockByMe: Boolean = Thread.currentThread() == holder.get()

    def tryLock(tryTimes: Int): Boolean = {
        val thread = Thread.currentThread()
        var times  = 0
        while (holder.compareAndSet(null, thread) && times < tryTimes) {
            times += 1
        }
        times < tryTimes
    }

}
