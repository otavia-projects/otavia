/*
 * Copyright 2022 Yan Kun
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

package io.otavia.core.stack

private[core] class ReplyWaiters {
    val waiters: collection.mutable.HashMap[Long, ReplyWaiter[?]] = collection.mutable.HashMap.empty

    def push(askId: Long, waiter: ReplyWaiter[?]): Unit = waiters.put(askId, waiter)

    def pop(askId: Long): ReplyWaiter[?] = waiters.remove(askId).get

    def contains(id: Long): Boolean = waiters.contains(id)

}
