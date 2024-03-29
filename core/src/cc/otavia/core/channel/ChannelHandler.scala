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

import cc.otavia.buffer.pool.AdaptiveBuffer
import cc.otavia.core.channel.message.ReadPlan
import cc.otavia.core.stack.ChannelFuture

import java.net.SocketAddress
import java.nio.file.attribute.FileAttribute
import java.nio.file.{OpenOption, Path}

/** Handles an I/O event or intercepts an I/O operation, and forwards it to its next handler in its [[ChannelPipeline]].
 *  ===The context object===
 *  A [[ChannelHandler]] is provided with a [[ChannelHandlerContext]] object. A [[ChannelHandler]] is supposed to
 *  interact with the [[ChannelPipeline]] it belongs to via a context object. Using the context object, the
 *  [[ChannelHandler]] can pass events upstream or downstream, modify the pipeline dynamically, or store the information
 *  (using [[AttributeKey]]s) which is specific to the handler.
 *  ===State management===
 *  A [[ChannelHandler]] often needs to store some stateful information. The simplest and recommended approach is to use
 *  member variables:
 *  {{{
 *    trait Msg {
 *       // your methods here
 *    }
 *
 *  }}}
 */
trait ChannelHandler {

    /** Gets called after the [[ChannelHandler]] was added to the actual context and it's ready to handle events. */
    @throws[Exception]
    def handlerAdded(ctx: ChannelHandlerContext): Unit = {}

    /** Gets called after the [[ChannelHandler]] was removed from the actual context and it doesn't handle events
     *  anymore.
     */
    @throws[Exception]
    def handlerRemoved(ctx: ChannelHandlerContext): Unit = {}

    /** @return
     *    true if this handler is sharable and thus can be added to more than one [[ChannelPipeline]]. By default, this
     *    method returns false. If this method returns false, you have to create a new handler instance every time you
     *    add it to a pipeline because it has unshared state such as member variables.
     */
    def isSharable: Boolean = false

    /** The [[Channel]] of the [[ChannelHandlerContext]] was registered with its [[cc.otavia.core.actor.ChannelsActor]]
     */
    @Skip
    @throws[Exception]
    def channelRegistered(ctx: ChannelHandlerContext): Unit = ctx.fireChannelRegistered()

    /** The [[Channel]] of the [[ChannelHandlerContext]] was unregistered from its
     *  [[cc.otavia.core.actor.ChannelsActor]]
     */
    @Skip
    @throws[Exception]
    def channelUnregistered(ctx: ChannelHandlerContext): Unit = ctx.fireChannelUnregistered()

    /** The [[Channel]] of the [[ChannelHandlerContext]] is now active */
    @Skip
    @throws[Exception]
    def channelActive(ctx: ChannelHandlerContext): Unit = ctx.fireChannelActive()

    /** The [[Channel]] of the [[ChannelHandlerContext]] was registered is now inactive and reached its end of lifetime.
     */
    @Skip
    @throws[Exception]
    def channelInactive(ctx: ChannelHandlerContext): Unit = ctx.fireChannelInactive()

    /** The [[Channel]] of the [[ChannelHandlerContext]] was shutdown in one direction. This might either be because the
     *  remote peer did cause a shutdown of one direction or the shutdown was requested explicit by us and was executed.
     *
     *  @param ctx
     *    the [[ChannelHandlerContext]] for which we notify about the completed shutdown.
     *  @param direction
     *    the [[ChannelShutdownDirection]] of the completed shutdown.
     *  @throws Exception
     */
    @Skip
    @throws[Exception]
    def channelShutdown(ctx: ChannelHandlerContext, direction: ChannelShutdownDirection): Unit =
        ctx.fireChannelShutdown(direction)

    /** Invoked when the current [[Channel]] has read a message from the peer. */
    @Skip
    @throws[Exception]
    def channelRead(ctx: ChannelHandlerContext, msg: AnyRef): Unit = ctx.fireChannelRead(msg)

    /** Invoked when the current [[Channel]] has read a message from the peer. */
    @Skip
    @throws[Exception]
    def channelRead(ctx: ChannelHandlerContext, msg: AnyRef, msgId: Long): Unit = ctx.fireChannelRead(msg, msgId)

