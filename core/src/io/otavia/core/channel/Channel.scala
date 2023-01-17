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

import io.netty5.buffer.{Buffer, BufferAllocator}
import io.netty5.util.AttributeMap
import io.otavia.core.actor.ChannelsActor
import io.otavia.core.address.ChannelsActorAddress
import io.otavia.core.reactor.{DeregisterReplyEvent, Reactor, RegisterReplyEvent}
import io.otavia.core.timer.Timer

import java.net.SocketAddress

trait Channel extends ChannelOutboundInvoker, AttributeMap, EventHandle {

    /** Unique id of this channel */
    val id: Int

    /** Executor of this channel instance, the channel inbound and outbound event must execute in the binding executor
     */
    def executor: ChannelsActor[?]

    /** Address of executor [[ChannelsActor]] of this [[Channel]] belong to. */
    final def executorAddress: ChannelsActorAddress[_] = executor.self

    /** [[Reactor]] of this actor system. */
    def reactor: Reactor = executor.reactor

    /** [[Timer]] of this actor system. */
    def timer: Timer = executor.system.timer

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

    /** Return the assigned [[BufferAllocator]] which will be used to allocate [[Buffer]]s. */
    def bufferAllocator: BufferAllocator

    final override def read(readBufferAllocator: ReadBufferAllocator): Channel = {
        pipeline.read(readBufferAllocator)
        this
    }

    override def read(): Channel = {
        pipeline.read()
        this
    }

    final override def bind(local: SocketAddress): Unit = pipeline.bind(local)

    final override def connect(remote: SocketAddress): Unit = pipeline.connect(remote)

    final override def connect(remote: SocketAddress, local: Option[SocketAddress]): Unit =
        pipeline.connect(remote, local)

    final override def disconnect(): Unit = pipeline.disconnect()

    final override def close(): Unit = pipeline.close()

    final override def shutdown(direction: ChannelShutdownDirection): Unit = pipeline.shutdown(direction)

    override def register(): Unit = pipeline.register()

    final override def deregister(): Unit = pipeline.deregister()

    final override def write(msg: AnyRef): Unit = pipeline.write(msg)

    final override def write(msg: AnyRef, msgId: Long): Unit = pipeline.write(msg, msgId)

    final override def writeAndFlush(msg: AnyRef): Unit = pipeline.writeAndFlush(msg)

    final override def writeAndFlush(msg: AnyRef, msgId: Long): Unit = pipeline.writeAndFlush(msg, msgId)

    final override def flush(): Channel = {
        pipeline.flush()
        this
    }

    final override def sendOutboundEvent(event: AnyRef): Unit = pipeline.sendOutboundEvent(event)

    final def assertExecutor(): Unit =
        assert(executor.inExecutor(), "method must be called in ChannelsActor which this channel registered!")

    /** generate a unique id for the channel message
     *  @return
     *    id
     */
    def generateMessageId: Long

    private[core] def onInboundMessage(msg: AnyRef): Unit

}
