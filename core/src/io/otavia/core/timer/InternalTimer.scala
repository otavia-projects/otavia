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

package io.otavia.core.timer

import scala.concurrent.duration.TimeUnit

/** Schedules TimerTasks for one-time future execution in a background thread. */
trait InternalTimer {

    /** Schedules the specified [[TimerTask]] for one-time execution after the specified delay.
     *
     *  @return
     *    a handle which is associated with the specified task
     *  @throws IllegalStateException
     *    if this timer has been [[stop]] stopped already
     *  @throws RejectedExecutionException
     *    if the pending timeouts are too many and creating new timeout can cause instability in the system.
     */
    def newTimeout(task: TimerTask, delay: Long, unit: TimeUnit): Timeout

    /** Releases all resources acquired by this [[InternalTimer]] and cancels all tasks which were scheduled but not
     *  executed yet.
     *
     *  @return
     *    the handles associated with the tasks which were canceled by this method
     */
    def stop: Set[Timeout]

}
