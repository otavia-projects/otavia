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
import io.otavia.core.channel.message.ReadPlanFactory
import io.otavia.core.channel.{AbstractNetChannel, Channel, ChannelException}
import io.otavia.core.message.ReactorEvent

import java.io.IOException
import java.net.SocketAddress
import java.nio.channels.{CancelledKeyException, SelectableChannel, SelectionKey, Selector}
import scala.language.unsafeNulls

/** Abstract base class for Channel implementations which use a Selector based approach.
 *
 *  @param supportingDisconnect
 *    true if and only if the channel has the [[disconnect]] operation that allows a user to disconnect and then call
 *    [[Channel.connect()]] again, such as UDP/IP.
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
abstract class AbstractNioChannel[L <: SocketAddress, R <: SocketAddress](
    supportingDisconnect: Boolean,
    defaultWriteHandleFactory: WriteHandleFactory,
    val ch: SelectableChannel,
    val readInterestOp: Int
) extends AbstractNetChannel[L, R](supportingDisconnect, defaultWriteHandleFactory) {

    @volatile private var _selectionKey: SelectionKey | Null = _

    // End implementation of NioProcessor

    // Start implementation of EventHandle
    // Methods in EventHandle is running at thread which execute executor.
    override private[core] def handleChannelCloseEvent(event: ReactorEvent.ChannelClose): Unit =
        closeTransport(newPromise())

    override private[core] def handleChannelReadinessEvent(event: ReactorEvent.ChannelReadiness): Unit = try {
        val ops = event.readyOps
        if ((ops & SelectionKey.OP_CONNECT) != 0) finishConnect()
        if ((ops & SelectionKey.OP_WRITE) != 0) writeFlushedNow()
        if ((ops & (SelectionKey.OP_READ | SelectionKey.OP_ACCEPT)) != 0) readNow()
    } catch {
        case _: CancelledKeyException => closeTransport(newPromise())
    }

    override private[core] def handleChannelAcceptedEvent(event: ReactorEvent.AcceptedEvent): Unit = {
        event.channel.pipeline.fireChannelRead(event.accepted)
    }

    override private[core] def handleChannelReadCompletedEvent(event: ReactorEvent.ReadCompletedEvent): Unit = {
        event.channel.pipeline.fireChannelReadComplete()
    }

    // End implementation of EventHandle

    override def isOpen: Boolean = ch.isOpen

    protected def javaChannel: SelectableChannel = ch

    protected def selectionKey: SelectionKey = _selectionKey

    override protected def doClearScheduledRead(): Unit = {
        val key = _selectionKey
        // Check first if the key is still valid as it may be canceled as part of the deregistration
        // from the Reactor
        if (key == null || !selectionKey.isValid) {} else {
            val ops = selectionKey.interestOps()
            if ((ops & readInterestOp) != 0) selectionKey.interestOps(ops & ~readInterestOp)
        }
    }

    override protected def isWriteFlushedScheduled: Boolean = {
        _selectionKey != null && selectionKey.isValid &&
        ((selectionKey.interestOps() & SelectionKey.OP_WRITE) != 0)
    }

    // Channel.read() or ChannelHandlerContext.read() was called
    override protected def doRead(): Unit = if (_selectionKey.isValid) {
        val ops = selectionKey.interestOps()
        if ((ops & readInterestOp) == 0)
            selectionKey.interestOps(ops | readInterestOp)
    }

    override protected def doClose(): Unit = javaChannel.close()

    override final protected def writeLoopComplete(allWriten: Boolean): Unit = {
        // Check first if the key is still valid as it may be canceled as part of the deregistration
        // from the EventLoop
        // See https://github.com/netty/netty/issues/2104
        if (_selectionKey != null && _selectionKey.isValid) {
            val ops = _selectionKey.interestOps()

        }
    }

}
