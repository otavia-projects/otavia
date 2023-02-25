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

import io.netty5.buffer.BufferAllocator
import io.otavia.core.actor.ChannelsActor
import io.otavia.core.buffer.AdaptiveBuffer
import io.otavia.core.channel.*
import io.otavia.core.channel.estimator.ReadBufferAllocator

abstract class DelegatingChannelHandlerContext(private val ctx: ChannelHandlerContext) extends ChannelHandlerContext {

    def delegatingCtx: ChannelHandlerContext = ctx

    override def channel: Channel = ctx.channel

    override def executor: ChannelsActor[_] = ctx.executor

    override def name: String = ctx.name

    override def handler: ChannelHandler = ctx.handler

    override def isRemoved: Boolean = ctx.isRemoved

    override def fireChannelRegistered(): this.type = {
        ctx.fireChannelRegistered()
        this
    }

    override def fireChannelUnregistered(): this.type = {
        ctx.fireChannelUnregistered()
        this
    }

    override def fireChannelActive(): this.type = {
        ctx.fireChannelActive()
        this
    }

    override def fireChannelInactive(): this.type = {
        ctx.fireChannelInactive()
        this
    }

    override def fireChannelShutdown(direction: ChannelShutdownDirection): this.type = {
        ctx.fireChannelShutdown(direction)
        this
    }

    override def fireChannelExceptionCaught(cause: Throwable): this.type = {
        ctx.fireChannelExceptionCaught(cause)
        this
    }

    override def fireChannelInboundEvent(evt: AnyRef): this.type = {
        ctx.fireChannelInboundEvent(evt)
        this
    }

    override def fireChannelTimeoutEvent(id: Long): this.type = {
        ctx.fireChannelTimeoutEvent(id)
        this
    }

    override def fireChannelRead(msg: AnyRef): this.type = {
        ctx.fireChannelRead(msg)
        this
    }

    override def fireChannelRead(msg: AnyRef, msgId: Long): this.type = {
        ctx.fireChannelRead(msg, msgId)
        this
    }

    override def fireChannelReadComplete(): this.type = {
        ctx.fireChannelReadComplete()
        this
    }

    override def fireChannelWritabilityChanged(): this.type = {
        ctx.fireChannelWritabilityChanged()
        this
    }

    override def read(readBufferAllocator: ReadBufferAllocator): this.type = {
        ctx.read(readBufferAllocator)
        this
    }

    override def read(): this.type = {
        ctx.read()
        this
    }

    override def flush(): this.type = {
        ctx.flush()
        this
    }

    override def sendOutboundEvent(event: AnyRef): Unit = ctx.sendOutboundEvent(event)

    override def pipeline: ChannelPipeline = ctx.pipeline

    override def bind(): Unit = ctx.bind()

    override def connect(): Unit = ctx.connect()

    override def disconnect(): Unit = ctx.disconnect()

    override def close(): Unit = ctx.close()

    override def shutdown(direction: ChannelShutdownDirection): Unit = ctx.shutdown(direction)

    override def deregister(): Unit = ctx.deregister()

    override def register(): Unit = ctx.register()

    override def write(msg: AnyRef): Unit = ctx.write(msg)

    override def write(msg: AnyRef, msgId: Long): Unit = ctx.write(msg, msgId)

    override def writeAndFlush(msg: AnyRef): Unit = ctx.writeAndFlush(msg)

    override def writeAndFlush(msg: AnyRef, msgId: Long): Unit = ctx.writeAndFlush(msg, msgId)

    override def isBufferHandlerContext: Boolean = ctx.isBufferHandlerContext

    override def inboundAdaptiveBuffer: AdaptiveBuffer = ctx.inboundAdaptiveBuffer

    override def outboundAdaptiveBuffer: AdaptiveBuffer = ctx.outboundAdaptiveBuffer

    override def nextInboundAdaptiveBuffer: AdaptiveBuffer = ctx.nextInboundAdaptiveBuffer

    override def nextOutboundAdaptiveBuffer: AdaptiveBuffer = ctx.nextOutboundAdaptiveBuffer

}
