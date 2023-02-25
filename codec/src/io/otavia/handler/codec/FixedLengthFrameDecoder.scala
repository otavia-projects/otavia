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

package io.otavia.handler.codec

import io.netty5.buffer.Buffer
import io.otavia.core.channel.ChannelHandlerContext
import io.otavia.handler.codec.ByteToMessageDecoder.{COMPOSITE_CUMULATOR, Cumulator}

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
 *  @param cumulator Buffer [[Cumulator]]
 */
class FixedLengthFrameDecoder(private val frameLength: Int, cumulator: Cumulator = COMPOSITE_CUMULATOR)
    extends ByteToMessageDecoder(cumulator) {

    assert(frameLength > 0)

    override protected def decode(ctx: ChannelHandlerContext, in: Buffer): Unit = {
        val maybe = decode0(ctx, in)
        maybe match
            case msg: AnyRef => ctx.fireChannelRead(msg)
            case null        =>
    }

    /** Create a frame out of the Buffer and return it.
     *
     *  @param ctx
     *    the [[ChannelHandlerContext]] which this [[ByteToMessageDecoder]] belongs to
     *  @param buffer
     *    the [[Buffer]] from which to read data
     *  @return
     *    frame the [[Buffer]] which represent the frame or null if no frame could be created.
     */
    @throws[Exception]
    protected def decode0(ctx: ChannelHandlerContext, buffer: Buffer): AnyRef | Null =
        if (buffer.readableBytes() < frameLength) null else buffer.readSplit(frameLength)

}
