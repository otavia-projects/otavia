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

import cc.otavia.buffer.pool.AdaptiveBuffer
import cc.otavia.buffer.{Buffer, BufferUtils}
import cc.otavia.core.cache.{ActorThreadLocal, ThreadLocal}
import cc.otavia.core.channel
import cc.otavia.core.channel.{ChannelHandlerContext, DefaultFileRegion}
import cc.otavia.core.slf4a.{Logger, LoggerFactory}
import cc.otavia.core.stack.ChannelFuture
import cc.otavia.core.timer.TimeoutTrigger
import cc.otavia.handler.codec.ByteToMessageCodec
import cc.otavia.http.*
import cc.otavia.http.server.*
import cc.otavia.http.server.Router.*
import cc.otavia.serde.Serde

import java.io.File
import java.nio.charset.{Charset, StandardCharsets}
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import scala.collection.mutable
import scala.language.unsafeNulls

class ServerCodec(val routerMatcher: RouterMatcher, val dates: ThreadLocal[Array[Byte]]) extends ByteToMessageCodec {

    import ServerCodec.*

    private var logger: Logger             = _
    private var ctx: ChannelHandlerContext = _

    private var parseState: Int       = ST_PARSE_HEADLINE
    private var currentRouter: Router = _

    private var currentBodyLength: Int = 0

    private val staticFilesCache = new ActorThreadLocal[mutable.HashMap[String, DefaultFileRegion]] {
        override protected def initialValue(): mutable.HashMap[String, DefaultFileRegion] = mutable.HashMap.empty
    }

    private def staticCache: mutable.HashMap[String, DefaultFileRegion] = staticFilesCache.get()

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
                    val headersStart = input.readerOffset
                    val headersEnd   = headersStart + headersLength + 4
                    val contentLengthOffset =
                        input.bytesBefore(HttpHeader.Key.CONTENT_LENGTH, headersStart, headersEnd, true)
                    val contentLength: Int = if (contentLengthOffset != -1) {
                        val contentStart  = headersStart + contentLengthOffset + HttpHeader.Key.CONTENT_LENGTH.length
                        val t             = input.getCharSequence(contentStart, 10, StandardCharsets.US_ASCII).toString
                        val contentLength = input.bytesBefore(HttpConstants.HEADER_LINE_END, contentStart, headersEnd)
                        input
                            .getCharSequence(contentStart, contentLength, StandardCharsets.US_ASCII)
                            .toString
                            .replace(':', ' ')
                            .trim
                            .toInt
                    } else 0
                    currentRouter match
                        case ControllerRouter(_, _, _, requestSerde, _) =>
                            if (requestSerde.requireHeaders.nonEmpty) {} else input.skipReadableBytes(headersLength + 4)
                            if (contentLength == 0) {
                                parseState = ST_PARSE_HEADLINE
                                generateRequest(contentLength, requestSerde, input)
                            } else {
                                currentBodyLength = contentLength
                                parseState = ST_PARSE_BODY
                            }
                        case StaticFilesRouter(path, root) =>
                            input.skipReadableBytes(headersLength + 4 + contentLength)
                            parseState = ST_PARSE_HEADLINE
                            val routerContext = routerMatcher.context
                            val rootFile      = root.toFile
                            if (rootFile.isFile && routerContext.remaining == null) {
                                responseFile(rootFile)
                            } else if (rootFile.isDirectory && routerContext.remaining != null) {
                                val file = new File(rootFile, routerContext.remaining)
                                if (file.exists()) responseFile(file)
                                else {
                                    // 404
                                }
                            } else {
                                // 404
                            }
                        case NotFoundRouter(page) =>
                        case ConstantRouter(method, path, value, serde, mediaType) =>
                            input.skipReadableBytes(headersLength + 4 + contentLength)
                            parseState = ST_PARSE_HEADLINE
                            responseConstant(value, serde, mediaType)
                }
            }

            if (parseState == ST_PARSE_BODY) {
                currentRouter match
                    case ControllerRouter(method, path, controller, requestSerde, responseSerde) =>
                        parseState = ST_PARSE_HEADLINE
                        generateRequest(currentBodyLength, requestSerde, input)
                    case _ =>
            }
        }

        if (ctx.outboundAdaptiveBuffer.readableBytes > 0) ctx.flush()
    }

    // encode http response
    override protected def encode(ctx: ChannelHandlerContext, output: AdaptiveBuffer, msg: AnyRef, id: Long): Unit = {
        println("")
        ???
    }

    private def parseHeadLine(buffer: Buffer, lineLength: Int): Unit = {
        val start = buffer.readerOffset
        while (buffer.skipIfNextIs(HttpConstants.SP)) {}
        currentRouter = routerMatcher.choice(buffer)
        buffer.readerOffset(start + lineLength + 2)
        parseState = ST_PARSE_HEADERS
    }

    private def generateRequest(contentLength: Int, requestSerde: HttpRequestSerde[?, ?, ?], input: Buffer): Unit = {
        requestSerde.setPathVars(routerMatcher.context.pathVars)
        val endIdx  = input.readerOffset + contentLength
        val request = requestSerde.deserialize(input)
        request.setRouter(currentRouter.asInstanceOf[ControllerRouter])
        val routerContext = routerMatcher.context
        request.setMethod(routerContext.method)
        request.setPath(routerContext.path)
        input.readerOffset(endIdx)
        ctx.fireChannelRead(request, ctx.channel.generateMessageId)
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
        buffer.writeBytes(ServerCodec.CONTENT_LENGTH_PLACEHOLDER)

        buffer.writeBytes(HttpConstants.HEADERS_END)

        val contentStart = buffer.writerOffset
        serde.serializeAny(value, buffer)
        val contentEnd = buffer.writerOffset

        buffer.setCharSequence(lengthOffset, (contentEnd - contentStart).toString)

        ctx.write(buffer)
    }

    private def responseFile(file: File): Unit = {
        val filePathName = file.getAbsolutePath
        val region =
            staticCache.getOrElseUpdate(filePathName, new DefaultFileRegion(file, 0, file.length())).retain

        val buffer = ctx.outboundAdaptiveBuffer
        buffer.writeBytes(RESPONSE_NORMAL)
        buffer.writeBytes(dates.get())
        buffer.writeBytes(HttpHeader.Key.CONTENT_LENGTH)
        buffer.writeBytes(HttpConstants.HEADER_SPLITTER)
        buffer.writeBytes(region.countBytes)
        buffer.writeBytes(HttpConstants.HEADER_LINE_END)
        writeStaticMediaHeader(buffer, filePathName)

        buffer.writeBytes(HttpConstants.HEADERS_END)

        ctx.write(buffer)
        ctx.write(region)
    }

    private def writeStaticMediaHeader(buffer: Buffer, fileName: String): Unit = {
        buffer.writeBytes(dates.get())
        buffer.writeBytes(HttpHeader.Key.CONTENT_TYPE)
        buffer.writeBytes(HttpConstants.HEADER_SPLITTER)
        if (fileName.endsWith(".html")) buffer.writeBytes(MediaType.TEXT_HTML_UTF8.fullName)
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

    private val CONTENT_LENGTH_PLACEHOLDER = "                   ".getBytes(StandardCharsets.US_ASCII)

    private val ST_PARSE_HEADLINE: Int = 0
    private val ST_PARSE_HEADERS: Int  = 1
    private val ST_PARSE_BODY: Int     = 2

}
