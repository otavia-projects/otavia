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

package cc.otavia.core.channel.inflight

import cc.otavia.core.channel.ChannelInflight
import cc.otavia.core.stack.ChannelReplyPromise

import scala.collection.mutable
import scala.language.unsafeNulls

class FutureQueue {

    private var idmap: mutable.HashMap[Long, ChannelReplyPromise] = _
    private var queue: mutable.Queue[ChannelReplyPromise]         = _

    private var count: Int = 0

    private var recentlyAppend: ChannelReplyPromise = _
    private var recentlyAddId: Long                 = ChannelInflight.INVALID_CHANNEL_MESSAGE_ID

    def hasMessage(id: Long): Boolean =
        if (recentlyAppend == null) false
        else
            (recentlyAppend.messageId == id) || (if (idmap != null) idmap.contains(id) else false)

    def size: Int = count

    def isEmpty: Boolean = count == 0

    def nonEmpty: Boolean = count > 0

    def append(promise: ChannelReplyPromise): Unit = if (count == 0) {
        recentlyAppend = promise
        count += 1
    } else {
        if (idmap == null) idmap = mutable.HashMap.empty
        if (queue == null) queue = mutable.Queue.empty
        idmap.put(recentlyAppend.messageId, recentlyAppend)
        queue.enqueue(recentlyAppend)

        recentlyAppend = promise
        count += 1
    }

    def pop(): ChannelReplyPromise = if (count == 1) {
        val promise = recentlyAppend
        recentlyAppend = null
        count -= 1
        promise
    } else if (count > 1) {
        val promise = queue.dequeue()
        idmap.remove(promise.messageId)
        count -= 1
        promise
    } else throw new RuntimeException()

    def headIsBarrier: Boolean =
        if (queue != null && queue.nonEmpty) queue.head.isBarrier
        else if (recentlyAppend != null) recentlyAppend.isBarrier
        else false

}

object FutureQueue {}
