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

package io.otavia.core.channel.nio

import io.otavia.core.actor.ChannelsActor
import io.otavia.core.channel.{AbstractChannel, Channel, ReadHandleFactory, WriteHandleFactory}
import io.otavia.core.reactor.ReactorEvent

import java.net.SocketAddress
import java.nio.channels.{CancelledKeyException, SelectableChannel, SelectionKey, Selector}

/** Abstract base class for Channel implementations which use a Selector based approach.
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
abstract class AbstractNioChannel[L <: SocketAddress, R <: SocketAddress](
    supportingDisconnect: Boolean,
    defaultReadHandleFactory: ReadHandleFactory,
    defaultWriteHandleFactory: WriteHandleFactory,
    val ch: SelectableChannel,
    val readInterestOp: Int
) extends AbstractChannel[L, R](supportingDisconnect, defaultReadHandleFactory, defaultWriteHandleFactory)
    with NioProcessor {

    @volatile private var _selectionKey: SelectionKey | Null = null

    // Start implementation methods in NioProcessor.
    // The methods in NioProcessor is running at reactor thread, which is not same as the thread to running executor.
    // So it needs to be thread safe.
    override def registerSelector(selector: Selector): Unit = {
        var interestOps: Int = 0
        if (_selectionKey != null) {
            interestOps = _selectionKey.interestOps()
            _selectionKey.cancel()
        }
        _selectionKey = ch.register(selector, interestOps, this)
    }

    override def deregisterSelector(): Unit = if (_selectionKey != null) {
        _selectionKey.cancel()
        _selectionKey = null
    }

    override def handle(key: SelectionKey): Unit = {
        if (!key.isValid) executorAddress.inform(ReactorEvent.ChannelClose(this))
        try {
            val readOps = key.readyOps()
            // We first need to call finishConnect() before try to trigger a read(...) or write(...) as otherwise
            // the NIO JDK channel implementation may throw a NotYetConnectedException.
            if ((readOps & SelectionKey.OP_CONNECT) != 0) {
                // remove OP_CONNECT as otherwise Selector.select(..) will always return without blocking
                // See https://github.com/netty/netty/issues/924
                var ops = key.interestOps()
                ops = ops & ~SelectionKey.OP_CONNECT
                key.interestOps(ops)

                executorAddress.inform(ReactorEvent.ChannelReadiness(this, SelectionKey.OP_CONNECT))
            }
            // Process OP_WRITE first as we may be able to write some queued buffers and so free memory.
            if ((readOps & SelectionKey.OP_WRITE) != 0) {
                // Notice to call forceFlush which will also take care of clear the OP_WRITE once there is nothing left to
                // write
                executorAddress.inform(ReactorEvent.ChannelReadiness(this, SelectionKey.OP_WRITE))
            }
            // Also check for readOps of 0 to workaround possible JDK bug which may otherwise lead
            // to a spin loop
            if ((readOps & (SelectionKey.OP_READ | SelectionKey.OP_ACCEPT)) != 0 || readOps == 0) {
                executorAddress.inform(
                  ReactorEvent.ChannelReadiness(this, SelectionKey.OP_READ | SelectionKey.OP_ACCEPT)
                )
            }
        } catch {
            case ignored: CancelledKeyException => executorAddress.inform(ReactorEvent.ChannelClose(this))
        }
    }

    override def closeProcessor(): Unit = executorAddress.inform(ReactorEvent.ChannelClose(this))

    // End implementation of NioProcessor

    // Start implementation of EventHandle
    // Methods in EventHandle is running at thread which execute executor.
    override private[core] def handleChannelCloseEvent(event: ReactorEvent.ChannelClose): Unit =
        closeTransport()

    override private[core] def handleChannelReadinessEvent(event: ReactorEvent.ChannelReadiness): Unit = try {
        val ops = event.readyOps
        if ((ops & SelectionKey.OP_CONNECT) != 0) finishConnect()
        if ((ops & SelectionKey.OP_WRITE) != 0) writeFlushedNow()
        if ((ops & (SelectionKey.OP_READ | SelectionKey.OP_ACCEPT)) != 0) readNow()
    } catch {
        case _: CancelledKeyException => closeTransport()
    }

    // End implementation of EventHandle

    final def nioProcessor: NioProcessor = this

    override def isOpen: Boolean = ch.isOpen

    protected def javaChannel: SelectableChannel = ch

    protected def selectionKey: SelectionKey = _selectionKey.nn

    override protected def doRead(wasReadPendingAlready: Boolean): Unit = if (!wasReadPendingAlready) {
        if (_selectionKey.nn.isValid()) {
            val ops = selectionKey.nn.interestOps()
            if ((ops & readInterestOp) == 0) selectionKey.interestOps(ops | readInterestOp)
        }
    }

    override protected def doClose(): Unit = javaChannel.close()

    override final protected def writeLoopComplete(allWriten: Boolean): Unit = {
        // Check first if the key is still valid as it may be canceled as part of the deregistration
        // from the EventLoop
        // See https://github.com/netty/netty/issues/2104
        if (_selectionKey != null && _selectionKey.nn.isValid()) {
            val ops = _selectionKey.nn.interestOps()

        }
    }

}
