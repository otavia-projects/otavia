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

package io.otavia.core.actor

import io.otavia.core.actor.Actor
import io.otavia.core.stack.{ReplyFuture, ReplyPromise}

import scala.collection.mutable
import scala.language.unsafeNulls

/** All [[ReplyFuture]] that a [[Actor]] instance is waiting. */
private[core] class ActorFutures {

    private var futures: mutable.HashMap[Long, ReplyPromise[?]] = _

    private def init(): Unit = futures = mutable.HashMap.empty[Long, ReplyPromise[?]]

    def push(askId: Long, future: ReplyPromise[?]): Unit = if (futures != null) futures.put(askId, future)
    else {
        init()
        futures.put(askId, future)
    }

    def pop(askId: Long): ReplyPromise[?] = futures.remove(askId).get

    def remove(askId: Long): Unit = futures.remove(askId)

    def contains(id: Long): Boolean = if (futures != null) futures.contains(id) else false

}
