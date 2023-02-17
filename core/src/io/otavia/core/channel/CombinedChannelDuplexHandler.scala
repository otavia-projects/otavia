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

import io.otavia.core.channel.CombinedChannelDuplexHandler.*
import io.otavia.core.channel.estimator.ReadBufferAllocator
import io.otavia.core.channel.internal.{ChannelHandlerMask, DelegatingChannelHandlerContext}

/** Combines the inbound handling of one [[ChannelHandler]] with the outbound handling of another [[ChannelHandler]]. */
class CombinedChannelDuplexHandler[I <: ChannelHandler, O <: ChannelHandler] extends ChannelHandlerAdapter {

    private var inboundCtx: CombinedChannelDuplexHandler.CombinedChannelHandlerContext  = _
    private var outboundCtx: CombinedChannelDuplexHandler.CombinedChannelHandlerContext = _

    private var inited: Boolean = false
    private var handlerAdded    = false

    private var inbound: I  = _
    private var outbound: O = _

    /** Creates a new instance that combines the specified two handlers into one.
     *
     *  @param inboundHandler
     *    inbound handler
     *  @param outboundHandler
     *    outbound handler
     *  @throws IllegalArgumentException
     *    if the specified handlers cannot be combined into one due to a conflict in the type hierarchy
     */
    @throws[IllegalArgumentException]
    def this(inboundHandler: I, outboundHandler: O) = {
        this()
        init(inboundHandler, outboundHandler)
    }

    /** Initialized this handler with the specified handlers.
     *
     *  @param inboundHandler
     *    inbound handler
     *  @param outboundHandler
     *    outbound handler
     *  @throws IllegalStateException
     *    if this handler was not constructed via the default constructor or if this handler does not implement all
     *    required handler interfaces
     *  @throws IllegalArgumentException
     *    if the specified handlers cannot be combined into one due to a conflict in the type hierarchy
     */
    @throws[IllegalStateException | IllegalArgumentException]
    protected final def init(inboundHandler: I, outboundHandler: O): Unit = {
        if (inited) throw new IllegalStateException(s"Handler CombinedChannelDuplexHandler[${this}] has been inited!")

        if (ChannelHandlerMask.isOutbound(inboundHandler.getClass))
            throw new IllegalArgumentException("inboundHandler must not implement any outbound method to get combined.")

        if (ChannelHandlerMask.isInbound(outboundHandler.getClass))
            throw new IllegalArgumentException("outbound must not implement any inbound method to get combined.")

        this.inbound = inboundHandler
        this.outbound = outboundHandler
        inited = true
    }

    def inboundHandler: I = this.inbound

    def outboundHandler: O = this.outbound

    private def checkAdded(): Unit =
        if (!handlerAdded) throw new IllegalStateException("handler not added to pipeline yet")

    /** Removes the inbound [[ChannelHandler]] that was combined in this [[CombinedChannelDuplexHandler]]. */
    final def removeInboundHandler(): Unit = {
        checkAdded()
        inboundCtx.remove()
    }

    /** Removes the outbound [[ChannelHandler]] that was combined in this [[CombinedChannelDuplexHandler]]. */
    final def removeOutboundHandler(): Unit = {
        checkAdded()
        outboundCtx.remove()
    }

    override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
        if (!inited)
            throw new IllegalStateException(
              "init() must be invoked before being added to a " + classOf[ChannelPipeline].getSimpleName +
                  " if " + classOf[CombinedChannelDuplexHandler[?, ?]].getSimpleName +
                  " was constructed with the default constructor."
            )

        this.outboundCtx = new CombinedChannelHandlerContext(ctx, outbound)
        this.inboundCtx = new CombinedChannelHandlerContext(ctx, inbound)

        // The inboundCtx and outboundCtx were created and set now it's safe to call removeInboundHandler() and
        // removeOutboundHandler().
        handlerAdded = true

