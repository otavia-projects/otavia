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

package io.otavia.core.channel

import io.otavia.core.actor.ChannelsActor
import io.otavia.core.channel.message.ReadPlan
import io.otavia.core.message.ReactorEvent
import io.otavia.core.reactor.Reactor

import java.io.IOException
import java.net.PortUnreachableException
import java.nio.file.attribute.FileAttribute
import java.nio.file.{OpenOption, Path}
import scala.language.unsafeNulls

/** The [[Channel]] in [[Reactor]] */
abstract class AbstractUnsafeChannel(val channel: Channel) extends UnsafeChannel {

    private var inputClosedSeenErrorOnRead: Boolean = false
    private var isAllowHalfClosure: Boolean         = true

    // read sink
    private var readSomething      = false
    private var continueReading    = false
    private var readPlan: ReadPlan = _

    // write sink

    def setReadPlan(plan: ReadPlan): Unit = readPlan = plan

    def executor: ChannelsActor[?] = channel.executor

    /** Reading from the underlying transport now until there is nothing more to read or the [[ReadPlan]] is telling us
     *  to stop.
     */
    protected final def readNow(): Unit = {
        if (isShutdown(ChannelShutdownDirection.Inbound) && (inputClosedSeenErrorOnRead || !isAllowHalfClosure)) {
            // There is nothing to read anymore.
            clearScheduledRead() // TODO: clearScheduledRead()
        } else this.readLoop()
    }

    protected def clearScheduledRead(): Unit = readPlan = null

    /** Process the read message and fire it through the [[ChannelPipeline]]
     *
     *  @param attemptedBytesRead
     *    The number of bytes the read operation did attempt to read.
     *  @param actualBytesRead
     *    The number of bytes the read operation actually read.
     *  @param message
     *    the read message or null if none was read.
     */
    def processRead(attemptedBytesRead: Int, actualBytesRead: Int, message: ReactorEvent): Unit = {
        message match
            case null =>
                readPlan.lastRead(attemptedBytesRead, actualBytesRead, 0)
                continueReading = false
            case event: ReactorEvent.AcceptedEvent =>
                readSomething = true
                continueReading = readPlan.lastRead(attemptedBytesRead, actualBytesRead, 1)
                executor.address.inform(event)

        // TODO: impl
    }

    /** Allocate a [[Buffer]] with a capacity that is probably large enough to read all inbound data and small enough
     *  not to waste space.
     */
    def allocateBuffer() = channel.pipeline.channelInboundBuffer.ensureWritable(readPlan.estimatedNextSize)

    private def complete(): Unit = try {
        readSomething0()
    } finally {
        continueReading = false
        readLoopComplete()
    }

    private def completeFailure(cause: Throwable): Boolean = try {
        readSomething0()
//        pipeline.fireChannelExceptionCaught(cause)
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
        // Complete the read handle, allowing channelReadComplete to schedule more reads.
        readPlan.readComplete()
        // Check if something was read as in this case we wall need to call the *ReadComplete methods.
        if (readSomething) {
            readSomething = false
            // pipeline.fireChannelReadComplete()
        }
    }

    def readLoop(): Unit = {
        continueReading = false
        var closed: Boolean = false
        try {
            while {
                try {
                    closed = doReadNow()
                    println(s"loop result closed: ${closed}")
                } catch {
                    case cause: Throwable =>
                        cause.printStackTrace()
                        if (completeFailure(cause)) shutdownReadSide() else close()
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

        if (closed) shutdownReadSide() else readIfIsAutoRead() // TODO: do not call this in reactor thread

    }

    /** Try to read a message from the transport and dispatch it via [[AbstractUnsafeChannel.processRead]] . This method
     *  is called in a loop until there is nothing more messages to read or the channel was shutdown / closed.
     *  <strong>This method should never be called directly by sub-classes, use [[readNow]] instead.</strong>
     *
     *  @return
     *    true if the channel should be shutdown / closed.
     */
    protected def doReadNow(): Boolean

    protected def shutdownReadSide(): Unit = {
        // TODO: Send event
    }

    protected def readIfIsAutoRead(): Unit = {
        // TODO:
    }

    protected def close(): Unit = {
        // TODO: close and end event
    }

    def isShutdown(channelShutdownDirection: ChannelShutdownDirection): Boolean = ???

    /** Called once the read loop completed for this Channel. Sub-classes might override this method but should also
     *  call super.
     */
    protected def readLoopComplete(): Unit = {
        // NOOP
    }

}
