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

package cc.otavia.http.client

import cc.otavia.buffer.Buffer
import cc.otavia.buffer.pool.AdaptiveBuffer
import cc.otavia.core.cache.ActorThreadLocal
import cc.otavia.core.channel.ChannelHandlerContext
import cc.otavia.core.slf4a.Logger
import cc.otavia.core.stack.ChannelFuture
import cc.otavia.core.timer.Timer
import cc.otavia.handler.codec.ByteToMessageCodec
import cc.otavia.handler.ssl.SslHandshakeCompletion
import cc.otavia.http.*

import java.nio.channels.ClosedChannelException
import java.nio.charset.StandardCharsets
import scala.collection.mutable
import scala.language.unsafeNulls

class ClientCodec(httpVersion: HttpVersion, isSsl: Boolean) extends ByteToMessageCodec {

    import ClientCodec.*

    private var logger: Logger = _

    private var sslHandshakeSuccess: Boolean = false

    private var currenRequest: HttpClientRequest    = _
    private var currentId: Long                     = 0
    private var currentResponse: HttpClientResponse = _
    private var responseContentLength: Int          = -1

    private var contentLengthOffset: Int = 0

    private var packetTimeoutId: Long = Timer.INVALID_TIMEOUT_REGISTER_ID
    private var parseState: Int       = ST_PARSE_HEADLINE