        try {
            inbound.handlerAdded(inboundCtx)
        } finally {
            outbound.handlerAdded(outboundCtx)
        }

    }

    override def handlerRemoved(ctx: ChannelHandlerContext): Unit = try {
        inboundCtx.remove()
    } finally {
        outboundCtx.remove()
    }

    override def channelRegistered(ctx: ChannelHandlerContext): Unit = if (!inboundCtx.removed)
        inbound.channelRegistered(inboundCtx)
    else
        inboundCtx.fireChannelRegistered()

    override def channelUnregistered(ctx: ChannelHandlerContext): Unit = if (!inboundCtx.removed)
        inbound.channelUnregistered(inboundCtx)
    else
        inboundCtx.fireChannelUnregistered()

    override def channelActive(ctx: ChannelHandlerContext): Unit = if (!inboundCtx.removed)
        inbound.channelActive(inboundCtx)
    else inboundCtx.fireChannelActive()

    override def channelInactive(ctx: ChannelHandlerContext): Unit = if (!inboundCtx.removed)
        inbound.channelInactive(inboundCtx)
    else inboundCtx.fireChannelInactive()

    override def channelShutdown(ctx: ChannelHandlerContext, direction: ChannelShutdownDirection): Unit =
        if (!inboundCtx.removed) inbound.channelShutdown(inboundCtx, direction)
        else
            inboundCtx.fireChannelShutdown(direction)

    override def channelExceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = if (!inboundCtx.removed)
        inbound.channelExceptionCaught(inboundCtx, cause)
    else
        inboundCtx.fireChannelExceptionCaught(cause)

    override def channelInboundEvent(ctx: ChannelHandlerContext, evt: AnyRef): Unit = if (!inboundCtx.removed)
        inbound.channelInboundEvent(inboundCtx, evt)
    else
        inboundCtx.fireChannelInboundEvent(evt)

    override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef): Unit = if (!inboundCtx.removed)
        inbound.channelRead(inboundCtx, msg)
    else
        inboundCtx.fireChannelRead(msg)

    override def channelReadComplete(ctx: ChannelHandlerContext): Unit = if (!inboundCtx.removed)
        inbound.channelReadComplete(inboundCtx)
    else
        inboundCtx.fireChannelReadComplete()

    override def channelWritabilityChanged(ctx: ChannelHandlerContext): Unit = if (!inboundCtx.removed)
        inbound.channelWritabilityChanged(inboundCtx)
    else
        inboundCtx.fireChannelWritabilityChanged()

    override def channelTimeoutEvent(ctx: ChannelHandlerContext, id: Long): Unit = if (!inboundCtx.removed)
        inbound.channelTimeoutEvent(inboundCtx, id)
    else
        inboundCtx.fireChannelTimeoutEvent(id)

    override def bind(ctx: ChannelHandlerContext): Unit = if (!outboundCtx.removed)
        outbound.bind(outboundCtx)
    else outboundCtx.bind()

    override def connect(ctx: ChannelHandlerContext): Unit = if (!outboundCtx.removed)
        outbound.connect(outboundCtx)
    else
        outboundCtx.connect()

    override def disconnect(ctx: ChannelHandlerContext): Unit = if (!outboundCtx.removed)
        outbound.disconnect(outboundCtx)
    else outboundCtx.disconnect()

    override def close(ctx: ChannelHandlerContext): Unit = if (!outboundCtx.removed)
        outbound.close(outboundCtx)
    else outboundCtx.close()

    override def shutdown(ctx: ChannelHandlerContext, direction: ChannelShutdownDirection): Unit =
        if (!outboundCtx.removed) outbound.shutdown(outboundCtx, direction)
        else
            outboundCtx.shutdown(direction)

    override def register(ctx: ChannelHandlerContext): Unit = if (!outboundCtx.removed)
        outbound.register(outboundCtx)
    else outboundCtx.register()

    override def deregister(ctx: ChannelHandlerContext): Unit = if (!outboundCtx.removed)
        outbound.deregister(outboundCtx)
    else outboundCtx.deregister()

    override def read(ctx: ChannelHandlerContext, readBufferAllocator: ReadBufferAllocator): Unit =
        if (!outboundCtx.removed) outbound.read(outboundCtx, readBufferAllocator)
        else outboundCtx.read(readBufferAllocator)

    override def write(ctx: ChannelHandlerContext, msg: AnyRef): Unit = if (outboundCtx.removed)
        outbound.write(outboundCtx, msg)
    else outboundCtx.write(msg)

    override def write(ctx: ChannelHandlerContext, msg: AnyRef, msgId: Long): Unit = if (!outboundCtx.removed)
        outbound.write(outboundCtx, msg, msgId)
    else outboundCtx.write(msg, msgId)

    override def flush(ctx: ChannelHandlerContext): Unit = if (!outboundCtx.removed)
        outbound.flush(outboundCtx)
    else outboundCtx.flush()

    override def sendOutboundEvent(ctx: ChannelHandlerContext, event: AnyRef): Unit = if (!outboundCtx.removed)
        outbound.sendOutboundEvent(outboundCtx, event)
    else outboundCtx.sendOutboundEvent(event)

    override def pendingOutboundBytes(ctx: ChannelHandlerContext): Long = if (!outboundCtx.removed)
        outboundCtx.handler.pendingOutboundBytes(outboundCtx)
    else 0

}

object CombinedChannelDuplexHandler {
    final class CombinedChannelHandlerContext(ctx: ChannelHandlerContext, override val handler: ChannelHandler)
        extends DelegatingChannelHandlerContext(ctx) {

        var removed: Boolean = false

        override def isRemoved: Boolean = delegatingCtx.isRemoved || removed

        def remove(): Unit = if (!removed) {
            removed = true
            try {
                handler.handlerRemoved(this)
            } catch {
                case cause: Throwable => this.fireChannelExceptionCaught(cause)
            }
        }

    }

}