    /** Invoked when the last message read by the current read operation has been consumed by [[channelRead]]. If
     *  [[ChannelOption.AUTO_READ]] is off, no further attempt to read an inbound data from the current [[Channel]] will
     *  be made until [[ChannelOutboundInvoker.read(ReadBufferAllocator)]] is called.
     */
    @Skip
    @throws[Exception]
    def channelReadComplete(ctx: ChannelHandlerContext): Unit = ctx.fireChannelReadComplete()

    /** Gets called if a custom inbound event happened. */
    @Skip
    @throws[Exception]
    def channelInboundEvent(ctx: ChannelHandlerContext, evt: AnyRef): Unit = ctx.fireChannelInboundEvent(evt)

    /** Gets called if a channel timeout event happened.
     *  @param ctx
     *    [[ChannelHandlerContext]] of this handler.
     *  @param id
     *    registered [[cc.otavia.core.timer.TimeoutTrigger]] id of this timeout event.
     *  @throws Exception
     */
    @Skip
    @throws[Exception]
    def channelTimeoutEvent(ctx: ChannelHandlerContext, id: Long): Unit = ctx.fireChannelTimeoutEvent(id)

    /** Gets called once the writable state of a [[Channel]] changed. You can check the state with
     *  [[Channel.writableBytes]] or [[Channel.isWritable]] .
     */
    @Skip
    @throws[Exception]
    def channelWritabilityChanged(ctx: ChannelHandlerContext): Unit = ctx.fireChannelWritabilityChanged()

    /** Gets called if a [[Throwable]] was thrown when handling inbound events. */
    @Skip
    @throws[Exception]
    def channelExceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit =
        ctx.fireChannelExceptionCaught(cause)

    /** Gets called if a [[Throwable]] was thrown when handling inbound events. */
    @Skip
    @throws[Exception]
    def channelExceptionCaught(ctx: ChannelHandlerContext, cause: Throwable, id: Long): Unit =
        ctx.fireChannelExceptionCaught(cause, id)

    /** Called once a bind operation is made.
     *  @param ctx
     *    the [[ChannelHandlerContext]] for which the bind operation is made
     *  @param local
     *    the [[SocketAddress]] to which it should bound
     *  @throws Exception
     */
    @Skip
    def bind(ctx: ChannelHandlerContext, local: SocketAddress, future: ChannelFuture): ChannelFuture =
        ctx.bind(local, future)

    /** Called once a connect operation is made.
     *  @param ctx
     *    the [[ChannelHandlerContext]] for which the bind operation is made
     *  @param remote
     *    the [[SocketAddress]] to which it should connect
     *  @param local
     *    the option [[SocketAddress]] which is used as source on connect
     *  @throws Exception
     */
    @Skip
    def connect(
        ctx: ChannelHandlerContext,
        remote: SocketAddress,
        local: Option[SocketAddress],
        future: ChannelFuture
    ): ChannelFuture = ctx.connect(remote, local, future)

    /** Called once a open operation is made.
     *  @param ctx
     *    the [[ChannelHandlerContext]] for which the bind operation is made
     *  @param path
     *    The path of the file to open or create
     *  @param options
     *    Options specifying how the file is opened
     *  @param attrs
     *    An optional list of file attributes to set atomically when creating the file
     */
    @Skip
    def open(
        ctx: ChannelHandlerContext,
        path: Path,
        options: Seq[OpenOption],
        attrs: Seq[FileAttribute[?]],
        future: ChannelFuture
    ): ChannelFuture = ctx.open(path, options, attrs, future)

    /** Called once a disconnect operation is made.
     *  @param ctx
     *    the [[ChannelHandlerContext]] for which the bind operation is made
     *  @throws Exception
     */
    @Skip
    def disconnect(ctx: ChannelHandlerContext, future: ChannelFuture): ChannelFuture = ctx.disconnect(future)

    /** Called once a close operation is made.
     *  @param ctx
     *    the [[ChannelHandlerContext]] for which the bind operation is made
     *  @throws Exception
     */
    @Skip
    def close(ctx: ChannelHandlerContext, future: ChannelFuture): ChannelFuture = ctx.close(future)

