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
import cc.otavia.common.SystemPropertyUtil
import cc.otavia.core.cache.{ActorThreadIsolatedObjectPool, ActorThreadLocal}
import cc.otavia.core.channel.ChannelHandlerContext
import cc.otavia.core.slf4a.{Logger, LoggerFactory}
import cc.otavia.handler.codec.ByteToByteCodec

import java.nio.ByteBuffer
import java.util.regex.Pattern
import javax.net.ssl.SSLEngineResult.{HandshakeStatus, Status}
import javax.net.ssl.{SSLEngine, SSLException}
import scala.language.unsafeNulls

class SslHandler(private val engine: SSLEngine, private val startTls: Boolean = false) extends ByteToByteCodec {

    import SslHandler.*

    private var logger: Logger = _

    private var ctx: ChannelHandlerContext    = _
    private var jdkCompatibilityMode: Boolean = true

    private val singleBuffer: Array[ByteBuffer] = new Array[ByteBuffer](1)

    private var packetLength: Int = 0
    private var state: Int        = 0

    private var handshakeSuccess = false

    private var pendingWrite: AdaptiveBuffer = _

    @volatile private var handshakeTimeoutMillis: Long        = 10000
    @volatile private var closeNotifyFlushTimeoutMillis: Long = 3000
    @volatile private var closeNotifyReadTimeoutMillis: Long  = 0L
    @volatile var wrapDataSize: Int                           = MAX_PLAINTEXT_LENGTH

    private def decodeJdkCompatible(ctx: ChannelHandlerContext, in: AdaptiveBuffer, out: AdaptiveBuffer): Unit = {
        while (checkDecodePacket(ctx, in)) {
            val packetLen = this.packetLength
            // Reset the state of this class so we can get the length of the next packet. We assume the entire packet will
            // be consumed by the SSLEngine.
            this.packetLength = 0

            val bytesConsumed = unwrap(ctx, in, packetLen, out)

        }
    }

    private def checkDecodePacket(ctx: ChannelHandlerContext, input: AdaptiveBuffer): Boolean = {
        if (this.packetLength > 0) input.readableBytes >= this.packetLength // wait until the whole packet can be read
        else {
            if (input.readableBytes >= SslUtils.SSL_RECORD_HEADER_LENGTH) {
                this.packetLength = SslUtils.getEncryptedPacketLength(input, input.readerOffset)
                if (this.packetLength == SslUtils.NOT_ENCRYPTED) {
                    // Not an SSL/TLS packet
                    val e = new SSLException("not an SSL/TLS record")
                    input.skipReadableBytes(input.readableBytes)
                    // First fail the handshake promise as we may need to have access to the SSLEngine which may
                    // be released because the user will remove the SslHandler in an exceptionCaught(...) implementation.
                    setHandshakeFailure(ctx, e)
                    ctx.fireChannelExceptionCaught(e)
                    false
                } else if (this.packetLength == SslUtils.NOT_ENOUGH_DATA) {
                    this.packetLength = 0
                    false
                } else if (this.packetLength > input.readableBytes) false
                else true
            } else false
        }
    }

    private def wrap(ctx: ChannelHandlerContext, inUnwarp: Boolean): Unit = {
        val out =
            if (ctx.outboundAdaptiveBuffer.isDirect) DIRECT_CACHE_BYTEBUFFER.get() else HEAP_CACHE_BYTEBUFFER.get()

        var break = false
        while (!break && !ctx.isRemoved) {
            if (pendingWrite != null && pendingWrite.readableBytes > 0) {
                val result = pendingWrite.sslwarp(engine, out)
            } else break = true
        }

        if (out.position() > 0) {
            out.flip()
            ctx.outboundAdaptiveBuffer.writeBytes(out)
            out.clear()
            ctx.write(ctx.outboundAdaptiveBuffer)
            setState(STATE_NEEDS_FLUSH)
        }
    }

