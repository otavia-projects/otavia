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

import java.net.SocketAddress

private[channel] trait ChannelInternal[L <: SocketAddress, R <: SocketAddress] {
    this: AbstractChannel[L, R] =>

    /** Bind the [[Channel]] to the [[SocketAddress]]
     *
     *  @param localAddress
     *    the [[SocketAddress]] to bound to.
     *  @throws Exception
     *    when an error happens.
     */
    @throws[Exception]
    protected def doBind(local: SocketAddress): Unit

    /** Disconnect this [[Channel]] from its remote peer
     *
     *  @throws Exception
     *    thrown on error.
     */
    @throws[Exception]
    protected def doDisconnect(): Unit

    /** Close the [[Channel]]
     *
     *  @throws Exception
     *    thrown on error.
     */
    @throws[Exception]
    protected def doClose(): Unit

    /** Shutdown one direction of the [[Channel]].
     *
     *  @param direction
     *    the direction to shut down.
     *  @throws Exception
     *    thrown on error.
     */
    @throws[Exception]
    protected def doShutdown(direction: ChannelShutdownDirection): Unit

    /** Schedule a read operation.
     *
     *  @param wasReadPendingAlready
     *    true if a read was already pending when [[read]] was called.
     *  @throws Exception
     *    thrown on error.
     */
    @throws[Exception]
    protected def doRead(wasReadPendingAlready: Boolean): Unit

    /** Called in a loop when writes should be performed until this method returns false or there are no more messages
     *  to write. Implementations are responsible for handling partial writes, which for example means that if
     *  [[Buffer]]s are written partial implementations need to ensure the [[Buffer.readerOffset]] is updated
     *  accordingly.
     *
     *  @param writeSink
     *    the [[WriteSink]] that must be completed with the write progress.
     *    [[WriteSink.complete(long, long, int, boolean)]] or [[WriteSink.complete ( long, Throwable, boolean)]] must be
     *    called exactly once before this method returns non-exceptional.
     *  @throws Exception
     *    if an error happened during writing. This will also terminate the write loop.
     */
    @throws[Exception]
    protected def doWriteNow(writeSink: WriteSink): Unit

    /** Connect to remote peer. This method should never be directly called.
     *
     *  @param remote
     *    the address of the remote peer.
     *  @param local
     *    the local address of this channel.
     *  @param initialData
     *    the initial data that is written during connect (if [[ChannelOption.TCP_FASTOPEN_CONNECT]] is supported and
     *    configured). If data is written care must be taken to update the [[Buffer.readerOffset]] .
     *  @return
     *    true if the connect operation was completed, false if [[finishConnect]] will be called later again to try
     *    finish connecting.
     *  @throws Exception
     *    thrown on error.
     */
    @throws[Exception]
    protected def doConnect(remote: SocketAddress, local: Option[SocketAddress], initialData: Buffer): Boolean

    /** Finish a connect request. This method should never be directly called, use [[finishConnect]] instead.
     *
     *  @param requestedRemoteAddress
     *    the remote address of the peer.
     *  @return
     *    true if the connect operations was completed, false if [[finishConnect]] will be called later again to try
     *    finishing the connect operation.
     *  @throws Exception
     *    thrown on error.
     */
    @throws[Exception]
    protected def doFinishConnect(requestedRemoteAddress: R): Boolean

    /** Try to read a message from the transport and dispatch it via [[ReadSink.processRead]] . This method is called in
     *  a loop until there is nothing more messages to read or the channel was shutdown / closed. <strong>This method
     *  should never be called directly by sub-classes, use [[readNow]] instead.</strong>
     *
     *  @param readSink
     *    the [[ReadSink]] that should be called with messages that are read from the transport to propagate these.
     *  @return
     *    true if the channel should be shutdown / closed.
     */
    protected[channel] def doReadNow(readSink: ReadSink): Boolean

    /** Returns the [[SocketAddress]] which is bound locally.
     *
     *  @return
     *    the local address if any, [[None]] otherwise.
     */
    protected def localAddress0: Option[L]

    /** Return the [[SocketAddress]] which the [[Channel]] is connected to.
     *
     *  @return
     *    the remote address if any, [[None]] otherwise.
     */
    protected def remoteAddress0: Option[R]

}
