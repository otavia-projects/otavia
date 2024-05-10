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

package cc.otavia.handler.ssl

import cc.otavia.buffer.pool.AdaptiveBuffer
import cc.otavia.core.channel.ChannelHandlerContext
import cc.otavia.handler.codec.ByteToByteCodec

import java.nio.ByteBuffer
import java.util.regex.Pattern
import javax.net.ssl.SSLEngine
import scala.language.unsafeNulls

class SslHandler(private val engine: SSLEngine, private val startTls: Boolean = false) extends ByteToByteCodec {

    import SslHandler.*

    private var ctx: ChannelHandlerContext    = _
    private var jdkCompatibilityMode: Boolean = _

    private val singleBuffer: Array[ByteBuffer] = new Array[ByteBuffer](1)

    private var packetLength: Int = -1
    private var state: Int        = 0

    @volatile private var handshakeTimeoutMillis: Long        = 10000
    @volatile private var closeNotifyFlushTimeoutMillis: Long = 3000
    @volatile private var closeNotifyReadTimeoutMillis: Long  = 0L
    @volatile var wrapDataSize: Int                           = MAX_PLAINTEXT_LENGTH

    override protected def decode(ctx: ChannelHandlerContext, input: AdaptiveBuffer, output: AdaptiveBuffer): Unit = {}

    override protected def encode(ctx: ChannelHandlerContext, input: AdaptiveBuffer, output: AdaptiveBuffer): Unit = {}

    private def startHandshakeProcessing(flushAtEnd: Boolean): Unit = {
        if (!isStateSet(STATE_HANDSHAKE_STARTED)) {
            setState(STATE_HANDSHAKE_STARTED)
            if (engine.getUseClientMode) {
                // Begin the initial handshake.
                // channelActive() event has been fired already, which means this.channelActive() will
                // not be invoked. We have to initialize here instead.
                handshake(flushAtEnd)
            }
            applyHandshakeTimeout()
        } else if (isStateSet(STATE_NEEDS_FLUSH)) forceFlush(ctx)
    }

    private def handshake(flushAtEnd: Boolean): Unit = {
        ???
    }

    private def applyHandshakeTimeout(): Unit = {
        ???
    }

    private def forceFlush(ctx: ChannelHandlerContext): Unit = {
        clearState(STATE_NEEDS_FLUSH)
        ctx.flush()
    }

    /** Issues an initial TLS handshake once connected when used in client-mode */
    override def channelActive(ctx: ChannelHandlerContext): Unit = {
        if (!startTls) startHandshakeProcessing(true)
        ctx.fireChannelActive()
    }

    private def isStateSet(bit: Int): Boolean = (state & bit) == bit
    private def setState(bit: Int): Unit      = state = state | bit
    private def clearState(bit: Int): Unit    = state = state & ~bit

}

object SslHandler {

    private val IGNORABLE_CLASS_IN_STACK = Pattern.compile("^.*(?:Socket|Datagram|Sctp|Udt)Channel.*$")
    private val IGNORABLE_ERROR_MESSAGE =
        Pattern.compile("^.*(?:connection.*(?:reset|closed|abort|broken)|broken.*pipe).*$", Pattern.CASE_INSENSITIVE)

    private val STATE_SENT_FIRST_MESSAGE: Int       = 1
    private val STATE_FLUSHED_BEFORE_HANDSHAKE: Int = 1 << 1
    private val STATE_READ_DURING_HANDSHAKE: Int    = 1 << 2
    private val STATE_HANDSHAKE_STARTED: Int        = 1 << 3

    /** Set by wrap*() methods when something is produced. channelReadComplete(ChannelHandlerContext) will check this
     *  flag, clear it, and call ctx.flush().
     */
    private val STATE_NEEDS_FLUSH: Int     = 1 << 4
    private val STATE_OUTBOUND_CLOSED: Int = 1 << 5
    private val STATE_CLOSE_NOTIFY: Int    = 1 << 6
    private val STATE_PROCESS_TASK: Int    = 1 << 7

    /** This flag is used to determine if we need to call ChannelHandlerContext.read() to consume more data when
     *  ChannelConfig.isAutoRead() is false.
     */
    private val STATE_FIRE_CHANNEL_READ = 1 << 8
    private val STATE_UNWRAP_REENTRY    = 1 << 9

    /** <a href="https://tools.ietf.org/html/rfc5246#section-6.2">exp(2,14)</a> which is the maximum sized plaintext
     *  chunk allowed by the TLS RFC.
     */
    private val MAX_PLAINTEXT_LENGTH = 16 * 1024

}
