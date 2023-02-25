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
import io.otavia.core.channel.estimator.{ReadBufferAllocator, ReadHandleFactory, WriteHandleFactory}
import io.otavia.core.channel.internal.ChannelOutboundBuffer
import io.otavia.core.channel.{AbstractChannel, ChannelPipeline, ChannelShutdownDirection, ServerChannel}

import java.io.IOException
import java.net.PortUnreachableException
import scala.beans.BeanProperty

/** Sink that will be used by [[AbstractChannel.doReadNow]] implementations to perform the actual read from the
 *  underlying transport (for example a socket).
 */
private[channel] trait ReadSink {
    this: AbstractChannel[?, ?] =>

    private lazy val readHandle: ReadHandleFactory.ReadHandle = newReadHandle // TODO:

    readSomething = false
    continueReading = false

    private var readBufferAllocator: ReadBufferAllocator = _

    def setReadBufferAllocator(allocator: ReadBufferAllocator): Unit = readBufferAllocator = allocator

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
    def allocateBuffer: Buffer = readBufferAllocator.allocate(directAllocator, readHandle.estimatedBufferCapacity)

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
                    closed = doReadNow(this)
                } catch {
                    case cause: Throwable =>
                        if (completeFailure(cause)) shutdownReadSide() else closeTransport()
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

/** Sink that will be used by [[AbstractChannel.doWriteNow]] implementations. */
private[channel] trait WriteSink {
    this: AbstractChannel[?, ?] =>

    private lazy val writeHandle: WriteHandleFactory.WriteHandle = newWriteHandle // TODO:

    private var writtenBytes         = 0
    private var writtenMessages: Int = 0

    private val predicate: AnyRef => Boolean = o =>
        o match
            case buffer: Buffer =>
                val readable = buffer.readableBytes()
                buffer.skipReadableBytes(math.min(readable, writtenBytes))
                if (buffer.readableBytes() == 0) {
                    writtenBytes -= readable
                    writtenMessages += 1
                    true
                } else ???
            case _ => false

    def writeLoop(outboundBuffer: ChannelOutboundBuffer): Unit = try {
        try {} catch {
            case t: Throwable => ???
        } finally {
            try {
                writeLoopComplete(outboundBuffer.isEmpty)
            } catch {
                case cause: Throwable => ???
            }
        }
    } finally {}

    /** Update the [[Buffer.readerOffset]] of each buffer and return the number of completely written [[Buffer]]s.
     *
     *  @param writtenBytes
     *    the number of written bytes.
     *  @return
     *    the number of completely written buffers.
     */
    def updateBufferReaderOffsets(writtenBytes: Long): Int = ???

    /** Return the estimated maximum number of bytes that can be written with one gathering write operation.
     *
     *  @return
     *    number of bytes.
     */
    def estimatedMaxBytesPerGatheringWrite(): Long = ???

    /** The number of flushed messages that are ready to be written. The messages can be accessed by either calling
     *  {@link # currentFlushedMessage ( )} or {@link # forEachFlushedMessage ( Predicate )}.
     *
     *  @return
     *    the number of messages.
     */
    def numFlushedMessages(): Int = ???

    /** Return the current message that should be written.
     *
     *  @return
     *    the first flushed message.
     */
    def currentFlushedMessage(): AnyRef = ???

    /** Call {@link Predicate# test ( Object )} for each message that is flushed until {@link Predicate# test ( Object
     *  )} returns {@code false} or there are no more flushed messages.
     *
     *  @param processor
     *    the {@link Function} to use.
     *  @throws IllegalStateException
     *    if called after {@link # complete ( long, long, int, boolean)} or {@link # complete ( long, Throwable,
     *    boolean)} was called.
     */
    def forEachFlushedMessage(processor: AnyRef => Boolean): Unit = ???

    /** Notify of the last write operation and its result.
     *
     *  @param attemptedBytesWrite
     *    The number of bytes the write operation did attempt to write.
     *  @param actualBytesWrite
     *    The number of bytes from the previous write operation. This may be negative if a write error occurs.
     *  @param messagesWritten
     *    The number of written messages, this can never be greater than {@link # numFlushedMessages ( )}.
     *  @param mightContinueWriting
     *    {@code true} if the write loop might continue writing messages, {@code false} otherwise
     *  @throws IllegalStateException
     *    if called after {@link # complete ( long, long, int, boolean)} or {@link # complete ( long, Throwable,
     *    boolean)} was called.
     */
    def complete(
        attemptedBytesWrite: Long,
        actualBytesWrite: Long,
        messagesWritten: Int,
        mightContinueWriting: Boolean
    ): Unit = ???

}
