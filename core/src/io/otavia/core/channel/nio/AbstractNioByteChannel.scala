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

import io.netty5.buffer.Buffer
import io.otavia.core.actor.ChannelsActor
import io.otavia.core.channel.*

import java.net.SocketAddress
import java.nio.channels.{SelectableChannel, SelectionKey}

/** [[AbstractNioChannel]] base class for [[Channel]]s that operate on bytes.
 *
 *  @param executor
 *    the [[ChannelsActor]] which will be used.
 *  @param defaultWriteHandleFactory
 *    the [[WriteHandleFactory]] that is used by default.
 *  @param ch
 *    the underlying [[SelectableChannel]] on which it operates
 *  @tparam L
 *    type of local address
 *  @tparam R
 *    type of remote address
 */
abstract class AbstractNioByteChannel[L <: SocketAddress, R <: SocketAddress](
    defaultWriteHandleFactory: WriteHandleFactory,
    ch: SelectableChannel
) extends AbstractNioChannel[L, R](
      false,
      new AdaptiveReadHandleFactory(),
      defaultWriteHandleFactory,
      ch,
      SelectionKey.OP_READ
    ) {

    override final protected[channel] def doReadNow(readSink: ReadSink): Boolean = ???

    override protected def doWriteNow(writeSink: WriteSink): Unit = ???

    /** Write a [[FileRegion]]
     *
     *  @param region
     *    the [[FileRegion]] from which the bytes should be written
     *  @return
     *    amount the amount of written bytes
     */
    @throws[Exception]
    protected def doWriteFileRegion(region: FileRegion): Long

    /** Read bytes into the given [[Buffer]] and return the amount. */
    @throws[Exception]
    protected def doReadBytes(buf: Buffer): Int

    /** Write bytes form the given [[Buffer]] to the underlying [[java.nio.channels.Channel]].
     *
     *  @param buf
     *    the [[Buffer]] from which the bytes should be written
     *  @return
     *    amount the amount of written bytes
     */
    @throws[Exception]
    protected def doWriteBytes(buf: Buffer): Int

}
