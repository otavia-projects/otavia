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

/** A handle associated with a TimerTask that is returned by a [[InternalTimer]]. */
trait Timeout {

    /** Returns the [[InternalTimer]] that created this handle. */
    def timer: InternalTimer

    /** Returns the [[TimerTask]] which is associated with this handle. */
    def task: TimerTask

    /** Returns `true` if and only if the [[TimerTask]] associated with this handle has been expired. */
    def isExpired: Boolean

    /** Returns `true` if and only if the [[TimerTask]] associated with this handle has been cancelled. */
    def isCancelled: Boolean

    /** Attempts to cancel the [[TimerTask]] associated with this handle. If the task has been executed or cancelled
     *  already, it will return with no side effect.
     *
     *  @return
     *    True if the cancellation completed successfully, otherwise false
     */
    def cancel: Boolean

    /** Returns true if and only if the [[Timeout]] is periodic. */
    def periodic: Boolean

}
