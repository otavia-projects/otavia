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

import io.otavia.core.reactor.{Event, ReactorEvent}
import io.otavia.core.stack.{ChannelFuture, ChannelReplyFuture, DefaultFuture, Future}

import java.net.SocketAddress

trait ChannelInflight extends ChannelOutboundInvoker {

    /** Inbound message barrier function */
    def inboundMessageBarrier: AnyRef => Boolean

    /** Set inbound message barrier function
     *  @param barrier
     *    a function to check a [[AnyRef]] object is barrier.
     */
    def setInboundMessageBarrier(barrier: AnyRef => Boolean): Unit

    /** Outbound message barrier function. */
    def outboundMessageBarrier: AnyRef => Boolean

    /** Set inbound message barrier
     *  @param barrier
     *    a function to check a [[AnyRef]] object is barrier.
     */
    def setOutboundMessageBarrier(barrier: AnyRef => Boolean): Unit

    def maxOutboundInflight: Int = 1

    def outboundInflightSize: Int
    def outboundPendingSize: Int

    def inboundInflightSize: Int
    def inboundPendingSize: Int

    // actor send ask message to channel, in underlying, it call channel.write
    def ask(value: AnyRef, future: ChannelReplyFuture): ChannelReplyFuture

    def batchAsk(asks: Seq[AnyRef], futures: Seq[ChannelReplyFuture]): Seq[ChannelReplyFuture]

    // actor send notice message to channel
    def notice(value: AnyRef): Unit

    def batchNotice(notices: Seq[AnyRef]): Unit

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

object ChannelInflight {
    val INVALID_CHANNEL_MESSAGE_ID: Long = -1
}
