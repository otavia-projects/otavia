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
import io.otavia.core.channel.estimator.ReadBufferAllocator
import io.otavia.core.timer.Timer

trait ChannelHandlerContext extends ChannelOutboundInvoker with ChannelInboundInvoker {

    /** The assigned [[ChannelPipeline]] */
    def pipeline: ChannelPipeline

    /** The [[Channel]] which is bound to the [[ChannelHandlerContext]]. */
    def channel: Channel = pipeline.channel

    /** [[Timer]] of this actor system. */
    def timer: Timer = channel.timer

    /** The unique name of the [[ChannelHandlerContext]].The name was used when then [[ChannelHandler]] was added to the
     *  [[ChannelPipeline]]. This name can also be used to access the registered [[ChannelHandler]] from the
     *  [[ChannelPipeline]].
     */
    def name: String

    /** The [[ChannelHandler]] that is bound this [[ChannelHandlerContext]]. */
    def handler: ChannelHandler

    /** Return `true` if the [[ChannelHandler]] which belongs to this context was removed from the [[ChannelPipeline]].
     */
    def isRemoved: Boolean

    override def fireChannelRegistered(): ChannelHandlerContext

    override def fireChannelUnregistered(): ChannelHandlerContext

    override def fireChannelActive(): ChannelHandlerContext

    override def fireChannelInactive(): ChannelHandlerContext

    override def fireChannelShutdown(direction: ChannelShutdownDirection): ChannelHandlerContext

    override def fireChannelExceptionCaught(cause: Throwable): ChannelHandlerContext

    override def fireChannelInboundEvent(evt: AnyRef): ChannelHandlerContext

    override def fireChannelTimeoutEvent(id: Long): ChannelHandlerContext

    override def fireChannelRead(msg: AnyRef): ChannelHandlerContext

    override def fireChannelRead(msg: AnyRef, msgId: Long): ChannelHandlerContext

    override def fireChannelReadComplete(): ChannelHandlerContext

    override def fireChannelWritabilityChanged(): ChannelHandlerContext

    override def read(readBufferAllocator: ReadBufferAllocator): ChannelHandlerContext

    override def read(): ChannelHandlerContext

    override def flush(): ChannelHandlerContext

    /** Return the assigned [[BufferAllocator]] which will be used to allocate off-heap [[Buffer]]s. */
    final def directAllocator(): BufferAllocator = channel.directAllocator

    /** Return the assigned [[BufferAllocator]] which will be used to allocate heap [[Buffer]]s. */
    final def heapAllocator(): BufferAllocator = channel.headAllocator

}
