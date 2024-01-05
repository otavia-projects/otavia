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

package cc.otavia.http.codec

import cc.otavia.buffer.pool.AdaptiveBuffer
import cc.otavia.buffer.{Buffer, BufferUtils}
import cc.otavia.common.SystemPropertyUtil
import cc.otavia.core.cache.{ActorThreadLocal, ThreadLocal}
import cc.otavia.core.channel
import cc.otavia.core.channel.{ChannelHandlerContext, DefaultFileRegion}
import cc.otavia.core.slf4a.{Logger, LoggerFactory}
import cc.otavia.core.stack.ChannelFuture
import cc.otavia.core.system.ActorThread
import cc.otavia.core.timer.{TimeoutTrigger, Timer}
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

class ServerCodec(val routerMatcher: RouterMatcher, val dates: ThreadLocal[Array[Byte]], val serverName: Array[Byte])
    extends ByteToMessageCodec {

    import ServerCodec.*

    private var logger: Logger             = _
    private var ctx: ChannelHandlerContext = _

    private var parseState: Int       = ST_PARSE_HEADLINE
    private var currentRouter: Router = _

    private var currentBodyLength: Int = 0

    private var currentRequest: HttpRequest[?, ?] = _

    private var packetTimeoutId: Long = Timer.INVALID_TIMEOUT_REGISTER_ID

    private val staticFilesCache = new ActorThreadLocal[mutable.HashMap[String, DefaultFileRegion]] {
        override protected def initialValue(): mutable.HashMap[String, DefaultFileRegion] = mutable.HashMap.empty
    }

    private def staticCache: mutable.HashMap[String, DefaultFileRegion] = staticFilesCache.get()

    // decode http request
    override protected def decode(ctx: ChannelHandlerContext, input: AdaptiveBuffer): Unit = {
        if (packetTimeoutId != Timer.INVALID_TIMEOUT_REGISTER_ID) {
            ctx.timer.cancelTimerTask(packetTimeoutId)
            packetTimeoutId = Timer.INVALID_TIMEOUT_REGISTER_ID
        }
        var continue = true
        while (continue && input.readableBytes > 0) {
            if (parseState == ST_PARSE_HEADLINE) {
                if (input.readableBytes <= 4096) {
                    val lineLength = input.bytesBefore(HttpConstants.HEADER_LINE_END)
                    if (lineLength != -1) parseHeadLine(input, lineLength) else continue = false
                } else {
                    val lineLength =
                        input.bytesBefore(HttpConstants.HEADER_LINE_END, input.readerOffset, input.readerOffset + 4096)

                    if (lineLength != -1) parseHeadLine(input, lineLength)
                    else { // illegal http packet
                        input.skipReadableBytes(input.readableBytes)
                        logger.error(s"Illegal http packet, head line is large than 4096, close channel ${ctx.channel}")
                        ctx.channel.pipeline.close(ChannelFuture())
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
                        val contentLength = input.bytesBefore(HttpConstants.HEADER_LINE_END, contentStart, headersEnd)
                        input
                            .getCharSequence(contentStart, contentLength, StandardCharsets.US_ASCII)
                            .toString
                            .replaceAll(":|\\s", "")
                            .toInt
                    } else 0
                    currentRouter match
                        case ControllerRouter(_, _, _, requestFactory, _) =>
                            if (requestFactory.requireHeaders.nonEmpty) {
                                // TODO: parse http headers which is required by user
                            } else input.skipReadableBytes(headersLength + 4)
                            if (contentLength == 0) {
                                parseState = ST_PARSE_HEADLINE
                                if (requestFactory.contentSerde.nonEmpty) {
                                    // TODO
                                } else {
                                    val request = currentRequest
                                    currentRequest = null
                                    currentRouter = null
                                    ctx.fireChannelRead(request, ctx.channel.generateMessageId)
                                }
                            } else {
                                currentBodyLength = contentLength
                                parseState = ST_PARSE_BODY
                            }
                        case _ =>
                            input.skipReadableBytes(headersLength + 4 + contentLength)
                            parseState = ST_PARSE_HEADLINE
                            if (ctx.channel.inflightStackSize + ctx.channel.pendingStackSize == 0) {
                                // response directly
                                currentRouter match
                                    case StaticFilesRouter(path, root) =>
                                        val remaining =
                                            if (currentRequest != null)
                                                currentRequest.asInstanceOf[InternalHttpRequest].subPath
                                            else
                                                routerMatcher.context.remaining
                                        responseStatic(root, remaining)
                                    case router: NotFoundRouter => response404(router)
                                    case ConstantRouter(method, path, value, serde, mediaType) =>
                                        responseConstant(value, serde, mediaType)
                                    case _ => // can't reach this branch
                            } else {
                                // send to channel inflight wait the others head requests response completed
                                // because http 1.1 is head of line
                                setInternalRequest()
                                ctx.fireChannelRead(currentRequest, ctx.channel.generateMessageId)
                            }
                            currentRequest = null
                            currentRouter = null
                } else { // headers data not received completed, save the parse head line context
                    continue = false
                    // save current http request router context
                    setInternalRequest()
                }
            }

            if (parseState == ST_PARSE_BODY) {
                if (input.readableBytes >= currentBodyLength) {
                    currentRouter match
                        case ControllerRouter(method, path, controller, requestSerde, responseSerde) =>
                            parseBody(currentBodyLength, requestSerde, input)
                        case _ =>
                    parseState = ST_PARSE_HEADLINE
                    currentRequest = null
                    currentRouter = null
                } else continue = false
            }
        }

        if (ctx.outboundAdaptiveBuffer.readableBytes > 0) {
            ctx.flush()
            ctx.outboundAdaptiveBuffer.compact()
        }

        if (HALF_PACKET_TIMEOUT_ENABLE && !continue) { // handle unfinished http packet
            packetTimeoutId = ctx.timer.registerChannelTimeout(TimeoutTrigger.DelayTime(1000), ctx.channel)
        } else input.compact()
    }

    // encode http response
    override protected def encode(ctx: ChannelHandlerContext, output: AdaptiveBuffer, msg: AnyRef, id: Long): Unit = {
        val request = ctx.inflightStacks[HttpRequest[?, ?]].unsafeBorrow(id).message
        val router  = request.router
        router match
            case ControllerRouter(method, path, controller, requestFactory, responseSerde) =>
                msg match
                    case response: HttpResponse[?] =>
                        writeResponseHeadLine(HttpStatus.OK, output)
                        writeServerHeader(output)
                        output.writeBytes(dates.get())
                        val body = response.content
                        if (!body.isInstanceOf[OK]) {
                            writeHeader(HttpHeader.Key.CONTENT_TYPE, responseSerde.mediaType.fullName, output)
                            val lengthOffset = writeContentLengthPlaceholder(output) // reset content length later
                            output.writeBytes(HttpConstants.HEADER_LINE_END) // headers end

                            // serialize content
                            val contentStart = output.writerOffset
                            responseSerde.contentSerde.serializeAny(body, output)
                            val contentEnd = output.writerOffset
                            output.setCharSequence(lengthOffset, (contentEnd - contentStart).toString)
                        } else output.writeBytes(HttpConstants.HEADER_LINE_END)
                    case body =>
                        writeResponseHeadLine(HttpStatus.OK, output)
                        writeServerHeader(output)
                        output.writeBytes(dates.get())
                        if (!body.isInstanceOf[OK]) {
                            writeHeader(HttpHeader.Key.CONTENT_TYPE, responseSerde.mediaType.fullName, output)
                            val lengthOffset = writeContentLengthPlaceholder(output) // reset content length later
                            output.writeBytes(HttpConstants.HEADER_LINE_END) // headers end

                            // serialize content
                            val contentStart = output.writerOffset
                            responseSerde.contentSerde.serializeAny(body, output)
                            val contentEnd = output.writerOffset
                            output.setCharSequence(lengthOffset, (contentEnd - contentStart).toString)
                        } else output.writeBytes(HttpConstants.HEADER_LINE_END)
            case StaticFilesRouter(path, root) =>
                val remaining = request.asInstanceOf[InternalHttpRequest].subPath
                responseStatic(root, remaining)
            case router: NotFoundRouter =>
                response404(router)
            case ConstantRouter(method, path, value, serde, mediaType) =>
                responseConstant(value, serde, mediaType)
    }

    private def parseHeadLine(buffer: Buffer, lineLength: Int): Unit = {
        val start = buffer.readerOffset
        while (buffer.skipIfNextIs(HttpConstants.SP)) {}
        currentRouter = routerMatcher.choice(buffer)
        val routerContext = routerMatcher.context

        currentRouter match
            case controllerRouter: ControllerRouter =>
                val requestFactory = controllerRouter.requestFactory
                val request        = requestFactory.createHttpRequest()
                currentRequest = request
                request.setRouter(controllerRouter)
                request.setMethod(routerContext.method)
                request.setPath(routerContext.path)
                if (routerContext.pathVars.nonEmpty) request.setPathVariables(routerContext.pathVars.toMap)
                if (buffer.readableBytes > 0 && buffer.nextIs(HttpConstants.PARAM_START)) {
                    val params = ActorThread.threadMap[String, String]
                    ParameterSerde.parse(buffer, params)
                    request.setParam(params.toMap)
                    params.clear()
                }
            case _ =>

        buffer.readerOffset(start + lineLength + 2)
        parseState = ST_PARSE_HEADERS
    }

    private def setInternalRequest(): Unit = if (currentRequest == null) {
        val routerContext = routerMatcher.context
        val request       = new InternalHttpRequest()
        currentRequest = request
        request.setMethod(routerContext.method)
        request.setRouter(currentRouter)
        request.setPath(routerContext.path)
        request.setRemaining(routerContext.remaining)
    }

    private def writeResponseHeadLine(status: HttpStatus, output: Buffer): Unit = {
        output.writeBytes(HttpVersion.HTTP_1_1.bytes)
        output.writeBytes(status.bytesCRCL)
    }

    private def writeHeader(key: Array[Byte], value: Array[Byte], output: Buffer): Unit = {
        output.writeBytes(key)
        output.writeBytes(HttpConstants.HEADER_SPLITTER)
        output.writeBytes(value)
        output.writeBytes(HttpConstants.HEADER_LINE_END)
    }

    private def writeServerHeader(output: Buffer): Unit = writeHeader(HttpHeader.Key.SERVER, serverName, output)

    private def writeContentLengthPlaceholder(buffer: Buffer): Int = {
        writeHeader(HttpHeader.Key.CONTENT_LENGTH, ServerCodec.CONTENT_LENGTH_PLACEHOLDER, buffer)
        buffer.writerOffset - ServerCodec.CONTENT_LENGTH_PLACEHOLDER.length - 2
    }

    private def parseBody(contentLength: Int, factory: HttpRequestFactory[?, ?], input: Buffer): Unit = {
        val request = currentRequest
        val endIdx  = input.readerOffset + contentLength

        factory.contentSerde match
            case Some(serde) => request.setContent(serde.deserialize(input))
            case None        =>

        input.readerOffset(endIdx)
        ctx.fireChannelRead(request, ctx.channel.generateMessageId)
    }

    private def responseConstant(value: Any, serde: Serde[?], media: MediaType): Unit = {
        val buffer = ctx.outboundAdaptiveBuffer
        writeResponseHeadLine(HttpStatus.OK, buffer)
        writeServerHeader(buffer)
        writeHeader(HttpHeader.Key.CONTENT_TYPE, media.fullName, buffer)
        buffer.writeBytes(dates.get()) // Date: xxx

        val lengthOffset = writeContentLengthPlaceholder(buffer)
        buffer.writeBytes(HttpConstants.HEADER_LINE_END)

        val contentStart = buffer.writerOffset
        serde.serializeAny(value, buffer)
        val contentEnd = buffer.writerOffset

        buffer.setCharSequence(lengthOffset, (contentEnd - contentStart).toString)

        ctx.write(buffer)
    }

    private def responseStatic(root: Path, remaining: String): Unit = {
        val rootFile = root.toFile
        if (rootFile.isFile && remaining == null) {
            responseFile(rootFile)
        } else if (rootFile.isDirectory && remaining != null) {
            val file = new File(rootFile, remaining)
            if (file.exists()) responseFile(file)
            else {
                response404(routerMatcher.`404`)
            }
        } else { // 404
            response404(routerMatcher.`404`)
        }
    }

    private def responseFile(file: File): Unit = {
        val filePathName = file.getAbsolutePath
        val region =
            staticCache.getOrElseUpdate(filePathName, new DefaultFileRegion(file, 0, file.length())).retain

        val buffer = ctx.outboundAdaptiveBuffer
        writeResponseHeadLine(HttpStatus.OK, buffer)
        writeServerHeader(buffer)
        buffer.writeBytes(dates.get())
        writeHeader(HttpHeader.Key.CONTENT_LENGTH, region.countBytes, buffer)

        writeStaticMediaHeader(buffer, filePathName)

        buffer.writeBytes(HttpConstants.HEADER_LINE_END)

        ctx.write(buffer)
        ctx.write(region)
    }

    private def response404(router: NotFoundRouter): Unit = {
        val buffer = ctx.outboundAdaptiveBuffer
        writeResponseHeadLine(HttpStatus.NOT_FOUND, buffer)
        writeServerHeader(buffer)
        buffer.writeBytes(dates.get())

        router.page match
            case Some(path) =>
                val file         = path.toFile
                val filePathName = path.toFile.getAbsolutePath
                val region =
                    staticCache.getOrElseUpdate(filePathName, new DefaultFileRegion(file, 0, file.length())).retain
                writeHeader(HttpHeader.Key.CONTENT_LENGTH, region.countBytes, buffer)
                writeStaticMediaHeader(buffer, filePathName)
                buffer.writeBytes(HttpConstants.HEADER_LINE_END)
                ctx.write(buffer)
                ctx.write(region)
            case None =>
                writeHeader(HttpHeader.Key.CONTENT_LENGTH, NOT_FOUND_CONTENT_LENGTH, buffer)
                writeHeader(HttpHeader.Key.CONTENT_TYPE, MediaType.TEXT_PLAIN.fullName, buffer)
                buffer.writeBytes(HttpConstants.HEADER_LINE_END)
                buffer.writeBytes(NOT_FOUND_CONTENT)
                ctx.write(buffer)
    }

    private def writeStaticMediaHeader(buffer: Buffer, fileName: String): Unit = {
        buffer.writeBytes(HttpHeader.Key.CONTENT_TYPE)
        buffer.writeBytes(HttpConstants.HEADER_SPLITTER)
        val mediaType = MediaType.values.find(m => fileName.endsWith(m.extension)).getOrElse(MediaType.APP_OCTET_STREAM)
        writeHeader(HttpHeader.Key.CONTENT_TYPE, mediaType.fullName, buffer)
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

    override def channelTimeoutEvent(ctx: ChannelHandlerContext, id: Long): Unit = if (id == packetTimeoutId) {
        logger.error(s"Can't receive completed http packet after 1 second, close channel ${ctx.channel}")
        packetTimeoutId = Timer.INVALID_TIMEOUT_REGISTER_ID
        ctx.channel.pipeline.close(ChannelFuture())
    }

}

object ServerCodec {

    /** server name of this http server */
    private val SERVER_NAME: Array[Byte] = "otavia-http".getBytes(StandardCharsets.US_ASCII)

    private val RESPONSE_NORMAL: Array[Byte] =
        """HTTP/1.1 200 OK
          |Server: otavia-http
          |""".stripMargin.getBytes(StandardCharsets.US_ASCII)

    private val CONTENT_LENGTH_PLACEHOLDER = "        ".getBytes(StandardCharsets.US_ASCII)

    private val NOT_FOUND_CONTENT: Array[Byte] = "404 Not Found!".getBytes(StandardCharsets.US_ASCII)
    private val NOT_FOUND_CONTENT_LENGTH: Array[Byte] =
        NOT_FOUND_CONTENT.length.toString.getBytes(StandardCharsets.US_ASCII)

    private val ST_PARSE_HEADLINE: Int = 0
    private val ST_PARSE_HEADERS: Int  = 1
    private val ST_PARSE_BODY: Int     = 2

    private val DEFAULT_HALF_PACKET_TIMEOUT_ENABLE: Boolean = true
    private val HALF_PACKET_TIMEOUT_ENABLE: Boolean =
        SystemPropertyUtil.getBoolean("cc.otavia.http.half.packet.timout.enable", DEFAULT_HALF_PACKET_TIMEOUT_ENABLE)

}
