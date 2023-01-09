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

import java.net.SocketAddress
import java.nio.channels.{SelectableChannel, SelectionKey, Selector}

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
    executor: ChannelsActor[?],
    supportingDisconnect: Boolean,
    defaultReadHandleFactory: ReadHandleFactory,
    defaultWriteHandleFactory: WriteHandleFactory,
    val ch: SelectableChannel,
    val readInterestOp: Int
) extends AbstractChannel[L, R](executor, supportingDisconnect, defaultReadHandleFactory, defaultWriteHandleFactory)
    with NioProcessor {

//    private var readInterestOp: Int                          = 0
    @volatile private var _selectionKey: SelectionKey | Null = null

    // Start implementation of NioProcessor
    override def registerSelector(selector: Selector): Unit = {
        var interestOps: Int = 0
        if (_selectionKey != null) {
            interestOps = _selectionKey.interestOps()
            _selectionKey.cancel()
        }
        _selectionKey = ch.register(selector, interestOps, this)
    }

    // Called by IoHandler in reactor
    override def deregisterSelector(): Unit = if (_selectionKey != null) {
        _selectionKey.cancel()
        _selectionKey = null
    }

    override def handle(key: SelectionKey): Unit = ???

    override def closeProcessor(): Unit = closeTransport()

    // End implementation of NioProcessor

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
