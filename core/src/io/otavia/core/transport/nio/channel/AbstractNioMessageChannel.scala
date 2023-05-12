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

package io.otavia.core.transport.nio.channel

import io.otavia.core.actor.ChannelsActor
import io.otavia.core.channel.estimator.{ReadHandleFactory, WriteHandleFactory}
import io.otavia.core.channel.internal.ReadSink

import java.net.SocketAddress
import java.nio.channels.SelectableChannel

/** [[AbstractNioChannel]] base class for [[Channel]]s that operate on messages.
 *
 *  @param executor
 *    the [[ChannelsActor]] which will be used.
 *  @param supportingDisconnect
 *    true if and only if the channel has the [[disconnect]] operation that allows a user to disconnect and then call
 *    [[Channel.connect()]] again, such as UDP/IP.
 *  @param defaultReadHandleFactory
 *    the [[ReadHandleFactory]] that is used by default.
 *  @param defaultWriteHandleFactory
 *    the [[WriteHandleFactory]] that is used by default.
 *  @param ch
 *    the underlying [[SelectableChannel]] on which it operates
 *  @param readInterestOp
 *    the ops to set to receive data from the [[SelectableChannel]]
 *  @tparam L
 *    type of local address
 *  @tparam R
 *    type of remote address
 */
abstract class AbstractNioMessageChannel[L <: SocketAddress, R <: SocketAddress](
    supportingDisconnect: Boolean,
    defaultReadHandleFactory: ReadHandleFactory,
    defaultWriteHandleFactory: WriteHandleFactory,
    ch: SelectableChannel,
    readInterestOp: Int
) extends AbstractNioChannel[L, R](
      supportingDisconnect,
      defaultReadHandleFactory,
      defaultWriteHandleFactory,
      ch,
      readInterestOp
    ) {

    override final protected[core] def doReadNow(readSink: ReadSink): Boolean = {
        val localRead = doReadMessages(readSink)
        localRead < 0
    }

    /** Read messages into the given array and return the amount which was read.
     *  @param readSink
     *    the [[ReadSink]] that should be called with messages that are read from the transport to propagate these.
     *  @throws Exception
     *  @return
     *    int
     */
    @throws[Exception]
    protected def doReadMessages(readSink: ReadSink): Int

}
