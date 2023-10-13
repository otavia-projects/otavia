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

package cc.otavia.core.stack

import cc.otavia.core.cache.Poolable
import cc.otavia.core.message.Reply

import scala.reflect.{ClassTag, classTag}

trait StackState {

    private val option: Option[StackState] = Some(this) // for pooling Some(this) object to reduce GC

    def resumable(): Boolean = false

    def id: Int = 0

    def suspend(): Option[StackState] = option // TODO: check whether has uncompleted promise

}

object StackState {

    val start: StackState = new StackState {
        final override def resumable(): Boolean = true
    }

    class FutureState[R <: Reply: ClassTag] extends StackState {

        val replyType: ClassTag[R] = classTag[R]
        val future: ReplyFuture[R] = ReplyFuture()

    }

    class ChannelReplyState extends StackState {
        val future: ChannelReplyFuture = ChannelReplyFuture()
    }

    class ChannelFutureState extends StackState {
        val future: ChannelFuture = ChannelFuture()
    }

}
