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

package cc.otavia.core.channel.internal

import cc.otavia.buffer.pool.AdaptiveBuffer
import cc.otavia.core.actor.ChannelsActor
import cc.otavia.core.channel.*
import cc.otavia.core.channel.inflight.QueueMap
import cc.otavia.core.channel.message.ReadPlan
import cc.otavia.core.stack.{ChannelFuture, ChannelPromise, ChannelStack}

import java.net.SocketAddress
import java.nio.file.attribute.FileAttribute
import java.nio.file.{OpenOption, Path}

abstract class DelegatingChannelHandlerContext(private val ctx: ChannelHandlerContext) extends ChannelHandlerContext {

    def delegatingCtx: ChannelHandlerContext = ctx

    override def channel: Channel = ctx.channel

    override def executor: ChannelsActor[?] = ctx.executor

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

    override def fireChannelExceptionCaught(cause: Throwable, id: Long): DelegatingChannelHandlerContext.this.type = {
        ctx.fireChannelExceptionCaught(cause, id)
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

    override def read(readPlan: ReadPlan): this.type = {
        ctx.read(readPlan)
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

    override def bind(local: SocketAddress, future: ChannelFuture): ChannelFuture = ctx.bind(local, future)

    override def connect(remote: SocketAddress, local: Option[SocketAddress], future: ChannelFuture): ChannelFuture =
        ctx.connect(remote, local, future)

    override def open(
        path: Path,
        options: Seq[OpenOption],
        attrs: Seq[FileAttribute[?]],
        future: ChannelFuture
    ): ChannelFuture = ctx.open(path, options, attrs, future)

    override def disconnect(future: ChannelFuture): ChannelFuture = ctx.disconnect(future)

    override def close(future: ChannelFuture): ChannelFuture = ctx.close(future)

    override def shutdown(direction: ChannelShutdownDirection, future: ChannelFuture): ChannelFuture =
        ctx.shutdown(direction, future)

    override def deregister(future: ChannelFuture): ChannelFuture = ctx.deregister(future)

    override def register(future: ChannelFuture): ChannelFuture = ctx.register(future)

    override def write(msg: AnyRef): Unit = ctx.write(msg)

    override def write(msg: AnyRef, msgId: Long): Unit = ctx.write(msg, msgId)

    override def writeAndFlush(msg: AnyRef): Unit = ctx.writeAndFlush(msg)

    override def writeAndFlush(msg: AnyRef, msgId: Long): Unit = ctx.writeAndFlush(msg, msgId)

    override def inboundAdaptiveBuffer: AdaptiveBuffer = ctx.inboundAdaptiveBuffer

    override def outboundAdaptiveBuffer: AdaptiveBuffer = ctx.outboundAdaptiveBuffer

    override def inflightFutures: QueueMap[ChannelPromise] = ctx.inflightFutures

    override def inflightStacks[T <: AnyRef]: QueueMap[ChannelStack[T]] = ctx.inflightStacks

}
