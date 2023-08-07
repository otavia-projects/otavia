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

package cc.otavia.handler.codec

import cc.otavia.core.buffer.AdaptiveBuffer
import cc.otavia.core.channel.{ChannelHandler, ChannelHandlerContext}

private[codec] trait ByteToByteDecoderTrait extends ChannelHandler {

    override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef): Unit = msg match
        case adaptiveBufferMessage: ByteToByteHandler.AdaptiveBufferMessage =>
            val buffer = ctx.inboundAdaptiveBuffer
            val out    = ctx.nextInboundAdaptiveBuffer
            decode(ctx, adaptiveBufferMessage, buffer, out)
        case _ => ctx.fireChannelRead(msg)

    @throws[Exception]
    protected def decode(
        ctx: ChannelHandlerContext,
        msg: ByteToByteHandler.AdaptiveBufferMessage,
        input: AdaptiveBuffer,
        output: AdaptiveBuffer
    ): Unit

}
