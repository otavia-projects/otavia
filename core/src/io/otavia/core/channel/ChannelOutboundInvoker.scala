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

import io.otavia.core.actor.ChannelsActor
import io.otavia.core.channel.estimator.ReadBufferAllocator
import io.otavia.core.stack.{ChannelFuture, DefaultFuture, Future}

import java.net.SocketAddress

trait ChannelOutboundInvoker {

    def bind(local: SocketAddress, future: ChannelFuture): ChannelFuture

    def connect(remote: SocketAddress, local: Option[SocketAddress], future: ChannelFuture): ChannelFuture

    def disconnect(future: ChannelFuture): ChannelFuture

    def close(future: ChannelFuture): ChannelFuture

    def shutdown(direction: ChannelShutdownDirection, future: ChannelFuture): ChannelFuture

    def register(future: ChannelFuture): ChannelFuture

    def deregister(future: ChannelFuture): ChannelFuture

    def read(readBufferAllocator: ReadBufferAllocator): this.type

    def read(): this.type

    def write(msg: AnyRef): Unit

    def write(msg: AnyRef, msgId: Long): Unit

//    def writeComplete(): Unit // TODO for clean AdaptiveBuffer in batch write for example http pipeline request

    def flush(): this.type

    def writeAndFlush(msg: AnyRef): Unit

    def writeAndFlush(msg: AnyRef, msgId: Long): Unit

    /** Send a custom outbound event via this [[ChannelOutboundInvoker]] through the [[ChannelPipeline]]. This will
     *  result in having the {{{ChannelHandler.sendOutboundEvent(ChannelHandlerContext, Object)}}} method called of the
     *  next [[ChannelHandler]] contained in the [[ChannelPipeline]] of the [[Channel]].
     *
     *  @param event
     */
    def sendOutboundEvent(event: AnyRef): Unit

    /** Executor of this channel instance, the channel inbound and outbound event must execute in the binding executor
     */
    def executor: ChannelsActor[?]

}
