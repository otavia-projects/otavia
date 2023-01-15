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

package io.otavia.core.stack

import io.otavia.core.channel.Channel
import io.otavia.core.message.{Ask, ExceptionMessage, Reply}

sealed abstract class Waiter[R] {
    protected var r: R                  = _
    protected var askId: Long           = _
    private[core] var frame: StackFrame = _

    private[core] def setAskId(ask: Long): Unit = this.askId = ask

    private[core] def setFrame(stackFrame: StackFrame): Unit = this.frame = stackFrame

    def reply: R = r

    def received: Boolean = r != null
}

//class ChannelWaiter extends Waiter[Channel] {
//  private[core] def receive(channel: Channel | Exception)
//}

/** Waiter for channel io reply
 *  @tparam R
 *    type of reply message
 */
class ChannelReplyWaiter[RR] extends Waiter[RR | Exception] {

    private[core] def receive(reply: AnyRef): Unit = this.r = reply.asInstanceOf[RR | Exception]

    def isException: Boolean = if (received) r.isInstanceOf[Exception] else false
    def exception: Exception = r.asInstanceOf[Exception]

}

// Waiter for reply message from other actor

class ReplyWaiter[R <: Reply] extends Waiter[R] {

    private[core] def receive(reply: Reply): Unit = this.r = reply.asInstanceOf[R]

}

class ExceptionWaiter[R <: Reply] extends ReplyWaiter[R | ExceptionMessage]
