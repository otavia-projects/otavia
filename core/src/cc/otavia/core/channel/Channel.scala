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

package cc.otavia.core.channel

import cc.otavia.buffer.pool.*
import cc.otavia.core.actor.ChannelsActor
import cc.otavia.core.address.ActorAddress
import cc.otavia.core.reactor.Reactor
import cc.otavia.core.system.ActorSystem
import cc.otavia.core.timer.Timer

import java.net.SocketAddress

trait Channel extends ChannelAddress {

    /** Executor of this channel instance, the channel inbound and outbound event must execute in the binding executor
     */
    def executor: ChannelsActor[?]

    /** The [[ActorSystem]] of this [[Channel]] is created. */
    def system: ActorSystem

    /** Set executor of this channel, this method will mount this [[Channel]] to the [[ChannelsActor]].
     *  @param channelsActor
     *    [[ChannelsActor]] to be mounted.
     */
    private[core] def mount(channelsActor: ChannelsActor[?]): Unit

    def mountThreadId: Int

    /** Address of executor [[ChannelsActor]] of this [[Channel]] belong to. */
    final def executorAddress: ActorAddress[?] = executor.self

    /** [[Reactor]] of this actor system. */
    final def reactor: Reactor = executor.reactor

    /** [[Timer]] of this actor system. */
    final def timer: Timer = executor.timer

    /** Return the value of the given [[ChannelOption]]
     *
     *  @param option
     *    the [[ChannelOption]]
     *  @tparam T
     *    the type of the value.
     *  @return
     *    the value for the [[ChannelOption]]
     *  @throws ChannelException
     *    thrown on error.
     *  @throws UnsupportedOperationException
     *    if the [[ChannelOption]] is not supported.
     */
    def getOption[T](option: ChannelOption[T]): T

    /** Sets a configuration property with the specified name and value.
     *
     *  @param option
     *    the [[ChannelOption]]
     *  @param value
     *    the value for the [[ChannelOption]]
     *  @tparam T
     *    the type of the value.
     *  @return
     *    itself.
     *  @throws ChannelException
     *    thrown on error.
     *  @throws UnsupportedOperationException
     *    if the [[ChannelOption]] is not supported.
     */
    def setOption[T](option: ChannelOption[T], value: T): Channel

    /** Returns true if the given ChannelOption is supported by this Channel implementation. If this methods returns
     *  false, calls to [[setOption]] and [[getOption]] with the [[ChannelOption]] will throw an
     *  [[UnsupportedOperationException]].
     *
     *  @param option
     *    the option.
     *  @return
     *    true if supported, false otherwise.
     */
    def isOptionSupported(option: ChannelOption[?]): Boolean

    /** Returns true if the [[Channel]] is mounted to [[ChannelsActor]] */
    def isMounted: Boolean

    /** Returns true if the [[Channel]] is open and may get active later */
    def isOpen: Boolean

    /** Return true if the [[Channel]] is active and so connected. */
    def isActive: Boolean

    /** Return true if registered already.
     *
     *  @return
     *    `true` if registered, `false` otherwise
     */
    def isRegistered: Boolean

    /** Returns true if the [[ChannelShutdownDirection]] of the [[Channel]] was shutdown before. */
    def isShutdown(direction: ChannelShutdownDirection): Boolean

    /** Returns the local address where this channel is bound to. The returned [[SocketAddress]] is supposed to be
     *  down-cast into more concrete type such as [[InetSocketAddress]] to retrieve the detailed information.
     *
     *  @return
     *    the local address of this channel. [[None]] if this channel is not bound
     */
    def localAddress: Option[SocketAddress]

    /** Returns the remote address where this channel is connected to. The returned [[SocketAddress]] is supposed to be
     *  down-cast into more concrete type such as [[InetSocketAddress]] to retrieve the detailed information.
     *
     *  @return
     *    the remote address of this channel. [[None]] if this channel is not connected. If this channel is not
     *    connected but it can receive messages from arbitrary remote addresses (e.g. [[DatagramChannel]], use
     *    [[DatagramPacket#recipient()]] to determine the origination of the received message as this method will return
     *    [[None]].
     */
    def remoteAddress: Option[SocketAddress]

    /** Set the local address where this channel is bound to.
     *  @param address
     *    address where this channel is bound to.
     */
    def setUnresolvedLocalAddress(address: SocketAddress): Unit = {}

    /** Set the remote address where this channel is connected to.
     *  @param address
     *    remote address where this channel is connected to.
     */
    def setUnresolvedRemoteAddress(address: SocketAddress): Unit = {}

    /** @return
     *    true if and only if the executor thread will perform the requested flush operation immediately. Any write
     *    requests made when this method returns false are queued until the executor thread is ready to process the
     *    queued write requests.
     */
    final def isWritable: Boolean = writableBytes > 0

    /** Returns how many bytes can be written before the [[Channel]] becomes 'unwritable'. Once a [[Channel]] becomes
     *  unwritable, all messages will be queued until the executor thread is ready to process the queued write requests.
     *
     *  @return
     *    the number of bytes that can be written before the [[Channel]] becomes unwritable.
     */
    def writableBytes: Long

    /** Return the assigned [[ChannelPipeline]]. */
    def pipeline: ChannelPipeline

    /** Return the assigned [[PooledPageAllocator]] which will be used to allocate [[RecyclablePageBuffer]]s. */
    def directAllocator: PooledPageAllocator

    def heapAllocator: PooledPageAllocator

    final def write(msg: AnyRef): Unit = pipeline.write(msg)

    final def write(msg: AnyRef, msgId: Long): Unit = pipeline.write(msg, msgId)

    final def writeAndFlush(msg: AnyRef): Unit = pipeline.writeAndFlush(msg)

    final def writeAndFlush(msg: AnyRef, msgId: Long): Unit = pipeline.writeAndFlush(msg, msgId)

    final def sendOutboundEvent(event: AnyRef): Unit = pipeline.sendOutboundEvent(event)

    final def assertExecutor(): Unit =
        assert(executor.inExecutor(), "method must be called in ChannelsActor which this channel registered!")

    /** Close the channel before is register */
    private[core] def closeAfterCreate(): Unit

    def unsafeChannel: UnsafeChannel

    // read socket data to this buffer
    def channelInboundAdaptiveBuffer: AdaptiveBuffer = pipeline.channelInboundBuffer

    // write data to socket from this buffer
    def channelOutboundAdaptiveBuffer: AdaptiveBuffer = pipeline.channelOutboundBuffer

    //// Channel Inflight
    def inflightFutureSize: Int

    def pendingFutureSize: Int

    def inflightStackSize: Int

    def pendingStackSize: Int

    /** generate a unique id for the channel message
     *
     *  @return
     *    id
     */
    def generateMessageId: Long

    /** Message from tail handler from pipeline. */
    private[core] def onInboundMessage(msg: AnyRef, exception: Boolean): Unit

    /** Message from tail handler from pipeline. */
    private[core] def onInboundMessage(msg: AnyRef, exception: Boolean, id: Long): Unit

}

object Channel {
    val FALSE_FUNC: AnyRef => Boolean = _ => false
}
