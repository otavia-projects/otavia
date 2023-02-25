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
import io.netty5.util.internal.{SilentDispose, TypeParameterMatcher}
import io.otavia.core.channel.{ChannelHandlerAdapter, ChannelHandlerContext}

/** [[ChannelHandler]] which encodes message in a stream-like fashion from one message to a [[Buffer]]. Example
 *  implementation which encodes [[Integer]]s to a [[Buffer]].
 *
 *  {{{
 *       class IntegerEncoder extends MessageToByteEncoder[Integer]() {
 *             override protected def encode(ctx: ChannelHandlerContext, msg: Integer, out: Buffer): Unit = {
 *                 out.writeInt(msg)
 *             }
 *       }
 *  }}}
 */
abstract class MessageToByteEncoder[I]() extends ChannelHandlerAdapter {

    private var matcher: TypeParameterMatcher =
        TypeParameterMatcher.find(this, classOf[MessageToByteEncoder[?]], "I").nn

    def this(outboundMessageType: Class[? <: I]) = {
        this()
        this.matcher = TypeParameterMatcher.get(outboundMessageType).nn
    }

    def this(matcher: TypeParameterMatcher) = {
        this()
        this.matcher = matcher
    }

    /** Returns true if the given message should be handled. If false it will be passed to the next [[ChannelHandler]]
     *  in the [[ChannelPipeline]].
     */
    @throws[Exception]
    def acceptOutboundMessage(msg: AnyRef): Boolean = matcher.`match`(msg)

    override def write(ctx: ChannelHandlerContext, msg: AnyRef): Unit = {
        var buf: Buffer | Null = null
        try {
            if (acceptOutboundMessage(msg)) {
                val cast = msg.asInstanceOf[I]
                buf = allocateBuffer(ctx, cast)
                val buffer: Buffer = buf.nn
                try {
                    encode(ctx, cast, buffer)
                } finally {
                    SilentDispose.autoClosing(cast) match
                        case null                     =>
                        case closeable: AutoCloseable => closeable.close()
                }
                if (buffer.readableBytes() > 0) {
                    ctx.write(buffer)
                } else {
                    ctx.write(ctx.directAllocator().allocate(0).nn)
                }
            } else ctx.write(msg)
        } catch {
            case e: EncoderException => throw e
            case e: Throwable        => throw new EncoderException(e)
        } finally if (buf != null) buf.nn.close()
    }

    /** Allocate a [[Buffer]] which will be used as argument of [[encode]].
     *
     *  @param ctx
     *    the [[ChannelHandlerContext]] which this [[MessageToByteEncoder]] belongs to
     *  @param msg
     *    the message to be encoded
     */
    @throws[Exception]
    protected def allocateBuffer(ctx: ChannelHandlerContext, msg: I): Buffer

    /** Encode a message into a [[Buffer]]. This method will be called for each written message that can be handled by
     *  this encoder.
     *
     *  @param ctx
     *    the [[ChannelHandlerContext]] which this [[MessageToByteEncoder]] belongs to
     *  @param msg
     *    the message to encode
     *  @param out
     *    the [[Buffer]] into which the encoded message will be written
     *  @throws Exception
     *    is thrown if an error occurs
     */
    @throws[Exception]
    protected def encode(ctx: ChannelHandlerContext, msg: I, out: Buffer): Unit

}
