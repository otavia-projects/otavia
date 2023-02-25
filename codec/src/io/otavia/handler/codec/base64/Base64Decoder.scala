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

package io.otavia.handler.codec.base64

import io.otavia.core.buffer.AdaptiveBuffer
import io.otavia.core.channel.ChannelHandlerContext
import io.otavia.handler.codec.{ByteToByteDecoder, ByteToByteHandler}

class Base64Decoder(private val dialect: Base64Dialect) extends ByteToByteDecoder {

    def this() = this(Base64Dialect.STANDARD)

    override def isSharable: Boolean = true

    override protected def decode(
        ctx: ChannelHandlerContext,
        msg: ByteToByteHandler.AdaptiveBufferMessage,
        input: AdaptiveBuffer,
        output: AdaptiveBuffer
    ): Unit = {
        // TODO
        ???
    }

}
