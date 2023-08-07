/*
 * Copyright 2022 Yan Kun <yan_kun_1992@foxmail.com>
 *
 * This file fork from netty.
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

package cc.otavia.core.reactor

/** The execution context for an [[IoHandler]]. All method must be called from the [[Reactor]] thread. */
trait IoExecutionContext {

    /** Returns `true` if blocking for IO is allowed or if we should try to do a non-blocking request for IO to be
     *  ready.
     */
    def canBlock: Boolean

    /** Returns the amount of time left until the scheduled task with the closest deadline should run. */
    def delayNanos(currentTimeNanos: Long): Long

    /** Returns the absolute point in time at which the next closest scheduled task should run or `-1` if nothing is
     *  scheduled to run.
     */
    def deadlineNanos: Long

}