    private def resetCurrent(): Unit = {
        currentId = 0
        currenRequest = null
        currentResponse = null
        parseState = ST_PARSE_HEADLINE
    }

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
                        ctx.channel.pipeline.close(ChannelFuture())
                        val msg = s"Illegal http packet, head line is large than 4096, close channel ${ctx.channel}"
                        ctx.fireChannelExceptionCaught(new IllegalStateException(msg), currentId)
                    }
                }
            }

            if (parseState == ST_PARSE_HEADERS) {
                val headersLength = input.bytesBefore(HttpConstants.HEADERS_END)
                if (headersLength != -1) {
                    parseHeaders(input, headersLength + 2) // plus 2 for \r\n
                    if (currentResponse.headers.contains(HttpHeaderKey.CONTENT_LENGTH)) {
                        parseState = ST_PARSE_BODY
                        responseContentLength =
                            currentResponse.headers(HttpHeaderKey.CONTENT_LENGTH).toString.trim.toInt
                    } else {
                        val request  = currenRequest
                        val response = currentResponse
                        val id       = currentId
                        resetCurrent()
                        ctx.fireChannelRead(response, id)
                    }
                } else continue = false // wait for more data
            }

            if (parseState == ST_PARSE_BODY) {
                val contentLen = responseContentLength
                if (contentLen <= input.readableBytes) {
                    responseContentLength = -1
                    val start    = input.readerOffset
                    val request  = currenRequest
                    val response = currentResponse
                    val id       = currentId
                    resetCurrent()

                    if (request.responseSerde != null) {
                        val content = request.responseSerde.deserializeToAny(input)
                        response.content = content
                    } else {
                        val bytes = new Array[Byte](contentLen)
                        input.readBytes(bytes)
                        response.content = bytes
                    }
                    val consumed = input.readerOffset - start
                    if (consumed < contentLen) input.skipReadableBytes(contentLen - consumed)
                    parseState = ST_PARSE_HEADLINE
                    ctx.fireChannelRead(response, id)
                } else continue = false // wait for more data
            }

        }

    }

    override protected def encode(ctx: ChannelHandlerContext, out: AdaptiveBuffer, msg: AnyRef, msgId: Long): Unit = {
        msg match
            case request: HttpClientRequest =>
                currenRequest = request
                currentId = msgId
                if ((isSsl && sslHandshakeSuccess) || !isSsl) writeRequest(request, out)
            case _ => ctx.write(msg, msgId)
    }

    private def parseHeadLine(buffer: Buffer, lineLength: Int): Unit = {
        currentResponse = new HttpClientResponse()
        while (buffer.skipIfNextIs(HttpConstants.SP)) {}
        // parse http version
        if (buffer.skipIfNextAre(HttpVersion.HTTP_1_1.bytes)) currentResponse.version = HttpVersion.HTTP_1_1
        else if (buffer.skipIfNextAre(HttpVersion.HTTP_1_0.bytes)) currentResponse.version = HttpVersion.HTTP_1_0
        while (buffer.skipIfNextIs(HttpConstants.SP)) {}

        // parse http status
        if (buffer.skipIfNextAre(HttpStatus.OK.bytes)) currentResponse.status = HttpStatus.OK
        else if (buffer.skipIfNextAre(HttpStatus.NOT_CONTENT.bytes)) currentResponse.status = HttpStatus.NOT_CONTENT
        else if (buffer.skipIfNextAre(HttpStatus.PARTIAL_CONTENT.bytes))
            currentResponse.status = HttpStatus.PARTIAL_CONTENT

        while (buffer.skipIfNextIs(HttpConstants.SP)) {}

        buffer.skipIfNextAre(HttpConstants.HEADER_LINE_END)
        parseState = ST_PARSE_HEADERS
    }

    private def parseHeaders(buffer: Buffer, headersLen: Int): Unit = {
        val headers = tempHeaders
        buffer.skipIfNextAre(HttpConstants.HEADER_LINE_END)

        var consumed = 0
        while (consumed < headersLen) {
            val start = buffer.readerOffset

            // read http header key
            val keyLen = buffer.bytesBefore(':'.toByte)
            val key    = HttpHeaderUtil.readKey(buffer, keyLen)

            buffer.skipReadableBytes(1) // skip :
            while (buffer.skipIfNextIs(HttpConstants.SP)) {}

            // read http header value
            val valueLen = buffer.bytesBefore(HttpConstants.HEADER_LINE_END)
            val value    = HttpHeaderUtil.readValue(buffer, valueLen)
            buffer.skipReadableBytes(HttpConstants.HEADER_LINE_END.length)

            headers.put(key, value)

            val acc = buffer.readerOffset - start
            consumed += acc
        }

        buffer.skipReadableBytes(2) // skip last \r\n

        currentResponse.headers = headers.toMap
        headers.clear()
    }

    private def writeRequest(request: HttpClientRequest, out: AdaptiveBuffer): Unit = {
        writeHeadLine(request, out)
        writeHeaders(request, request.body != null, out)
        if (request.body != null) {
            val start = out.writerOffset
            request.bodySerde.serializeAny(request.body, out)
            val contentLen = out.writerOffset - start
            out.setInt(contentLengthOffset, contentLen)
        }
    }

    private def writeHeadLine(request: HttpClientRequest, out: AdaptiveBuffer): Unit = {
        out.writeBytes(request.method.bytes)
        out.writeByte(HttpConstants.SP)
        out.writeCharSequence(request.path, StandardCharsets.US_ASCII)
        if (request.params != null) ParameterSerde.write(out, request.params)
        out.writeByte(HttpConstants.SP)
        out.writeBytes(httpVersion.bytes)
        out.writeBytes(HttpConstants.HEADER_LINE_END)
    }

    private def writeHeaders(request: HttpClientRequest, hasBody: Boolean, out: AdaptiveBuffer): Unit = {
        val headers = request.headers

        for ((key, value) <- headers if key != HttpHeaderKey.CONTENT_TYPE) {
            out.writeBytes(key.getBytes)
            out.writeBytes(HttpConstants.HEADER_SPLITTER)
            out.writeBytes(value.getBytes)
            out.writeBytes(HttpConstants.HEADER_LINE_END)
        }

        if (hasBody) {
            out.writeBytes(HttpHeaderKey.CONTENT_TYPE.getBytes)
            out.writeBytes(HttpConstants.HEADER_SPLITTER)
            out.writeBytes(request.mediaType.fullName)
            out.writeBytes(HttpConstants.HEADER_LINE_END)

            out.writeBytes(HttpHeaderKey.CONTENT_LENGTH.getBytes)
            out.writeBytes(HttpConstants.HEADER_SPLITTER)
            contentLengthOffset = out.writerOffset
            out.writeBytes(HttpConstants.CONTENT_LENGTH_PLACEHOLDER)
            out.writeBytes(HttpConstants.HEADER_LINE_END)
        }

        out.writeBytes(HttpConstants.HEADER_LINE_END)
    }

    override def channelInboundEvent(ctx: ChannelHandlerContext, evt: AnyRef): Unit = {
        evt match
            case SslHandshakeCompletion.SUCCESS =>
                sslHandshakeSuccess = true
                if (currenRequest != null) {
                    writeRequest(currenRequest, ctx.outboundAdaptiveBuffer)
                    ctx.writeAndFlush(ctx.outboundAdaptiveBuffer)
                }
            case _ =>
        ctx.fireChannelInboundEvent(evt)
    }

}

object ClientCodec {

    private val ST_PARSE_HEADLINE: Int = 0
    private val ST_PARSE_HEADERS: Int  = 1
    private val ST_PARSE_BODY: Int     = 2

    private def tempHeaders: mutable.Map[HttpHeaderKey, HttpHeaderValue] = tempHeadersLocal.get()

    private val tempHeadersLocal = new ActorThreadLocal[mutable.Map[HttpHeaderKey, HttpHeaderValue]] {
        override protected def initialValue(): mutable.Map[HttpHeaderKey, HttpHeaderValue] = mutable.Map.empty
    }

}
