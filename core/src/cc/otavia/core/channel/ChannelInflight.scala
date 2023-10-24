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

package cc.otavia.core.channel

import cc.otavia.core.message.{Event, ReactorEvent}
import cc.otavia.core.stack.{ChannelFuture, ChannelReplyFuture, Future}

import java.net.SocketAddress

trait ChannelInflight {

    /** Inbound message barrier function */
    def futureMessageBarrier: AnyRef => Boolean

    /** Set inbound message barrier function
     *  @param barrier
     *    a function to check a [[AnyRef]] object is barrier.
     */
    def setFutureMessageBarrier(barrier: AnyRef => Boolean): Unit

    /** Outbound message barrier function. */
    def stackMessageBarrier: AnyRef => Boolean

    /** Set inbound message barrier
     *  @param barrier
     *    a function to check a [[AnyRef]] object is barrier.
     */
    def setStackMessageBarrier(barrier: AnyRef => Boolean): Unit

    def setMaxFutureInflight(max: Int): Unit

    def setMaxStackInflight(max: Int): Unit

    def inflightFutureSize: Int
    def pendingFutureSize: Int

    def inflightStackSize: Int
    def pendingStackSize: Int

    def setStackHeadOfLine(hol: Boolean): Unit

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
