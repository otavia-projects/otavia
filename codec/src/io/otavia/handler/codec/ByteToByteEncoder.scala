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

package io.otavia.handler.codec

import io.otavia.core.buffer.AdaptiveBuffer
import io.otavia.core.channel.ChannelHandlerContext

abstract class ByteToByteEncoder extends ByteToByteHandler {

    override def write(ctx: ChannelHandlerContext, msg: AnyRef): Unit = {
        if (msg == ByteToByteHandler.ADAPTIVE_BUFFER_NOTICE) {
            val input  = ctx.outboundAdaptiveBuffer
            val output = ctx.nextOutboundAdaptiveBuffer
            encode(ctx, input, output)
        } else ctx.write(msg)
    }

    @throws[Exception]
    protected def encode(ctx: ChannelHandlerContext, input: AdaptiveBuffer, output: AdaptiveBuffer): Unit

}
