/*
 * Copyright 2022 Yan Kun <yan_kun_1992@foxmail.com>
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

package cc.otavia.core.channel.handler

import cc.otavia.buffer.pool.AdaptiveBuffer
import cc.otavia.core.channel.{ChannelHandler, ChannelHandlerContext}

/** channel io transport --> Byte2ByteDecoder --> Byte2MessageDecoder --> Message2MessageDecoder --> channel inflight
 *
 *  channel io transport <-- Byte2ByteEncoder <-- Message2ByteEncoder <-- Message2MessageEncoder <-- channel inflight
 */
trait Byte2ByteDecoder extends ChannelHandler {

    final override def hasInboundAdaptive: Boolean = true

    override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef): Unit = {
        msg match
            case buffer: AdaptiveBuffer =>
                val readable1 = ctx.inboundAdaptiveBuffer.readableBytes
                decode(ctx, buffer, ctx.inboundAdaptiveBuffer)
                val readable2 = ctx.inboundAdaptiveBuffer.readableBytes
                if (readable2 - readable1 > 0)
                    ctx.fireChannelRead(ctx.inboundAdaptiveBuffer) // transfer generated data to next handler
            case _ => ctx.fireChannelRead(msg)
    }

    @throws[Exception]
    protected def decode(ctx: ChannelHandlerContext, input: AdaptiveBuffer, output: AdaptiveBuffer): Unit

}
