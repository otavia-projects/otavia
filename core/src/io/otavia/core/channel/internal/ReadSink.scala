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

package io.otavia.core.channel.internal

import io.netty5.buffer.Buffer
import io.otavia.core.channel.estimator.ReadHandleFactory
import io.otavia.core.channel.message.ReadPlan
import io.otavia.core.channel.{AbstractNetChannel, ChannelPipeline, ChannelShutdownDirection, ServerChannel}

import java.io.IOException
import java.net.PortUnreachableException

/** Sink that will be used by [[AbstractNetChannel.doReadNow]] implementations to perform the actual read from the
 *  underlying transport (for example a socket).
 */
private[core] trait ReadSink {
    this: AbstractNetChannel[?, ?] =>

    private lazy val readHandle: ReadHandleFactory.ReadHandle = newReadHandle // TODO:

    readSomething = false
    continueReading = false

    private var readBufferAllocator: ReadPlan = _

    def setReadBufferAllocator(allocator: ReadPlan): Unit = readBufferAllocator = allocator

    /** Process the read message and fire it through the [[ChannelPipeline]]
     *
     *  @param attemptedBytesRead
     *    The number of bytes the read operation did attempt to read.
     *  @param actualBytesRead
     *    The number of bytes the read operation actually read.
     *  @param message
     *    the read message or null if none was read.
     */
    def processRead(attemptedBytesRead: Int, actualBytesRead: Int, message: AnyRef): Unit = {
        // TODO: impl
    }

    /** Allocate a [[Buffer]] with a capacity that is probably large enough to read all inbound data and small enough
     *  not to waste space.
     *
     *  @return
     *    the allocated [[Buffer]].
     */
    def allocateBuffer: Buffer =
        ??? // readBufferAllocator.allocate(directAllocator, readHandle.estimatedBufferCapacity)

    private def complete(): Unit = try {
        readSomething0()
    } finally {
        continueReading = false
        readLoopComplete()
    }

    private def completeFailure(cause: Throwable): Boolean = try {
        readSomething0()
        pipeline.fireChannelExceptionCaught(cause)
        if (cause.isInstanceOf[PortUnreachableException]) false
        else {
            // If oom will close the read event, release connection.
            // See https://github.com/netty/netty/issues/10434
            cause.isInstanceOf[IOException] && !this.isInstanceOf[ServerChannel]
        }
    } finally {
        continueReading = false
        readLoopComplete()
    }

    private def readSomething0(): Unit = {
        // Check if something was read as in this case we wall need to call the *ReadComplete methods.
        if (readSomething) {
            readSomething = false
            pipeline.fireChannelReadComplete()
        }
        readHandle.readComplete()
    }

    def readLoop(): Unit = {
        continueReading = false
        var closed: Boolean = false
        try {
            while {
                try {
                    closed = doReadNow(this) // TODO: submit blocking io task to reactor
                } catch {
                    case cause: Throwable =>
                        if (completeFailure(cause)) shutdownReadSide() else closeTransport(newPromise())
                        return
                }
                continueReading && !closed && !isShutdown(ChannelShutdownDirection.Inbound)
            } do ()
            complete()
        } finally {
            // Check if there is a readPending which was not processed yet.
            // This could be for two reasons:
            // * The user called Channel.read() or ChannelHandlerContext.read() in channelRead(...) method
            // * The user called Channel.read() or ChannelHandlerContext.read() in channelReadComplete(...) method
            //
            // See https://github.com/netty/netty/issues/2254
            // TODO: impl
        }
        // TODO: impl
    }

}
