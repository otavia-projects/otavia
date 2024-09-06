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

package cc.otavia.core.transport.nio.channel

import cc.otavia.core.channel.*
import cc.otavia.core.channel.message.{AutoReadPlan, ReadPlan}
import cc.otavia.core.message.*

import java.io.IOException
import java.net.{PortUnreachableException, SocketAddress}
import java.nio.channels.*
import java.nio.file.attribute.FileAttribute
import java.nio.file.{OpenOption, Path}
import scala.language.unsafeNulls

abstract class AbstractNioUnsafeChannel[C <: SelectableChannel](
    channel: AbstractChannel,
    val ch: C,
    val readInterestOp: Int
) extends AbstractUnsafeChannel(channel)
    with NioUnsafeChannel {

    protected var _selectionKey: SelectionKey = _

    protected def javaChannel: C = ch

    def localAddress: SocketAddress

    override def registerSelector(selector: Selector): Unit = {
        var interestOps: Int = 0
        if (_selectionKey != null) {
            interestOps = _selectionKey.interestOps()
            _selectionKey.cancel()
        }
        if (ch.isOpen) _selectionKey = ch.register(selector, interestOps, this) else throw new ClosedChannelException()
    }

    override def deregisterSelector(): Unit = if (_selectionKey != null) {
        try {
            _selectionKey.cancel()
            _selectionKey = null
            // channel.invokeLater(() => channel.handleChannelDeregisterReply(false, isOpen, None))
            executorAddress.inform(DeregisterReply(channel, false, isOpen))
        } catch {
            case e: Throwable =>
                executorAddress.inform(DeregisterReply(channel, false, isOpen, Some(e)))
            // channel.invokeLater(() => channel.handleChannelDeregisterReply(false, isOpen, Some(e)))
        }
    } else {
        val cause = Some(new IllegalStateException())
        // channel.invokeLater(() => channel.handleChannelDeregisterReply(false, isOpen, cause))
        executorAddress.inform(DeregisterReply(channel, false, isOpen, cause))
    }

    override def handle(key: SelectionKey): Unit = if (!key.isValid) {
        unsafeClose(None)
    } else {
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

                finishConnect()
            }
            // Process OP_WRITE first as we may be able to write some queued buffers and so free memory.
            if ((readOps & SelectionKey.OP_WRITE) != 0) {
                // Notice to call forceFlush which will also take care of clear the OP_WRITE once there is nothing left to
                // write
                unsafeFlush(null)
            }
            // Also check for readOps of 0 to workaround possible JDK bug which may otherwise lead
            // to a spin loop
            if ((readOps & (SelectionKey.OP_READ | SelectionKey.OP_ACCEPT)) != 0 || readOps == 0) {
                readNow()
            }
        } catch {
            case ignored: CancelledKeyException => unsafeClose(Some(ignored))
        }
    }

    override def closeProcessor(): Unit = executorAddress.inform(ChannelClose(channel))

    override def unsafeRead(readPlan: ReadPlan): Unit = if (_selectionKey.isValid) {
        if (readPlan == AutoReadPlan) {
            this.setAutoRead(true)
            this.currentReadPlan = readPlanFactory.newPlan(channel)
        } else this.currentReadPlan = readPlan
        val ops = _selectionKey.interestOps()
        if ((ops & readInterestOp) == 0)
            _selectionKey.interestOps(ops | readInterestOp)
    }

    override def unsafeOpen(path: Path, options: Seq[OpenOption], attrs: Seq[FileAttribute[?]]): Unit = {
        channel.executorAddress.inform(
          OpenReply(channel, cause = Some(new UnsupportedOperationException()))
        )
    }

    override def unsafeClose(cause: Option[Throwable]): Unit = {
        try {
            if (_selectionKey != null) deregisterSelector()
            ch.close()
            executorAddress.inform(ChannelClose(channel, cause))
        } catch {
            case t: Throwable =>
                executorAddress.inform(ChannelClose(channel, Some(t)))
        }
    }

    protected def finishConnect(): Unit = {}

    private def readNow(): Unit = {
        if (isShutdown(ChannelShutdownDirection.Inbound) && (inputClosedSeenErrorOnRead || !isAllowHalfClosure)) {
            clearScheduledRead()
        } else {
            readLoop()
        }
    }

    override protected def doClearScheduledRead(): Unit = {
        val key = _selectionKey
        if (key == null || !key.isValid) {} else {
            val interestOps = key.interestOps()
            if ((interestOps & readInterestOp) != 0) key.interestOps(interestOps & ~readInterestOp)
        }
    }

    private def readLoop(): Unit = {
        var closed = false
        try {
            while {
                try {
                    closed = doReadNow()
                } catch {
                    case cause: Throwable =>
                        cause match
                            case _: PortUnreachableException =>
                                shutdownReadSide()
                            case _: IOException if !this.isInstanceOf[NioUnsafeServerSocketChannel] =>
                                unsafeClose(Some(cause))
                }
                currentReadPlan.continueReading && !closed && !isShutdown(ChannelShutdownDirection.Inbound)
            } do ()
            completed()
        } finally {
            if (!autoRead) {
                clearScheduledRead()
            }
        }

        if (closed) {
            shutdownReadSide()
        }
    }

    private def completed(): Unit = {
        currentReadPlan.readComplete()
        channel.handleChannelReadCompleted(None)
        // executorAddress.inform(ReadCompletedEvent(channel))
    }

    protected def processRead(attemptedBytesRead: Int, actualBytesRead: Int, numMessagesRead: Int): Unit = {
        currentReadPlan.lastRead(attemptedBytesRead, actualBytesRead, numMessagesRead)
    }

    protected def doReadNow(): Boolean

    protected def shutdownReadSide(): Unit = {
        if (!isShutdown(ChannelShutdownDirection.Inbound)) {
            if (isAllowHalfClosure) {
                unsafeShutdown(ChannelShutdownDirection.Inbound)
            } else {
                unsafeClose(None)
            }
        } else {
            inputClosedSeenErrorOnRead = true
        }
    }

}
