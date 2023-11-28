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

import cc.otavia.buffer.pool.RecyclablePageBuffer
import cc.otavia.core.channel.ChannelShutdownDirection.{Inbound, Outbound}
import cc.otavia.core.channel.message.{ReadPlan, ReadPlanFactory}
import cc.otavia.core.channel.{Channel, ChannelShutdownDirection, FileRegion}
import cc.otavia.core.message.ReactorEvent
import cc.otavia.core.transport.nio.channel.NioUnsafeSocketChannel.NioSocketChannelReadPlan

import java.io.IOException
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.channels.{ClosedChannelException, SelectableChannel, SelectionKey, SocketChannel}
import scala.collection.mutable
import scala.language.unsafeNulls

class NioUnsafeSocketChannel(channel: Channel, ch: SocketChannel, readInterestOp: Int)
    extends AbstractNioUnsafeChannel[SocketChannel](channel, ch, readInterestOp) {

    private var flushQueue: mutable.ArrayDeque[FileRegion | RecyclablePageBuffer] = _
    private var lastReadTime: Long                                                = System.currentTimeMillis()

    setReadPlanFactory((channel: Channel) => new NioSocketChannelReadPlan())

    override def localAddress: SocketAddress = javaChannel.getLocalAddress

    override def unsafeBind(local: SocketAddress): Unit = try {
        javaChannel.bind(local)
        executorAddress.inform(ReactorEvent.BindReply(channel))
    } catch {
        case t: Throwable =>
            executorAddress.inform(ReactorEvent.BindReply(channel, cause = Some(t)))
    }

    override def unsafeConnect(remote: SocketAddress, local: Option[SocketAddress], fastOpen: Boolean): Unit = {
        local match
            case None        =>
            case Some(value) => javaChannel.bind(value)

        try {
            val connected = javaChannel.connect(remote)
            if (!connected) {
                _selectionKey.interestOps(SelectionKey.OP_CONNECT)
            } else {
                executorAddress.inform(ReactorEvent.ConnectReply(channel, true))
            }
        } catch {
            case t: Throwable => executorAddress.inform(ReactorEvent.ConnectReply(channel, cause = Some(t)))
        }
    }

    override protected def finishConnect(): Unit = {
        try {
            javaChannel.finishConnect()
            executorAddress.inform(ReactorEvent.ConnectReply(channel, true))
        } catch {
            case t: Throwable => executorAddress.inform(ReactorEvent.ConnectReply(channel, cause = Some(t)))
        }
    }

    override def unsafeDisconnect(): Unit =
        throw new UnsupportedOperationException("should never execute this method")

    override def unsafeShutdown(direction: ChannelShutdownDirection): Unit = {
        try {
            direction match
                case Inbound =>
                    javaChannel.shutdownInput()
                    shutdownedInbound = true
                case Outbound =>
                    javaChannel.shutdownOutput()
                    shutdownedOutbound = true
            executorAddress.inform(ReactorEvent.ShutdownReply(this.channel, direction))
        } catch {
            case e: Throwable => executorAddress.inform(ReactorEvent.ShutdownReply(this.channel, direction, Some(e)))
        }
    }

    override def unsafeFlush(payload: FileRegion | RecyclablePageBuffer): Unit = {
        if (flushQueue != null && flushQueue.nonEmpty) {
            if (payload != null) flushQueue.addOne(payload)
            val resize   = flushQueue.size > 64
            var continue = true
            while (continue && flushQueue.nonEmpty) {
                flushQueue.removeHead() match
                    case buffer: RecyclablePageBuffer =>
                        continue = unsafeFlushBuffer(buffer)
                    case fileRegion: FileRegion => unsafeFlushFileRegion(fileRegion)
            }
            if (_selectionKey != null && _selectionKey.isValid) { // Channel not be closed.
                val interestOps = _selectionKey.interestOps()
                if (flushQueue.nonEmpty) { // Did not write completely.
                    if ((interestOps & SelectionKey.OP_WRITE) == 0)
                        _selectionKey.interestOps(interestOps | SelectionKey.OP_WRITE)
                } else { // Write completely.
                    if ((interestOps & SelectionKey.OP_WRITE) != 0)
                        _selectionKey.interestOps(interestOps & ~SelectionKey.OP_WRITE)

                    if (resize) flushQueue.clearAndShrink()
                }
            }
        } else {
            payload match
                case buffer: RecyclablePageBuffer => unsafeFlushBuffer(buffer)
                case fileRegion: FileRegion       => unsafeFlushFileRegion(fileRegion)
        }
    }

    private def unsafeFlushBuffer(buffer: RecyclablePageBuffer): Boolean = {
        var closed   = false
        var cursor   = buffer
        var continue = true
        while (cursor != null && continue) {
            val buf = cursor
            cursor = cursor.next
            buf.next = null
            if (!closed) {
                val writable   = buf.readableBytes
                val byteBuffer = buf.byteBuffer
                byteBuffer.limit(buf.writerOffset)
                byteBuffer.position(buf.readerOffset)
                try {
                    val write = ch.write(byteBuffer)
                    if (write != writable) {
                        continue = false
                        buf.skipReadableBytes(write)
                        buf.next = cursor
                        if (flushQueue == null) flushQueue = mutable.ArrayDeque.empty
                        flushQueue.prepend(buf)
                        val interestOps = _selectionKey.interestOps()
                        if ((interestOps & SelectionKey.OP_WRITE) == 0)
                            _selectionKey.interestOps(interestOps | SelectionKey.OP_WRITE)
                    } else buf.close()
                } catch {
                    case e: IOException =>
                        unsafeClose(Some(e))
                        closed = true
                }
            } else buf.close()
        }
        continue
    }

    private def unsafeFlushFileRegion(fileRegion: FileRegion): Unit = {
        fileRegion.transferTo(ch, 0)
        fileRegion.release
    }

    override def isOpen: Boolean = this.synchronized { ch.isOpen }

    override def isActive: Boolean = this.synchronized { ch.isOpen && ch.isConnected }

    override def isShutdown(direction: ChannelShutdownDirection): Boolean = if (!isActive) true
    else {
        this.synchronized {
            direction match
                case Inbound  => shutdownedInbound
                case Outbound => shutdownedOutbound
        }
    }

    override protected def doReadNow(): Boolean =
        if (
          directAllocator.cacheSize == 0 && directAllocator.totalAllocated > 1000 &&
          System.currentTimeMillis() - lastReadTime < 1
        ) { // control single channel read data too fast
            processRead(0, 0, 0)
            false
        } else {
            doReadNow0()
        }

    private def doReadNow0(): Boolean = {
        lastReadTime = System.currentTimeMillis()
        val page      = directAllocator.allocate()
        val attempted = page.writableBytes
        var read: Int = 0
        try {
            read = page.transferFrom(ch, attempted)
            processRead(attempted, read, 1)
        } catch {
            case t: Throwable => unsafeClose(Some(t))
        }

        if (read > 0) {
            executorAddress.inform(ReactorEvent.ReadBuffer(channel, page, recipient = null))
            false
        } else if (read == 0) {
            page.close()
            false
        } else {
            page.close()
            true
        }
    }

}

object NioUnsafeSocketChannel {
    class NioSocketChannelReadPlan extends ReadPlan {

        private var continue: Boolean = true
        private var times: Int        = 0

        override def estimatedNextSize: Int = 0

        override def lastRead(attemptedBytesRead: Int, actualBytesRead: Int, numMessagesRead: Int): Boolean = {
            times += 1
            continue = attemptedBytesRead == actualBytesRead && actualBytesRead > 0 && times < 10
            continue
        }

        override def readComplete(): Unit = {
            times = 0
            continue = true
        }

        override def continueReading: Boolean = continue

    }
}