    /** Called once a shutdown operation was requested and should be executed.
     *  @param ctx
     *    the [[ChannelHandlerContext]] for which the bind operation is made
     *  @param direction
     *    the [[ChannelShutdownDirection]] that is used.
     *  @throws Exception
     */
    @Skip
    def shutdown(
        ctx: ChannelHandlerContext,
        direction: ChannelShutdownDirection,
        future: ChannelFuture
    ): ChannelFuture =
        ctx.shutdown(direction, future)

    /** Called once a register operation is made to register for IO on the [[cc.otavia.core.actor.ChannelsActor]]. */
    @Skip
    def register(ctx: ChannelHandlerContext, future: ChannelFuture): ChannelFuture = ctx.register(future)

    /** Called once a deregister operation is made from the current registered [[cc.otavia.core.actor.ChannelsActor]] */
    @Skip
    def deregister(ctx: ChannelHandlerContext, future: ChannelFuture): ChannelFuture = ctx.deregister(future)

    /** Called once a read operation is made from the current registered [[cc.otavia.core.actor.ChannelsActor]]. If the
     *  [[ChannelHandler]] implementation queues the read and another read happens it is free to drop the first
     *  [[ReadPlan]] and just use the last one.
     *
     *  @param ctx
     *    the [[ChannelHandlerContext]] for which the bind operation is made
     *  @param readPlan
     *    The [[ReadPlan]] that should be used to allocate a [[Buffer]] if needed (for reading the data).
     */
    @Skip
    def read(ctx: ChannelHandlerContext, readPlan: ReadPlan): Unit = ctx.read(readPlan)

    /** Called once a write operation is made. The write operation will write the messages through the
     *  [[ChannelPipeline]]. Those are then ready to be flushed to the actual [[Channel]] once [[Channel.flush()]] is
     *  called.
     *
     *  @param ctx
     *    the [[ChannelHandlerContext]] for which the bind operation is made
     *  @param msg
     *    the message to write
     */
    @Skip
    def write(ctx: ChannelHandlerContext, msg: AnyRef): Unit = ctx.write(msg)

    /** Called once a write operation is made. The write operation will write the messages through the
     *  [[ChannelPipeline]]. Those are then ready to be flushed to the actual [[Channel]] once [[Channel.flush()]] is
     *  called.
     *
     *  @param ctx
     *    the [[ChannelHandlerContext]] for which the bind operation is made
     *  @param msg
     *    the message to write
     *  @param msgId
     *    the id of the message
     */
    @Skip
    def write(ctx: ChannelHandlerContext, msg: AnyRef, msgId: Long): Unit = ctx.write(msg, msgId)

    /** Called once a flush operation is made. The flush operation will try to flush out all previous written messages
     *  that are pending.
     */
    @Skip
    def flush(ctx: ChannelHandlerContext): Unit = ctx.flush()

    /** Called once a custom defined outbound event was sent. This operation will pass the event through the
     *  [[ChannelPipeline]] in the outbound direction.
     *
     *  @param ctx
     *    the [[ChannelHandlerContext]] for which the bind operation is made
     *  @param event
     *    the event.
     */
    @Skip
    def sendOutboundEvent(ctx: ChannelHandlerContext, event: AnyRef): Unit = ctx.sendOutboundEvent(event)

    /** The number of the outbound bytes that are buffered / queued in this [[ChannelHandler]]. This number will affect
     *  the writability of the [[Channel]] together the buffered / queued bytes in the [[Channel]] itself. By default
     *  this methods returns 0. If the [[ChannelHandler]] implementation buffers / queues outbound data this methods
     *  should be implemented to return the correct value.
     *
     *  @param ctx
     *    the [[ChannelHandlerContext]] for which the bind operation is made
     *  @return
     *    the number of buffered / queued bytes.
     */
    def pendingOutboundBytes(ctx: ChannelHandlerContext): Long = 0

    def isBufferHandler: Boolean = false

    /** If the handler has inbound [[AdaptiveBuffer]] */
    def hasInboundAdaptive: Boolean = false

    /** If the handler has outbound [[AdaptiveBuffer]] */
    def hasOutboundAdaptive: Boolean = false

}
