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

package cc.otavia.core.config

/** Spin lock tuning configuration.
 *
 *  Spin locks use adaptive spinning with progressive backoff through three phases:
 *  1. Pure spin with [[Thread.onSpinWait]] (x86 PAUSE instruction)
 *  2. [[Thread.yield]] to hint the OS scheduler
 *  3. [[LockSupport.parkNanos]] to truly free the CPU core
 *
 *  The critical sections protected by spin locks are typically only a few instructions (pointer assignment + counter
 *  update), so uncontended or lightly contended CAS usually succeeds within 1-2 attempts.
 *
 *  @param spinThreshold
 *    Number of spin iterations in Phase 1 before switching to yield. Covers the common case of uncontended or
 *    lightly contended lock acquisition. Default is 100.
 *  @param yieldThreshold
 *    Number of spin iterations before switching from yield to park. Entered when the lock holder has been preempted
 *    by the OS scheduler or paused by a brief GC event. Default is 200.
 *  @param parkNanos
 *    Duration in nanoseconds to park in Phase 3. Triggered during long GC STW pauses (10-200ms) where the lock holder
 *    is suspended at a safepoint. Default is 1000ns (1μs).
 */
case class SpinLockConfig(
    spinThreshold: Int  = 100,
    yieldThreshold: Int = 200,
    parkNanos: Long     = 1000
)
