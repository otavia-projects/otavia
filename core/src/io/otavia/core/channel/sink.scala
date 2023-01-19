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

import io.netty5.buffer.Buffer

/** Sink that will be used by [[AbstractChannel.doReadNow]] implementations to perform the actual read from the
 *  underlying transport (for example a socket).
 */
private[channel] trait ReadSink {
    this: AbstractChannel[?, ?] =>

    //    final val readHandle: ReadHandleFactory.ReadHandle = AbstractChannel.this.readHandle

    private var readSomething   = false
    private var continueReading = false

//     this.readHandle

    /** Process the read message and fire it through the [[ChannelPipeline]]
     *
     *  @param attemptedBytesRead
     *    The number of bytes the read operation did attempt to read.
     *  @param actualBytesRead
     *    The number of bytes the read operation actually read.
     *  @param message
     *    the read message or null if none was read.
     */
    def processRead(attemptedBytesRead: Int, actualBytesRead: Int, message: AnyRef): Unit

    /** Allocate a [[Buffer]] with a capacity that is probably large enough to read all inbound data and small enough
     *  not to waste space.
     *
     *  @return
     *    the allocated [[Buffer]].
     */
    def allocateBuffer: Buffer

    def readLoop(): Unit = {
        continueReading = false
        

        doReadNow(this)
    }

}

/** Sink that will be used by [[AbstractChannel.doWriteNow]] implementations. */
private[channel] trait WriteSink {
    this: AbstractChannel[?, ?] =>

    def writeLoop(outboundBuffer: ChannelOutboundBuffer): Unit

    /** Update the [[Buffer.readerOffset]] of each buffer and return the number of completely written [[Buffer]]s.
     *
     *  @param writtenBytes
     *    the number of written bytes.
     *  @return
     *    the number of completely written buffers.
     */
    def updateBufferReaderOffsets(writtenBytes: Long): Int

    /** Return the estimated maximum number of bytes that can be written with one gathering write operation.
     *
     *  @return
     *    number of bytes.
     */
    def estimatedMaxBytesPerGatheringWrite(): Long

    /** The number of flushed messages that are ready to be written. The messages can be accessed by either calling
     *  {@link # currentFlushedMessage ( )} or {@link # forEachFlushedMessage ( Predicate )}.
     *
     *  @return
     *    the number of messages.
     */
    def numFlushedMessages(): Int

    /** Return the current message that should be written.
     *
     *  @return
     *    the first flushed message.
     */
    def currentFlushedMessage(): AnyRef

    /** Call {@link Predicate# test ( Object )} for each message that is flushed until {@link Predicate# test ( Object
     *  )} returns {@code false} or there are no more flushed messages.
     *
     *  @param processor
     *    the {@link Function} to use.
     *  @throws IllegalStateException
     *    if called after {@link # complete ( long, long, int, boolean)} or {@link # complete ( long, Throwable,
     *    boolean)} was called.
     */
    def forEachFlushedMessage(processor: AnyRef => Boolean): Unit

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
    ): Unit

}
