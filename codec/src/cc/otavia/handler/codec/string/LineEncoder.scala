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

import cc.otavia.buffer.pool.AdaptiveBuffer
import cc.otavia.core.channel.{ChannelHandlerContext, ChannelPipeline}
import cc.otavia.handler.codec.MessageToByteEncoder

import java.nio.charset.{Charset, StandardCharsets}
import scala.language.unsafeNulls

/** Apply a line separator to the requested [[String]] and encode it into a [[AdaptiveBuffer]]. A typical setup for a
 *  text-based line protocol in a TCP/IP socket would be:
 *
 *  [[ChannelPipeline]] pipeline = ...
 *
 *  // Encoder
 *
 *  pipeline.addLast("lineEncoder", new [[LineEncoder]](LineSeparator.UNIX, StandardCharsets.UTF_8))
 *
 *  and then you can use a String as a message:
 *
 *  pipeline.write("Did you say '" + msg + "'?")
 *
 *  @param lineSeparator
 *    separator of each message
 *  @param charset
 *    message encoder charset
 */
class LineEncoder(private val lineSeparator: Array[Byte], private val charset: Charset) extends MessageToByteEncoder {

    /** Creates a new instance with the specified line separator and character set. */
    def this(lineSeparator: LineSeparator, charset: Charset) = this(lineSeparator.value.getBytes(charset), charset)

    /** Creates a new instance with the current system line separator and UTF-8 charset encoding. */
    def this() = this(LineSeparator.DEFAULT, StandardCharsets.UTF_8)

    /** Creates a new instance with the specified line separator and UTF-8 charset encoding. */
    def this(lineSeparator: LineSeparator) = this(lineSeparator, StandardCharsets.UTF_8)

    /** Creates a new instance with the specified character set. */
    def this(charset: Charset) = this(LineSeparator.DEFAULT, charset)

    override protected def encode(ctx: ChannelHandlerContext, input: AnyRef, output: AdaptiveBuffer): Unit = {
        output.writeCharSequence(input.asInstanceOf[String], charset)
        output.writeBytes(lineSeparator)
    }

}