    /** This method will not call setHandshakeFailure(ChannelHandlerContext, Throwable, boolean, boolean, boolean) or
     *  setHandshakeFailure(ChannelHandlerContext, Throwable).
     *
     *  @return
     *    true if this method ends on SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING.
     */
    private def wrapNonAppData(ctx: ChannelHandlerContext, inUnwarp: Boolean): Boolean = {
        var break = false
        var ret   = false
        // Only continue to loop if the handler was not removed in the meantime.
        // See https://github.com/netty/netty/issues/5860
        while (!ctx.isRemoved && !break) {
            val in =
                if (ctx.outboundAdaptiveBuffer.isDirect) DIRECT_EMPTY_BYTEBUFFER.get() else HEAP_EMPTY_BYTEBUFFER.get()
            val out =
                if (ctx.outboundAdaptiveBuffer.isDirect) DIRECT_CACHE_BYTEBUFFER.get() else HEAP_CACHE_BYTEBUFFER.get()
            val result = engine.wrap(in, out)
            if (result.bytesProduced() > 0) {
                out.flip()
                ctx.outboundAdaptiveBuffer.writeBytes(out)
                out.clear()
                ctx.write(ctx.outboundAdaptiveBuffer)
                if (inUnwarp) setState(STATE_NEEDS_FLUSH)
            } else if (result.getStatus == Status.BUFFER_OVERFLOW) {
                val msg =
                    s"$SSL_CACHE_BYTEBUFFER_SIZE_KEY value $SSL_CACHE_BYTEBUFFER_SIZE is too small for ssl wrap/unwrap"
                ctx.fireChannelExceptionCaught(new RuntimeException(msg))
            }
            val status = result.getHandshakeStatus
            logger.info(s"wrapNonAppData $result")
            status match
                case HandshakeStatus.FINISHED =>
                    // We may be here because we read data and discovered the remote peer initiated a renegotiation
                    // and this write is to complete the new handshake. The user may have previously done a
                    // writeAndFlush which wasn't able to wrap data due to needing the pending handshake, so we
                    // attempt to wrap application data here if any is pending.
                    if (setHandshakeSuccess() && inUnwarp && pendingWrite != null && pendingWrite.readableBytes > 0) {
                        wrap(ctx, true)
                    }
                    ret = false
                case HandshakeStatus.NEED_TASK =>
                    runDelegatedTasks(inUnwarp)
                case HandshakeStatus.NEED_UNWRAP =>
                    if (inUnwarp || unwrapNonAppData(ctx) <= 0) {
                        // If we asked for a wrap, the engine requested an unwrap, and we are in unwrap there is
                        // no use in trying to call wrap again because we have already attempted (or will after we
                        // return) to feed more data to the engine.
                        ret = false
                        break = true
                    }
                case HandshakeStatus.NEED_WRAP =>
                case HandshakeStatus.NOT_HANDSHAKING =>
                    if (setHandshakeSuccess() && inUnwarp && ctx.outboundAdaptiveBuffer.readableBytes > 0) {
                        wrap(ctx, true)
                    }
                    // Workaround for TLS False Start problem reported at:
                    // https://github.com/netty/netty/issues/1108#issuecomment-14266970
                    if (!inUnwarp) unwrapNonAppData(ctx)
                    ret = true
                    break = true
                case _ =>
                    val exception = new IllegalStateException("Unknown handshake status: " + result.getHandshakeStatus)
                    ctx.fireChannelExceptionCaught(exception)

            // Check if did not produce any bytes and if so break out of the loop, but only if we did not process
            // a task as last action. It's fine to not produce any data as part of executing a task.
            if (!break && result.bytesProduced() == 0 && status != HandshakeStatus.NEED_TASK) {
                break = true
            }

            // It should not consume empty buffers when it is not handshaking
            // Fix for Android, where it was encrypting empty buffers even when not handshaking
            if (!break && result.bytesConsumed() == 0 && result.getHandshakeStatus == HandshakeStatus.NOT_HANDSHAKING) {
                break = true
            }
        }
        ret
    }

    /** Calls SSLEngine.unwrap(ByteBuffer, ByteBuffer) with a null buffer to handle handshakes, etc. */
    private def unwrapNonAppData(ctx: ChannelHandlerContext): Int =
        unwrap(ctx, ctx.inboundAdaptiveBuffer, 0, ctx.outboundAdaptiveBuffer)

