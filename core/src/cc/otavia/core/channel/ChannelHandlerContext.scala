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

import cc.otavia.buffer.pool.{AdaptiveBuffer, PooledPageAllocator, RecyclablePageBuffer}
import cc.otavia.core.channel.inflight.QueueMap
import cc.otavia.core.stack.{ChannelPromise, ChannelStack}
import cc.otavia.core.timer.Timer

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

    /** Return the assigned [[PooledPageAllocator]] which will be used to allocate off-heap [[RecyclablePageBuffer]]s.
     */
    final def directAllocator(): PooledPageAllocator = channel.directAllocator

    /** Return the assigned [[PooledPageAllocator]] which will be used to allocate heap [[RecyclablePageBuffer]]s. */
    final def heapAllocator(): PooledPageAllocator = channel.heapAllocator

    final def isBufferHandlerContext: Boolean = hasOutboundAdaptive || hasInboundAdaptive

    /** Write data by upstream. */
    def inboundAdaptiveBuffer: AdaptiveBuffer

    /** Write data by upstream. */
    def outboundAdaptiveBuffer: AdaptiveBuffer

    /** If the handler has inbound [[AdaptiveBuffer]] */
    final def hasInboundAdaptive: Boolean = handler.hasInboundAdaptive

    /** If the handler has outbound [[AdaptiveBuffer]] */
    final def hasOutboundAdaptive: Boolean = handler.hasOutboundAdaptive

    def inflightFutures: QueueMap[ChannelPromise]

    def inflightStacks[T <: AnyRef]: QueueMap[ChannelStack[T]]

}
