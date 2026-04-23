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

import cc.otavia.core.config.SpinLockConfig
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.LockSupport
import scala.language.unsafeNulls

private[core] class SpinLock(config: SpinLockConfig = SpinLockConfig()) extends AtomicReference[Thread] {

    private val spinThreshold = config.spinThreshold

    private val yieldThreshold = config.yieldThreshold

    private val parkNanos = config.parkNanos

    /** Acquire the lock using adaptive spinning with progressive backoff.
     *
     *  Phase 1 (spins 0..SPIN_THRESHOLD): pure spin with [[Thread.onSpinWait]] (x86 PAUSE). The vast majority of
     *  acquisitions succeed here — the critical sections protected by SpinLock are only a few instructions (pointer
     *  assignment + counter update), so uncontended or lightly contended CAS typically succeeds within 1-2 attempts.
     *
     *  Phase 2 (spins SPIN_THRESHOLD..YIELD_THRESHOLD): [[Thread.yield]] hints the OS scheduler to let the lock holder
     *  run. Triggered when the holder is preempted by the OS (time slice exhaustion) or delayed by a brief GC pause.
     *  Without yielding, the spinning thread burns CPU without making progress since the holder isn't on-core.
     *
     *  Phase 3 (spins > YIELD_THRESHOLD): [[LockSupport.parkNanos]](1μs) truly frees the CPU core. Triggered during
     *  long GC STW pauses (10-200ms) where the lock holder is suspended at a safepoint and cannot release the lock.
     *  Pure spinning during such pauses wastes an entire core and degrades tail latency for co-tenant workloads.
     */
    final def lock(): Unit = {
        val thread = Thread.currentThread()
        var spins  = 0
        while (!this.compareAndSet(null, thread)) {
            spins += 1
            if spins < spinThreshold then Thread.onSpinWait()
            else if spins < yieldThreshold then Thread.`yield`()
            else {
                LockSupport.parkNanos(parkNanos)
                spins = yieldThreshold // reset to avoid growing indefinitely
            }
        }
    }

    /** Release the lock. No thread-check — all callers are kernel-internal (private[core]) with structured
     *  lock/unlock pairs in try/finally, so wrong-thread unlock cannot happen by construction.
     */
    final def unlock(): Unit = {
        this.lazySet(null)
    }

    /** Try to acquire the lock with a single CAS attempt. Returns immediately regardless of success. Unlike [[lock]],
     *  this method never spins — suitable for opportunistic acquisitions where contention should result in immediate
     *  fallback rather than waiting.
     */
    final def tryLock(): Boolean = compareAndSet(null, Thread.currentThread())

    /** Check the lock whether is locked. */
    final def isLocked: Boolean = this.get() != null

    /** Check the lock whether is locked by current thread. */
    final def isHeldByCurrentThread: Boolean = Thread.currentThread() == this.get()

    /** Try to get lock until get the lock or spin [[timeout]] nanosecond for timeout.
     *  @param timeout
     *    timeout nanosecond.
     *  @return
     *    whether get the lock.
     */
    final def tryLock(timeout: Long): Boolean = {
        val thread = Thread.currentThread()
        val start  = System.nanoTime()

        // spin until get lock or timeout
        while (!this.compareAndSet(null, thread) && (System.nanoTime() - start < timeout)) {}

        thread == this.get()
    }

}
