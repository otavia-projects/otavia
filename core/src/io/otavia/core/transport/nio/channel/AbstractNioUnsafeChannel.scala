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

import io.otavia.core.channel.message.ReadPlan
import io.otavia.core.channel.{AbstractUnsafeChannel, Channel, ChannelException}
import io.otavia.core.message.ReactorEvent

import java.io.IOException
import java.nio.channels.{CancelledKeyException, SelectableChannel, SelectionKey, Selector}
import java.nio.file.attribute.FileAttribute
import java.nio.file.{OpenOption, Path}
import scala.language.unsafeNulls

abstract class AbstractNioUnsafeChannel(channel: Channel, val ch: SelectableChannel, val readInterestOp: Int)
    extends AbstractUnsafeChannel(channel)
    with NioUnsafeChannel {

    private var _selectionKey: SelectionKey = _

    private var readPlan: ReadPlan = _

    protected def javaChannel: SelectableChannel = ch

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
        if (!key.isValid) executorAddress.inform(ReactorEvent.ChannelClose(channel))
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

                executorAddress.inform(ReactorEvent.ChannelReadiness(channel, SelectionKey.OP_CONNECT))
            }
            // Process OP_WRITE first as we may be able to write some queued buffers and so free memory.
            if ((readOps & SelectionKey.OP_WRITE) != 0) {
                // Notice to call forceFlush which will also take care of clear the OP_WRITE once there is nothing left to
                // write
                executorAddress.inform(ReactorEvent.ChannelReadiness(channel, SelectionKey.OP_WRITE))
            }
            // Also check for readOps of 0 to workaround possible JDK bug which may otherwise lead
            // to a spin loop
            if ((readOps & (SelectionKey.OP_READ | SelectionKey.OP_ACCEPT)) != 0 || readOps == 0) {
                readNow()
                // val event = ReactorEvent.ChannelReadiness(this, SelectionKey.OP_READ | SelectionKey.OP_ACCEPT)
                // executorAddress.inform(event)
            }
        } catch {
            case ignored: CancelledKeyException => executorAddress.inform(ReactorEvent.ChannelClose(channel))
        }
    }

    override def closeProcessor(): Unit = executorAddress.inform(ReactorEvent.ChannelClose(channel))

    override def unsafeRead(readPlan: ReadPlan): Unit = if (_selectionKey.isValid) {
        this.readPlan = readPlan
        val ops = _selectionKey.interestOps()
        if ((ops & readInterestOp) == 0)
            _selectionKey.interestOps(ops | readInterestOp)
    }

    override def unsafeOpen(path: Path, options: Seq[OpenOption], attrs: Seq[FileAttribute[?]]): Unit = {
        channel.executorAddress.inform(
          ReactorEvent.OpenReply(channel, cause = Some(new UnsupportedOperationException()))
        )
    }

}
