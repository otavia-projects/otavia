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

package io.otavia.core.channel

import io.netty5.util.Resource
import io.netty5.util.internal.StringUtil
import io.otavia.core.actor.ChannelsActor
import io.otavia.core.buffer.AdaptiveBuffer
import io.otavia.core.channel.OtaviaChannelHandlerContext.*
import io.otavia.core.channel.internal.ChannelHandlerMask
import io.otavia.core.channel.internal.ChannelHandlerMask.*
import io.otavia.core.channel.message.ReadPlan
import io.otavia.core.slf4a.Logger
import io.otavia.core.stack.ChannelFuture
import io.otavia.core.util.ThrowableUtil

import java.net.SocketAddress
import java.nio.file.attribute.FileAttribute
import java.nio.file.{OpenOption, Path}
import scala.util.Try

final class OtaviaChannelHandlerContext(
    override val pipeline: OtaviaChannelPipeline,
    override val name: String,
    override val handler: ChannelHandler
) extends ChannelHandlerContext {

    protected val logger: Logger = Logger.getLogger(getClass, pipeline.system)

    private[channel] val executionMask = mask(handler.getClass)

    private var currentPendingBytes: Long = 0

    private var handlerState: Int = INIT
    private var removed: Boolean  = _

    private var idx: Int = -1

    protected[channel] var next: OtaviaChannelHandlerContext = _
    protected[channel] var prev: OtaviaChannelHandlerContext = _

    private var inboundAdaptive: AdaptiveBuffer  = _
    private var outboundAdaptive: AdaptiveBuffer = _

    def setInboundAdaptiveBuffer(inboundAdaptiveBuffer: AdaptiveBuffer): Unit =
        inboundAdaptive = inboundAdaptiveBuffer

    def setOutboundAdaptiveBuffer(outboundAdaptiveBuffer: AdaptiveBuffer): Unit =
        outboundAdaptive = outboundAdaptiveBuffer

    def index: Int = idx

    def setIndex(id: Int): Unit = idx = id

    override def isBufferHandlerContext: Boolean = handler.isBufferHandler

    override def inboundAdaptiveBuffer: AdaptiveBuffer =
        if (isBufferHandlerContext) inboundAdaptive else throw new UnsupportedOperationException()

    override def outboundAdaptiveBuffer: AdaptiveBuffer =
        if (isBufferHandlerContext) outboundAdaptive else throw new UnsupportedOperationException()

    override def nextInboundAdaptiveBuffer: AdaptiveBuffer = {
        val ctx = findContextInbound(ChannelHandlerMask.MASK_CHANNEL_READ)
        ctx.inboundAdaptiveBuffer
    }

    override def nextOutboundAdaptiveBuffer: AdaptiveBuffer = {
        val ctx = findContextOutbound(ChannelHandlerMask.MASK_WRITE)
        ctx.outboundAdaptiveBuffer
    }

    private def handleOutboundHandlerException(cause: Throwable, closeDidThrow: Boolean): IllegalStateException = {
        val msg = s"$handler threw an exception while handling an outbound event. This is most likely a bug"
        logger.warn(s"$msg. This is most likely a bug, closing the channel.", cause)
        if (closeDidThrow) close(ChannelFuture()) else channel.close(ChannelFuture()) // ignore close future
        new IllegalStateException(msg, cause)
    }

    private def findContextInbound(mask: Int): OtaviaChannelHandlerContext = if (!removed) {
        var ctx = this
        while {
            ctx = ctx.next
            (ctx.executionMask & mask) == 0
        } do ()
        ctx
    } else {
        throw IllegalStateException(
          s"handler $handler has been removed from pipeline, " +
              s"so can't call the next handler in pipeline! This is most likely a bug!"
        )
    }

    private def findContextOutbound(mask: Int): OtaviaChannelHandlerContext = if (!removed) {
        var ctx = this
        while {
            ctx = ctx.prev
            (ctx.executionMask & mask) == 0
        } do ()
        ctx
    } else {
        throw IllegalStateException(
          s"handler $handler has been removed from pipeline, " +
              s"so can't call the next handler in pipeline! This is most likely a bug!"
        )
    }

    def setAddComplete(): Boolean = if (handlerState == INIT) {
        handlerState = ADD_COMPLETE
        true
    } else false

    @throws[Exception]
    def callHandlerAdded(): Unit = if (setAddComplete()) {
        // We must call setAddComplete before calling handlerAdded. Otherwise if the handlerAdded method generates
        // any pipeline events ctx.handler() will miss them because the state will not allow it.
        handler.handlerAdded(this)
        if (OtaviaChannelHandlerContext.handlesPendingOutboundBytes(executionMask)) {
            val pending = pendingOutboundBytes()
            currentPendingBytes = -1
            if (pending > 0) pipeline.incrementPendingOutboundBytes(pending)
        }
    }

    def callHandlerRemoved(): Unit = try {
        if (handlerState == ADD_COMPLETE) {
            handlerState = REMOVE_STARTED
            try { handler.handlerRemoved(this) }
            finally {
                if (handlesPendingOutboundBytes(executionMask)) {
                    val pending = pendingOutboundBytes()
                    currentPendingBytes = -1
                    if (pending > 0) pipeline.decrementPendingOutboundBytes(pending)
                }
            }
        }
    } finally {
        handlerState = REMOVE_COMPLETE
        removed = true
    }

    /** Return `true` if the [[ChannelHandler]] which belongs to this context was removed from the [[ChannelPipeline]].
     */
    override def isRemoved: Boolean = removed

    def remove(relink: Boolean): Unit = {
        assert(handlerState == REMOVE_COMPLETE)
        if (relink) {
            val prev = this.prev
            val next = this.next
            // TODO
        }
    }

    override def fireChannelRegistered(): this.type = {
        val ctx = findContextInbound(ChannelHandlerMask.MASK_CHANNEL_REGISTERED)
        ctx.invokeChannelRegistered()
        this
    }

    private[channel] def invokeChannelRegistered(): Unit = try {
        if (saveCurrentPendingBytesIfNeededInbound()) handler.channelRegistered(this)
    } catch {
        case t: Throwable => invokeChannelExceptionCaught(t)
    } finally updatePendingBytesIfNeeded()

    override def fireChannelUnregistered(): this.type = {
        val ctx = findContextInbound(ChannelHandlerMask.MASK_CHANNEL_UNREGISTERED)
        ctx.invokeChannelUnregistered()
        this
    }

    private[channel] def invokeChannelUnregistered(): Unit = try {
        if (saveCurrentPendingBytesIfNeededInbound()) handler.channelUnregistered(this)
    } catch {
        case t: Throwable => invokeChannelExceptionCaught(t)
    } finally updatePendingBytesIfNeeded()

    override def fireChannelActive(): this.type = {
        val ctx = findContextInbound(ChannelHandlerMask.MASK_CHANNEL_ACTIVE)
        ctx.invokeChannelActive()
        this
    }

    private[channel] def invokeChannelActive(): Unit = try {
        if (saveCurrentPendingBytesIfNeededInbound()) handler.channelActive(this)
    } catch {
        case t: Throwable => invokeChannelExceptionCaught(t)
    } finally updatePendingBytesIfNeeded()

    override def fireChannelInactive(): this.type = {
        val ctx = findContextInbound(ChannelHandlerMask.MASK_CHANNEL_INACTIVE)
        ctx.invokeChannelInactive()
        this
    }

    private[channel] def invokeChannelInactive(): Unit = try {
        if (saveCurrentPendingBytesIfNeededInbound()) handler.channelInactive(this)
    } catch {
        case t: Throwable => invokeChannelExceptionCaught(t)
    } finally updatePendingBytesIfNeeded()

    override def fireChannelShutdown(direction: ChannelShutdownDirection): this.type = {
        val ctx = findContextInbound(ChannelHandlerMask.MASK_CHANNEL_SHUTDOWN)
        ctx.invokeChannelShutdown(direction)
        this
    }

    private[channel] def invokeChannelShutdown(direction: ChannelShutdownDirection): Unit = try {
        if (saveCurrentPendingBytesIfNeededInbound()) handler.channelShutdown(this, direction)
    } catch {
        case t: Throwable => invokeChannelExceptionCaught(t)
    } finally updatePendingBytesIfNeeded()

    override def fireChannelExceptionCaught(cause: Throwable): this.type = {
        val ctx = findContextInbound(ChannelHandlerMask.MASK_CHANNEL_EXCEPTION_CAUGHT)
        ctx.invokeChannelExceptionCaught(cause)
        this
    }

    def invokeChannelExceptionCaught(cause: Throwable): Unit = if (saveCurrentPendingBytesIfNeededInbound()) {
        try { handler.channelExceptionCaught(this, cause) }
        catch {
            case error: Throwable =>
                logger.debug(
                  s"An exception ${ThrowableUtil.stackTraceToString(error)} was thrown by a user handler's " +
                      "exceptionCaught() method while handling the following exception:",
                  cause
                )
                logger.warn(
                  s"An exception '$error' [enable DEBUG level for full stacktrace] "
                      + "was thrown by a user handler's exceptionCaught() "
                      + "method while handling the following exception:",
                  cause
                )
        } finally updatePendingBytesIfNeeded()
    }

    override def fireChannelInboundEvent(evt: AnyRef): this.type = {
        val ctx = findContextInbound(ChannelHandlerMask.MASK_CHANNEL_INBOUND_EVENT)
        ctx.invokeChannelInboundEvent(evt)
        this
    }

    private[channel] def invokeChannelInboundEvent(event: AnyRef): Unit = try {
        if (saveCurrentPendingBytesIfNeededInbound()) handler.channelInboundEvent(this, event)
        else Resource.dispose(event)
    } catch {
        case t: Throwable => invokeChannelExceptionCaught(t)
    } finally updatePendingBytesIfNeeded()

    override def fireChannelTimeoutEvent(id: Long): this.type = {
        val ctx = findContextInbound(ChannelHandlerMask.MASK_CHANNEL_TIMEOUT_EVENT)
        ctx.invokeChannelTimeoutEvent(id)
        this
    }

    private[channel] def invokeChannelTimeoutEvent(id: Long): Unit = {
        try handler.channelTimeoutEvent(this, id)
        catch {
            case t: Throwable => invokeChannelExceptionCaught(t)
        }
    }

    override def fireChannelRead(msg: AnyRef): this.type = {
        val ctx = findContextInbound(ChannelHandlerMask.MASK_CHANNEL_READ)
        ctx.invokeChannelRead(msg)
        this
    }

    private[channel] def invokeChannelRead(msg: AnyRef): Unit = if (saveCurrentPendingBytesIfNeededInbound()) {
        try handler.channelRead(this, msg)
        catch {
            case t: Throwable => invokeChannelExceptionCaught(t)
        } finally updatePendingBytesIfNeeded()
    } else Resource.dispose(msg)

    override def fireChannelRead(msg: AnyRef, msgId: Long): this.type = {
        val ctx = findContextInbound(ChannelHandlerMask.MASK_CHANNEL_READ_ID)
        ctx.invokeChannelRead(msg, msgId)
        this
    }

    private[channel] def invokeChannelRead(msg: AnyRef, id: Long): Unit =
        if (saveCurrentPendingBytesIfNeededInbound()) {
            try handler.channelRead(this, msg, id)
            catch { case t: Throwable => invokeChannelExceptionCaught(t) }
            finally updatePendingBytesIfNeeded()
        } else Resource.dispose(msg)

    override def fireChannelReadComplete(): this.type = {
        val ctx = findContextInbound(ChannelHandlerMask.MASK_CHANNEL_READ_COMPLETE)
        ctx.invokeChannelReadComplete()
        this
    }

    private[channel] def invokeChannelReadComplete(): Unit = try {
        if (saveCurrentPendingBytesIfNeededInbound()) handler.channelReadComplete(this)
    } catch {
        case t: Throwable => invokeChannelExceptionCaught(t)
    } finally updatePendingBytesIfNeeded()

    override def fireChannelWritabilityChanged(): this.type = {
        val ctx = findContextInbound(ChannelHandlerMask.MASK_CHANNEL_WRITABILITY_CHANGED)
        ctx.invokeChannelWritabilityChanged()
        this
    }

    def invokeChannelWritabilityChanged(): Unit = try {
        if (saveCurrentPendingBytesIfNeededInbound()) handler.channelWritabilityChanged(this)
    } catch {
        case t: Throwable => invokeChannelExceptionCaught(t)
    } finally updatePendingBytesIfNeeded()

    override def read(readPlan: ReadPlan): this.type = {
        val ctx = findContextOutbound(ChannelHandlerMask.MASK_READ)
        ctx.invokeRead(readPlan)
        this
    }

    private def invokeRead(allocator: ReadPlan): Unit = try {
        if (saveCurrentPendingBytesIfNeededInbound()) handler.read(this, allocator)
    } catch {
        case t: Throwable => handleOutboundHandlerException(t, false)
    } finally updatePendingBytesIfNeeded()

    override def read(): this.type = {
        val ctx = findContextOutbound(ChannelHandlerMask.MASK_READ)
        ctx.invokeRead(OtaviaChannelPipeline.DEFAULT_READ_BUFFER_ALLOCATOR)
        this
    }

    override def flush(): this.type = {
        val ctx = findContextOutbound(ChannelHandlerMask.MASK_FLUSH)
        ctx.invokeFlush()
        this
    }

    private def invokeFlush(): Unit = try {
        if (saveCurrentPendingBytesIfNeededInbound()) handler.flush(this)
    } catch {
        case t: Throwable => handleOutboundHandlerException(t, false)
    } finally updatePendingBytesIfNeeded()

    override def bind(local: SocketAddress, future: ChannelFuture): ChannelFuture = {
        try {
            val nextCtx = findContextOutbound(ChannelHandlerMask.MASK_BIND)
            nextCtx.invokeBind(local, future)
        } catch {
            case e: Throwable => future.promise.setFailure(e)
        }

        future
    }

    private[channel] def invokeBind(local: SocketAddress, future: ChannelFuture): Unit = {
        saveCurrentPendingBytesIfNeededOutbound() match
            case Some(cause) => future.promise.setFailure(cause)
            case None =>
                try {
                    handler.bind(this, local, future)
                } catch {
                    case t: Throwable => future.promise.setFailure(handleOutboundHandlerException(t, false))
                } finally updatePendingBytesIfNeeded()
    }

    override def connect(remote: SocketAddress, local: Option[SocketAddress], future: ChannelFuture): ChannelFuture = {
        try {
            val nextCtx = findContextOutbound(ChannelHandlerMask.MASK_CONNECT)
            nextCtx.invokeConnect(remote, local, future)
        } catch {
            case e: Throwable => future.promise.setFailure(e)
        }
        future
    }

    private def invokeConnect(remote: SocketAddress, local: Option[SocketAddress], future: ChannelFuture): Unit = {
        saveCurrentPendingBytesIfNeededOutbound() match
            case Some(cause) => future.promise.setFailure(cause)
            case None =>
                try {
                    handler.connect(this, remote, local, future)
                } catch {
                    case t: Throwable => future.promise.setFailure(handleOutboundHandlerException(t, false))
                } finally updatePendingBytesIfNeeded()
    }

    override def open(
        path: Path,
        options: Seq[OpenOption],
        attrs: Seq[FileAttribute[?]],
        future: ChannelFuture
    ): ChannelFuture = {
        try {
            val nextCtx = findContextOutbound(ChannelHandlerMask.MASK_OPEN)
            nextCtx.invokeOpen(path, options, attrs, future)
        } catch {
            case e: Throwable => future.promise.setFailure(e)
        }
        future
    }

    private def invokeOpen(
        path: Path,
        options: Seq[OpenOption],
        attrs: Seq[FileAttribute[?]],
        future: ChannelFuture
    ): Unit = {
        saveCurrentPendingBytesIfNeededOutbound() match
            case Some(cause) => future.promise.setFailure(cause)
            case None =>
                try {
                    handler.open(this, path, options, attrs, future)
                } catch {
                    case t: Throwable => future.promise.setFailure(handleOutboundHandlerException(t, false))
                } finally updatePendingBytesIfNeeded()
    }

    override def disconnect(future: ChannelFuture): ChannelFuture = {
        val abstractChannel = channel.asInstanceOf[AbstractNetChannel[?, ?]]
        if (!abstractChannel.supportingDisconnect) close(future)
        else {
            try {
                val nextCtx = findContextOutbound(ChannelHandlerMask.MASK_DISCONNECT)
                nextCtx.invokeDisconnect(future)
            } catch {
                case e: Throwable => future.promise.setFailure(e)
            }
            future
        }
    }

    private def invokeDisconnect(future: ChannelFuture): Unit = {
        saveCurrentPendingBytesIfNeededOutbound() match
            case Some(cause) => future.promise.setFailure(cause)
            case None =>
                try {
                    handler.disconnect(this, future)
                } catch {
                    case t: Throwable => future.promise.setFailure(handleOutboundHandlerException(t, false))
                } finally updatePendingBytesIfNeeded()
    }

    override def close(future: ChannelFuture): ChannelFuture = {
        try {
            val ctx = findContextOutbound(ChannelHandlerMask.MASK_CLOSE)
            ctx.invokeClose(future)
        } catch {
            case e: Throwable => future.promise.setFailure(e)
        }
        future
    }

    private def invokeClose(future: ChannelFuture): Unit = {
        saveCurrentPendingBytesIfNeededOutbound() match
            case Some(value) => future.promise.setFailure(value)
            case None =>
                try {
                    handler.close(this, future)
                } catch {
                    case t: Throwable => future.promise.setFailure(handleOutboundHandlerException(t, false))
                } finally updatePendingBytesIfNeeded()
    }

    override def shutdown(direction: ChannelShutdownDirection, future: ChannelFuture): ChannelFuture = {
        try {
            val ctx = findContextOutbound(ChannelHandlerMask.MASK_SHUTDOWN)
            ctx.invokeShutdown(direction, future)
        } catch {
            case e: Throwable => future.promise.setFailure(e)
        }
        future
    }

    private def invokeShutdown(direction: ChannelShutdownDirection, future: ChannelFuture): Unit = {
        saveCurrentPendingBytesIfNeededOutbound() match
            case Some(value) => future.promise.setFailure(value)
            case None =>
                try {
                    handler.shutdown(this, direction, future)
                } catch {
                    case t: Throwable => future.promise.setFailure(handleOutboundHandlerException(t, false))
                } finally updatePendingBytesIfNeeded()
    }

    override def register(future: ChannelFuture): ChannelFuture = {
        try {
            val ctx = findContextOutbound(ChannelHandlerMask.MASK_REGISTER)
            ctx.invokeRegister(future)
        } catch {
            case e: Throwable => future.promise.setFailure(e)
        }
        future
    }

    private def invokeRegister(future: ChannelFuture): Unit = {
        saveCurrentPendingBytesIfNeededOutbound() match
            case Some(value) => future.promise.setFailure(value)
            case None =>
                try {
                    handler.register(this, future)
                } catch {
                    case t: Throwable => future.promise.setFailure(handleOutboundHandlerException(t, false))
                } finally updatePendingBytesIfNeeded()
    }

    override def deregister(future: ChannelFuture): ChannelFuture = {
        try {
            val ctx = findContextOutbound(ChannelHandlerMask.MASK_DEREGISTER)
            ctx.invokeDeregister(future)
        } catch {
            case e: Throwable => future.promise.setFailure(e)
        }
        future
    }

    private def invokeDeregister(future: ChannelFuture): Unit = {
        saveCurrentPendingBytesIfNeededOutbound() match
            case Some(value) => future.promise.setFailure(value)
            case None =>
                try {
                    handler.deregister(this, future)
                } catch {
                    case t: Throwable => future.promise.setFailure(handleOutboundHandlerException(t, false))
                } finally updatePendingBytesIfNeeded()
    }

    override def write(msg: AnyRef): Unit = write0(msg, false)

    private def invokeWrite(msg: AnyRef): Unit = try {
        val m = pipeline.touch(msg, this)
        if (saveCurrentPendingBytesIfNeededInbound()) handler.write(this, m)
    } catch {
        case t: Throwable => handleOutboundHandlerException(t, false)
    } finally updatePendingBytesIfNeeded()
    override def write(msg: AnyRef, msgId: Long): Unit = write0(msg, false, msgId)

    private def invokeWrite(msg: AnyRef, msgId: Long): Unit = try {
        val m = pipeline.touch(msg, this)
        if (saveCurrentPendingBytesIfNeededInbound()) handler.write(this, m, msgId)
    } catch {
        case t: Throwable => handleOutboundHandlerException(t, false)
    } finally updatePendingBytesIfNeeded()

    override def writeAndFlush(msg: AnyRef): Unit = write0(msg, true)

    private def invokeWriteAndFlush(msg: AnyRef): Unit = {
        invokeWrite(msg)
        invokeFlush()
    }
    override def writeAndFlush(msg: AnyRef, msgId: Long): Unit = write0(msg, true, msgId)

    private def invokeWriteAndFlush(msg: AnyRef, msgId: Long): Unit = {
        invokeWrite(msg, msgId)
        invokeFlush()
    }
    private def write0(msg: AnyRef, flush: Boolean): Unit = {
        channel.assertExecutor() // check the method is called in ChannelsActor which the channel registered
        val next = findContextOutbound(if (flush) MASK_WRITE | MASK_FLUSH else MASK_WRITE)
        if (flush) next.invokeWriteAndFlush(msg) else next.invokeWrite(msg)
    }

    private def write0(msg: AnyRef, flush: Boolean, msgId: Long): Unit = {
        channel.assertExecutor() // check the method is called in ChannelsActor which the channel registered
        val next = findContextOutbound(if (flush) MASK_WRITE | MASK_FLUSH else MASK_WRITE)
        if (flush) next.invokeWriteAndFlush(msg, msgId) else next.invokeWrite(msg, msgId)
    }

    /** Send a custom outbound event via this [[ChannelOutboundInvoker]] through the [[ChannelPipeline]]. This will
     *  result in having the {{{ChannelHandler.sendOutboundEvent(ChannelHandlerContext, Object)}}} method called of the
     *  next [[ChannelHandler]] contained in the [[ChannelPipeline]] of the [[Channel]].
     *
     *  @param event
     */
    override def sendOutboundEvent(event: AnyRef): Unit = {
        val ctx = findContextInbound(ChannelHandlerMask.MASK_SEND_OUTBOUND_EVENT)
        ctx.invokeSendOutboundEvent(event)
    }

    private def invokeSendOutboundEvent(event: AnyRef): Unit = try {
        if (saveCurrentPendingBytesIfNeededInbound()) handler.sendOutboundEvent(this, event)
    } catch {
        case t: Throwable => handleOutboundHandlerException(t, false)
    } finally updatePendingBytesIfNeeded()

    override def executor: ChannelsActor[?] = pipeline.executor

    private def pendingOutboundBytes(): Long = {
        val pending = handler.pendingOutboundBytes(this)
        if (pending < 0) {
            pipeline.forceCloseTransport()
            val handlerName = StringUtil.simpleClassName(handler.getClass)
            val message =
                s"$handlerName.pendingOutboundBytes(ChannelHandlerContext) returned a negative value: $pending. Force closed transport."
            throw new IllegalStateException(message)
        }
        pending
    }

    private def updatePendingBytesIfNeeded(): Unit = if (handlesPendingOutboundBytes(executionMask)) {
        val current = currentPendingBytes
        if (current != -1) {
            currentPendingBytes = -1
            try {
                val newPending = pendingOutboundBytes()
                val delta      = current - newPending
                if (delta > 0) pipeline.decrementPendingOutboundBytes(delta)
                else if (delta < 0) pipeline.incrementPendingOutboundBytes(-delta)
            } catch { case e: IllegalStateException => logger.error(ThrowableUtil.stackTraceToString(e)) }
        }
    } else assert(currentPendingBytes == 0)

    private def saveCurrentPendingBytesIfNeededInbound(): Boolean =
        saveCurrentPendingBytesIfNeeded() match
            case Some(value) =>
                logger.error(ThrowableUtil.stackTraceToString(value))
                false
            case None => true

    inline private def saveCurrentPendingBytesIfNeededOutbound(): Option[IllegalStateException] =
        saveCurrentPendingBytesIfNeeded()

    private def saveCurrentPendingBytesIfNeeded(): Option[IllegalStateException] =
        if (!handlesPendingOutboundBytes(executionMask)) {
            assert(currentPendingBytes == 0)
            None
        } else if (currentPendingBytes == -1) {
            var ret: Option[IllegalStateException] = None
            try { currentPendingBytes = pendingOutboundBytes() }
            catch { case e: IllegalStateException => ret = Some(e) }
            ret
        } else None

}

object OtaviaChannelHandlerContext {

    /** Neither [[ChannelHandler.handlerAdded(ChannelHandlerContext)]] nor
     *  [[ChannelHandler.handlerRemoved(ChannelHandlerContext)]] was called.
     */
    private final val INIT: Int = 0

    /** [[ChannelHandler.handlerAdded(ChannelHandlerContext)]] was called. */
    private final val ADD_COMPLETE: Int = 1

    /** [[ChannelHandler.handlerRemoved(ChannelHandlerContext)]] is about to be called. */
    private final val REMOVE_STARTED: Int = 2

    /** [[ChannelHandler.handlerRemoved(ChannelHandlerContext)]] was called. */
    private final val REMOVE_COMPLETE: Int = 3

    private final def handlesPendingOutboundBytes(mask: Int): Boolean = (mask & MASK_PENDING_OUTBOUND_BYTES) != 0

}
