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

package cc.otavia.handler.codec

import cc.otavia.buffer.pool.AdaptiveBuffer
import cc.otavia.core.channel.ChannelHandlerContext

/** A decoder that splits the received [[Buffer]]s by the fixed number of bytes. For example, if you received the
 *  following four fragmented packets:
 *  <pre>
 *  +---+----+------+----+\
 *  | A | BC | DEFG | HI |\
 *  +---+----+------+----+\</pre>
 *
 *  A [[FixedLengthFrameDecoder]](3) will decode them into the following three packets with the fixed length:
 *  <pre>
 *  +-----+-----+-----+\
 *  | ABC | DEF | GHI |\
 *  +-----+-----+-----+\</pre>
 *
 *  @param frameLength the length of the frame
 */
class FixedLengthFrameDecoder(private val frameLength: Int) extends ByteToMessageDecoder {

    assert(frameLength > 0)

    override protected def decode(ctx: ChannelHandlerContext, input: AdaptiveBuffer): Unit = {
        if (input.readableBytes >= frameLength) {
            val data = new Array[Byte](frameLength)
            input.readBytes(data)
            ctx.fireChannelRead(data)
        }
    }

}