    /** Unwraps inbound SSL records. */
    private def unwrap(ctx: ChannelHandlerContext, packet: AdaptiveBuffer, length: Int, out: AdaptiveBuffer): Int = {
        val originalLength = length
        var wrapLater      = false
        var notifyClosure  = false
        var executedRead   = false
        val decodeOut =
            if (out.isDirect) DIRECT_CACHE_BYTEBUFFER.get() else HEAP_CACHE_BYTEBUFFER.get()

        val empty = HEAP_EMPTY_BYTEBUFFER.get()

        var len = length

        var continue = true
        while (continue) {
            val result = if (len > 0) packet.sslunwarp(engine, len, decodeOut) else engine.unwrap(empty(0), decodeOut)
            logger.info(s"unwrap $result")
            val status          = result.getStatus
            val handshakeStatus = result.getHandshakeStatus
            val produced        = result.bytesProduced
            val consumed        = result.bytesConsumed

            len -= consumed

            // The expected sequence of events is:
            // 1. Notify of handshake success
            // 2. fireChannelRead for unwrapped data
            if (handshakeStatus == HandshakeStatus.FINISHED || handshakeStatus == HandshakeStatus.NOT_HANDSHAKING) {
                wrapLater = wrapLater | ((if (decodeOut.position() > 0) setHandshakeSuccessUnwrapMarkReentry()
                                          else setHandshakeSuccess()) || handshakeStatus == HandshakeStatus.FINISHED)
            }

            // Dispatch decoded data after we have notified of handshake success. If this method has been invoked
            // in a re-entry fashion we execute a task on the executor queue to process after the stack unwinds
            // to preserve order of events.
            if (decodeOut.position() > 0) {
                setState(STATE_FIRE_CHANNEL_READ)
                decodeOut.flip()
                out.writeBytes(decodeOut)
            }

            if (handshakeStatus == HandshakeStatus.NEED_TASK) {
                runDelegatedTasks(true)
            } else if (handshakeStatus == HandshakeStatus.NEED_WRAP) {
                // If the wrap operation transitions the status to NOT_HANDSHAKING and there is no more data to
                // unwrap then the next call to unwrap will not produce any data. We can avoid the potentially
                // costly unwrap operation and break out of the loop.
                if (wrapNonAppData(ctx, true) && len == 0) continue = false
            }

            if (
              status == Status.BUFFER_OVERFLOW ||
              // If we processed NEED_TASK we should try again even we did not consume or produce anything.
              handshakeStatus != HandshakeStatus.NEED_TASK && (consumed == 0 && produced == 0 ||
                  (len == 0 && handshakeStatus == HandshakeStatus.NOT_HANDSHAKING))
            ) {
                continue = false
            }

            decodeOut.clear()
        }

        if (wrapLater) wrap(ctx, true)

        originalLength - len
    }

    private def setHandshakeSuccessUnwrapMarkReentry(): Boolean = {
        // setHandshakeSuccess calls out to external methods which may trigger re-entry. We need to preserve ordering of
        // fireChannelRead for decodeOut relative to re-entry data.
        val setReentryState = !isStateSet(STATE_UNWRAP_REENTRY)
        if (setReentryState) setState(STATE_UNWRAP_REENTRY)

        try { setHandshakeSuccess() }
        finally { if (setReentryState) clearState(STATE_UNWRAP_REENTRY) }
    }

    override protected def decode(ctx: ChannelHandlerContext, input: AdaptiveBuffer, output: AdaptiveBuffer): Unit = {
        if (!isStateSet(STATE_PROCESS_TASK)) decodeJdkCompatible(ctx, input, output)
    }

    override def channelReadComplete(ctx: ChannelHandlerContext): Unit = {
        if (isStateSet(STATE_NEEDS_FLUSH)) forceFlush(ctx)
    }

    override protected def encode(ctx: ChannelHandlerContext, input: AdaptiveBuffer, output: AdaptiveBuffer): Unit =
        pendingWrite = input

    override def flush(ctx: ChannelHandlerContext): Unit =
        if (handshakeSuccess) {
            wrap(ctx, false)
            forceFlush(ctx)
        } else ctx.flush()

    /** Will run the delegated task directly calling Runnable.run() and return true */
    private def runDelegatedTasks(inUnwrap: Boolean): Boolean = {
        var continue = true
        while (continue) {
            val task = engine.getDelegatedTask
            if (task == null) continue = false
            else {
                setState(STATE_PROCESS_TASK)
                try { task.run() }
                finally clearState(STATE_PROCESS_TASK)
            }
        }
        true
    }

