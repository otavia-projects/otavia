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

import java.net.SocketAddress

trait ChannelOutboundInvoker {

    @throws[Exception]
    def bind(): Unit

    @throws[Exception]
    def connect(): Unit

    def disconnect(): Unit

    def close(): Unit

    def shutdown(direction: ChannelShutdownDirection): Unit

    def register(): Unit

    def deregister(): Unit

    def read(readBufferAllocator: ReadBufferAllocator): ChannelOutboundInvoker
    def read(): ChannelOutboundInvoker

    def write(msg: AnyRef): Unit

    def write(msg: AnyRef, msgId: Long): Unit

    def flush(): ChannelOutboundInvoker

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
