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
import cc.otavia.core.channel.ChannelHandlerContext
import cc.otavia.core.slf4a.{Logger, LoggerFactory}
import cc.otavia.core.timer.TimeoutTrigger
import cc.otavia.handler.codec.ByteToMessageCodec
import cc.otavia.http.server.*
import cc.otavia.http.{HttpConstants, HttpHeader}

import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import scala.language.unsafeNulls

class ServerCodec(routerMatcher: RouterMatcher) extends ByteToMessageCodec {

    private var logger: Logger = _

    private var date: Array[Byte]   = _ // TODO: use threadLocal cache
    private var dateTimeoutId: Long = 0

    // decode http request
    override protected def decode(ctx: ChannelHandlerContext, input: AdaptiveBuffer): Unit = {
        while (checkPacket(input)) {          // check packet completed
            routerMatcher.choice(input) match // find the router
                case controllerRouter: ControllerRouter   =>
                case staticFilesRouter: StaticFilesRouter =>
                case notFoundRouter: NotFoundRouter       => // 404
                case plainTextRouter: PlainTextRouter     =>
        }
    }

    // encode http response
    override protected def encode(ctx: ChannelHandlerContext, output: AdaptiveBuffer, msg: AnyRef, msgId: Long): Unit =
        ???

    private def checkPacket(input: AdaptiveBuffer): Boolean = {
        while (input.skipIfNext(HttpConstants.SP)) {}
        val headerEndIdx     = input.bytesBefore(HttpConstants.HEADERS_END)
        val contentLengthIdx = input.bytesBefore(HttpHeader.Key.CONTENT_LENGTH)
        ???
    }

    override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
        super.handlerAdded(ctx)
        this.logger = LoggerFactory.getLogger(getClass, ctx.system)
        this.dateTimeoutId = ctx.timer.registerChannelTimeout(TimeoutTrigger.DelayPeriod(1000, 1000), ctx.channel)
    }

    override def handlerRemoved(ctx: ChannelHandlerContext): Unit = {
        ctx.timer.cancelTimerTask(this.dateTimeoutId)
        super.handlerRemoved(ctx)
    }

    override def channelTimeoutEvent(ctx: ChannelHandlerContext, id: Long): Unit = if (id == this.dateTimeoutId) {
        // update date
        val time = LocalDateTime.now()
        logger.info("date updating")
    } else ctx.fireChannelTimeoutEvent(id)

}

object ServerCodec {

    /** server name of this http server */
    private val SERVER_NAME: Array[Byte] = "otavia-http".getBytes(StandardCharsets.US_ASCII)

}