    /** Notify all the handshake futures about the successfully handshake
     *  @return
     *    true if handshakePromise was set successfully and a [[SslHandshakeCompletion.SUCCESS]] was fired. false
     *    otherwise.
     */
    private def setHandshakeSuccess(): Boolean = {
        if (!handshakeSuccess) {
            handshakeSuccess = true
            ctx.fireChannelInboundEvent(SslHandshakeCompletion.SUCCESS)
            true
        } else false
    }

    /** Notify all the handshake futures about the failure during the handshake. */
    private def setHandshakeFailure(ctx: ChannelHandlerContext, cause: Throwable): Unit =
        setHandshakeFailure(ctx, cause, true, true, false)

    /** Notify all the handshake futures about the failure during the handshake. */
    private def setHandshakeFailure(
        ctx: ChannelHandlerContext,
        cause: Throwable,
        closeInbound: Boolean,
        notify: Boolean,
        alwaysFlushAndClose: Boolean
    ): Unit = {
        ???
    }

    override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
        this.ctx = ctx
        logger = LoggerFactory.getLogger(getClass, ctx.system)
        // TODO
    }

    private def startHandshakeProcessing(flushAtEnd: Boolean): Unit = {
        if (!isStateSet(STATE_HANDSHAKE_STARTED)) {
            setState(STATE_HANDSHAKE_STARTED)
            if (engine.getUseClientMode) {
                logger.info("Client side starting TLS handshake")
                // Begin the initial handshake.
                // channelActive() event has been fired already, which means this.channelActive() will
                // not be invoked. We have to initialize here instead.
                handshake(flushAtEnd)
            }
            applyHandshakeTimeout()
        } else if (isStateSet(STATE_NEEDS_FLUSH)) forceFlush(ctx)
    }

    /** Performs TLS (re)negotiation.
     *  @param flushAtEnd
     *    Set to true if the outbound buffer should be flushed (written to the network) at the end. Set to false if the
     *    handshake will be flushed later, e.g. as part of TCP Fast Open connect.
     */
    private def handshake(flushAtEnd: Boolean): Unit = {
        if (engine.getHandshakeStatus != HandshakeStatus.NOT_HANDSHAKING) {
            // Not all SSLEngine implementations support calling beginHandshake multiple times while a handshake
            // is in progress. See https://github.com/netty/netty/issues/4718.
            // return
        } else {
            // Begin handshake
            try {
                engine.beginHandshake()
                logger.info("Send ClientHello")
                wrapNonAppData(ctx, false)
            } catch {
                case e: Throwable => // TODO
                    throw e
            } finally {
                if (flushAtEnd) forceFlush(ctx)
            }
        }
    }

    private def applyHandshakeTimeout(): Unit = {
        // TODO
    }

    private def forceFlush(ctx: ChannelHandlerContext): Unit = {
        logger.info("forceFlush")
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

    private val HEAP_EMPTY_BYTEBUFFER = new ActorThreadLocal[Array[ByteBuffer]] {
        override protected def initialValue(): Array[ByteBuffer] = Array(ByteBuffer.allocate(0))
    }

    private val DIRECT_EMPTY_BYTEBUFFER = new ActorThreadLocal[Array[ByteBuffer]] {
        override protected def initialValue(): Array[ByteBuffer] = Array(ByteBuffer.allocateDirect(0))
    }

    private val SSL_CACHE_BYTEBUFFER_SIZE_KEY = "cc.otavia.handler.ssl.cacheSize"
    private val SSL_CACHE_BYTEBUFFER_SIZE     = SystemPropertyUtil.getInt(SSL_CACHE_BYTEBUFFER_SIZE_KEY, 4096 * 5)

    private val HEAP_CACHE_BYTEBUFFER = new ActorThreadLocal[ByteBuffer] {
        override protected def initialValue(): ByteBuffer = ByteBuffer.allocate(SSL_CACHE_BYTEBUFFER_SIZE)
    }

    private val DIRECT_CACHE_BYTEBUFFER = new ActorThreadLocal[ByteBuffer] {
        override protected def initialValue(): ByteBuffer = ByteBuffer.allocateDirect(SSL_CACHE_BYTEBUFFER_SIZE)
    }

}
