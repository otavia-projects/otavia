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

package cc.otavia.handler.http

import cc.otavia.buffer.Buffer
import cc.otavia.buffer.pool.AdaptiveBuffer
import cc.otavia.core.cache.ThreadLocal
import cc.otavia.core.channel.ChannelHandlerContext
import cc.otavia.core.slf4a.{Logger, LoggerFactory}
import cc.otavia.core.stack.ChannelFuture
import cc.otavia.core.timer.TimeoutTrigger
import cc.otavia.handler.codec.ByteToMessageCodec
import cc.otavia.http.server.*
import cc.otavia.http.server.Router.*
import cc.otavia.http.{HttpConstants, HttpHeader, HttpVersion, MediaType}
import cc.otavia.serde.Serde

import java.nio.charset.{Charset, StandardCharsets}
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import scala.language.unsafeNulls

class ServerCodec(val routerMatcher: RouterMatcher, val dates: ThreadLocal[Array[Byte]]) extends ByteToMessageCodec {

    import ServerCodec.*

    private var logger: Logger             = _
    private var ctx: ChannelHandlerContext = _

    private var parseState: Int       = ST_PARSE_HEADLINE
    private var currentRouter: Router = _

    // decode http request
    override protected def decode(ctx: ChannelHandlerContext, input: AdaptiveBuffer): Unit = {
        while (input.readableBytes > 0) {
            if (parseState == ST_PARSE_HEADLINE) {
                if (input.readableBytes <= 4096) {
                    val lineLength = input.bytesBefore(HttpConstants.HEADER_LINE_END)
                    if (lineLength != -1) parseHeadLine(input, lineLength)
                } else {
                    val lineLength =
                        input.bytesBefore(HttpConstants.HEADER_LINE_END, input.readerOffset, input.readerOffset + 4096)

                    if (lineLength != -1) parseHeadLine(input, lineLength)
                    else { // illegal http packet
                        input.skipReadableBytes(input.readableBytes)
                        logger.error(s"Illegal http packet, head line is large than 4096, close channel ${ctx.channel}")
                        ctx.channel.close(ChannelFuture())
                    }
                }
            }

            if (parseState == ST_PARSE_HEADERS) {
                val headersLength = input.bytesBefore(HttpConstants.HEADERS_END)
                if (headersLength != -1) {
                    currentRouter match
                        case ControllerRouter(
                              method,
                              path,
                              controller,
                              headers,
                              paramsSerde,
                              contentSerde,
                              responseSerde
                            ) =>
                        case StaticFilesRouter(path, root) =>
                        case NotFoundRouter(page)          =>
                        case ConstantRouter(method, path, value, serde, mediaType) =>
                            input.skipReadableBytes(headersLength + 4)
                            parseState = ST_PARSE_HEADLINE
                            responseConstant(value, serde, mediaType)
                }
            }

            if (parseState == ST_PARSE_BODY) {
                ???
            }
        }
    }

    // encode http response
    override protected def encode(ctx: ChannelHandlerContext, output: AdaptiveBuffer, msg: AnyRef, msgId: Long): Unit =
        ???

    private def parseHeadLine(buffer: Buffer, lineLength: Int): Unit = {
        val start = buffer.readerOffset
        while (buffer.skipIfNextIs(HttpConstants.SP)) {}
        currentRouter = routerMatcher.choice(buffer)
        buffer.readerOffset(start + lineLength + 2)
        parseState = ST_PARSE_HEADERS
    }

    private def responseConstant(value: Any, serde: Serde[?], media: MediaType): Unit = {
        val buffer = ctx.outboundAdaptiveBuffer
        buffer.writeBytes(RESPONSE_NORMAL)
        buffer.writeBytes(HttpHeader.Key.CONTENT_TYPE)
        buffer.writeBytes(HttpConstants.HEADER_SPLITTER)
        buffer.writeBytes(media.fullName)
        buffer.writeBytes(HttpConstants.HEADER_LINE_END)
        buffer.writeBytes(dates.get())
        buffer.writeBytes(HttpHeader.Key.CONTENT_LENGTH)
        buffer.writeBytes(HttpConstants.HEADER_SPLITTER)
        val lengthOffset = buffer.writerOffset
        buffer.writeBytes("                   ".getBytes(StandardCharsets.US_ASCII))

        buffer.writeBytes(HttpConstants.HEADERS_END)

        val contentStart = buffer.writerOffset
        serde.serializeAny(value, buffer)
        val contentEnd = buffer.writerOffset

        buffer.setCharSequence(lengthOffset, (contentEnd - contentStart).toString)

        ctx.writeAndFlush(buffer)
    }

    override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
        super.handlerAdded(ctx)
        this.logger = LoggerFactory.getLogger(getClass, ctx.system)
        this.ctx = ctx
    }

    override def handlerRemoved(ctx: ChannelHandlerContext): Unit = {
        this.ctx = null
        super.handlerRemoved(ctx)
    }

    private def httpVersion(buffer: Buffer): HttpVersion = {
        while (buffer.skipIfNextIs(HttpConstants.SP)) {}
        val version =
            if (buffer.skipIfNextAre(HttpVersion.HTTP_1_1.bytes)) HttpVersion.HTTP_1_1
            else if (buffer.skipIfNextAre(HttpVersion.HTTP_2.bytes)) HttpVersion.HTTP_2
            else HttpVersion.HTTP_1_0
        buffer.skipReadableBytes(buffer.bytesBefore(HttpConstants.HEADER_LINE_END) + 2)
        version
    }

}

object ServerCodec {

    /** server name of this http server */
    private val SERVER_NAME: Array[Byte] = "otavia-http".getBytes(StandardCharsets.US_ASCII)

    private val RESPONSE_NORMAL: Array[Byte] =
        """HTTP/1.1 200 OK
          |Server: otavia-http
          |""".stripMargin.getBytes(StandardCharsets.US_ASCII)

    private val ST_PARSE_HEADLINE: Int = 0
    private val ST_PARSE_HEADERS: Int  = 1
    private val ST_PARSE_BODY: Int     = 2

}
