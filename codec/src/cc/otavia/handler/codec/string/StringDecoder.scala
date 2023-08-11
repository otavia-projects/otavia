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

package cc.otavia.handler.codec.string

import cc.otavia.core.buffer.AdaptiveBuffer
import cc.otavia.core.channel.ChannelHandlerContext
import cc.otavia.handler.codec.ByteToMessageDecoder

import java.nio.charset.{Charset, StandardCharsets}
import scala.language.unsafeNulls

class StringDecoder(private val charset: Charset) extends ByteToMessageDecoder {

    def this() = this(StandardCharsets.UTF_8)

    override protected def decode(ctx: ChannelHandlerContext, input: AdaptiveBuffer): Unit = {
        val msg = input.readCharSequence(input.readableBytes, charset)
        ctx.fireChannelRead(msg)
    }

}
