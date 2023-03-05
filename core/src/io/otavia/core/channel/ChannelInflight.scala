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

package io.otavia.core.channel

import io.otavia.core.stack.ChannelReplyFuture

trait ChannelInflight {

    /** The Channel inbound is head-of-line */
    def isHeadOfLine: Boolean

    /** Set the Channel inbound head-of-line
     *  @param hol
     *    head-of-line
     */
    def setHeadOfLine(hol: Boolean): Unit

    /** Inbound message barrier */
    def inboundMessageBarrier: AnyRef => Boolean

    /** Set inbound message barrier
     *  @param barrier
     */
    def setInboundMessageBarrier(barrier: AnyRef => Boolean): Unit

    // actor send ask message to channel
    def ask(value: AnyRef, future: ChannelReplyFuture): Unit

    // actor send notice message to channel
    def notice(value: AnyRef): Unit

    // actor send reply message to channel, the channel generate a ChannelFrame
    def reply(value: AnyRef): Unit

    /** generate a unique id for the channel message
     *
     *  @return
     *    id
     */
    def generateMessageId: Long

    /** Message from tail handler from pipeline. */
    private[core] def onInboundMessage(msg: AnyRef): Unit

    /** Message from tail handler from pipeline. */
    private[core] def onInboundMessage(msg: AnyRef, id: Long): Unit

}
