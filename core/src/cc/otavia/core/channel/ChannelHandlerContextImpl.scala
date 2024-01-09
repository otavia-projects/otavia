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

package cc.otavia.core.channel

import cc.otavia.buffer.pool.AdaptiveBuffer
import cc.otavia.common.ThrowableUtil
import cc.otavia.core.actor.ChannelsActor
import cc.otavia.core.channel.ChannelHandlerContextImpl.*
import cc.otavia.core.channel.inflight.QueueMap
import cc.otavia.core.channel.internal.ChannelHandlerMask
import cc.otavia.core.channel.internal.ChannelHandlerMask.*
import cc.otavia.core.channel.message.{AutoReadPlan, ReadPlan}
import cc.otavia.core.slf4a.Logger
import cc.otavia.core.stack.{ChannelFuture, ChannelPromise, ChannelStack}
import cc.otavia.util.Resource

import java.net.SocketAddress
import java.nio.file.attribute.FileAttribute
import java.nio.file.{OpenOption, Path}
import scala.util.Try

final class ChannelHandlerContextImpl(
    override val pipeline: ChannelPipelineImpl,
    override val name: String,
    override val handler: ChannelHandler
) extends ChannelHandlerContext {

    protected val logger: Logger = Logger.getLogger(getClass, pipeline.system)

    private[core] val executionMask = mask(handler.getClass)

    private var currentPendingBytes: Long = 0

    private var handlerState: Int = INIT
    private var removed: Boolean  = _

    private var idx: Int = -1

    protected[channel] var next: ChannelHandlerContextImpl = _
    protected[channel] var prev: ChannelHandlerContextImpl = _

    private var inboundAdaptive: AdaptiveBuffer  = _
    private var outboundAdaptive: AdaptiveBuffer = _

    private[channel] def setInboundAdaptiveBuffer(inboundAdaptiveBuffer: AdaptiveBuffer): Unit =
        inboundAdaptive = inboundAdaptiveBuffer

    private[channel] def setOutboundAdaptiveBuffer(outboundAdaptiveBuffer: AdaptiveBuffer): Unit =
        outboundAdaptive = outboundAdaptiveBuffer

    def index: Int = idx

    def setIndex(id: Int): Unit = idx = id

    override def inboundAdaptiveBuffer: AdaptiveBuffer =
        if (hasInboundAdaptive) inboundAdaptive else throw new UnsupportedOperationException()

    override def outboundAdaptiveBuffer: AdaptiveBuffer =
        if (hasOutboundAdaptive) outboundAdaptive else throw new UnsupportedOperationException()

    private def handleOutboundHandlerException(cause: Throwable, closeDidThrow: Boolean): IllegalStateException = {
        val msg = s"$handler threw an exception while handling an outbound event. This is most likely a bug"
        logger.warn(s"$msg. This is most likely a bug, closing the channel.", cause)
        if (closeDidThrow) close(ChannelFuture()) else channel.pipeline.close(ChannelFuture()) // ignore close future
        new IllegalStateException(msg, cause)
    }

    override def inflightFutures: QueueMap[ChannelPromise] = pipeline.inflightFutures

    override def inflightStacks[T <: AnyRef]: QueueMap[ChannelStack[T]] = pipeline.inflightStacks

    private def findContextInbound(mask: Int): ChannelHandlerContextImpl = if (!removed) {
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

    private def findContextOutbound(mask: Int): ChannelHandlerContextImpl = if (!removed) {
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
        if (ChannelHandlerContextImpl.handlesPendingOutboundBytes(executionMask)) {
            val pending = pendingOutboundBytes()
            currentPendingBytes = Math.max(0, pending)
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
                    currentPendingBytes = Math.max(0, pending)
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
            prev.next = next
            next.prev = prev
            // TODO
        }
    }

    override def fireChannelRegistered(): this.type = {
        val ctx = findContextInbound(ChannelHandlerMask.MASK_CHANNEL_REGISTERED)
        ctx.invokeChannelRegistered()
        this
    }

    private[core] def invokeChannelRegistered(): Unit = try {
        handler.channelRegistered(this)
        updatePendingBytesIfNeeded()
    } catch {
        case t: Throwable => invokeChannelExceptionCaught(t)
    }

    override def fireChannelUnregistered(): this.type = {
        val ctx = findContextInbound(ChannelHandlerMask.MASK_CHANNEL_UNREGISTERED)
        ctx.invokeChannelUnregistered()
        this
    }

    private[core] def invokeChannelUnregistered(): Unit = try {
        handler.channelUnregistered(this)
        updatePendingBytesIfNeeded()
    } catch {
        case t: Throwable => invokeChannelExceptionCaught(t)
    }

    override def fireChannelActive(): this.type = {
        val ctx = findContextInbound(ChannelHandlerMask.MASK_CHANNEL_ACTIVE)
        ctx.invokeChannelActive()
        this
    }

    private[core] def invokeChannelActive(): Unit = try {
        handler.channelActive(this)
        updatePendingBytesIfNeeded()
    } catch {
        case t: Throwable => invokeChannelExceptionCaught(t)
    }

    override def fireChannelInactive(): this.type = {
        val ctx = findContextInbound(ChannelHandlerMask.MASK_CHANNEL_INACTIVE)
        ctx.invokeChannelInactive()
        this
    }

    private[core] def invokeChannelInactive(): Unit = try {
        handler.channelInactive(this)
        updatePendingBytesIfNeeded()
    } catch {
        case t: Throwable => invokeChannelExceptionCaught(t)
    }

    override def fireChannelShutdown(direction: ChannelShutdownDirection): this.type = {
        val ctx = findContextInbound(ChannelHandlerMask.MASK_CHANNEL_SHUTDOWN)
        ctx.invokeChannelShutdown(direction)
        this
    }

    private[core] def invokeChannelShutdown(direction: ChannelShutdownDirection): Unit = try {
        handler.channelShutdown(this, direction)
        updatePendingBytesIfNeeded()
    } catch {
        case t: Throwable => invokeChannelExceptionCaught(t)
    }

    override def fireChannelExceptionCaught(cause: Throwable): this.type = {
        val ctx = findContextInbound(ChannelHandlerMask.MASK_CHANNEL_EXCEPTION_CAUGHT)
        ctx.invokeChannelExceptionCaught(cause)
        this
    }

    def invokeChannelExceptionCaught(cause: Throwable): Unit = {
        try {
            handler.channelExceptionCaught(this, cause)
            updatePendingBytesIfNeeded()
        } catch {
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
        }
    }

    override def fireChannelExceptionCaught(cause: Throwable, id: Long): this.type = {
        val ctx = findContextInbound(ChannelHandlerMask.MASK_CHANNEL_EXCEPTION_CAUGHT_ID)
        try {
            handler.channelExceptionCaught(this, cause, id)
            updatePendingBytesIfNeeded()
        } catch {
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
        }
        this
    }

    def invokeChannelExceptionCaught(cause: Throwable, id: Long): Unit = {
        try {
            handler.channelExceptionCaught(this, cause, id)
            updatePendingBytesIfNeeded()
        } catch {
            case t: Throwable => invokeChannelExceptionCaught(t)
        }
    }

    override def fireChannelInboundEvent(evt: AnyRef): this.type = {
        val ctx = findContextInbound(ChannelHandlerMask.MASK_CHANNEL_INBOUND_EVENT)
        ctx.invokeChannelInboundEvent(evt)
        this
    }

    private[core] def invokeChannelInboundEvent(event: AnyRef): Unit = try {
        handler.channelInboundEvent(this, event)
        updatePendingBytesIfNeeded()
    } catch {
        case t: Throwable => invokeChannelExceptionCaught(t)
    }

    override def fireChannelTimeoutEvent(id: Long): this.type = {
        val ctx = findContextInbound(ChannelHandlerMask.MASK_CHANNEL_TIMEOUT_EVENT)
        ctx.invokeChannelTimeoutEvent(id)
        this
    }

    private[core] def invokeChannelTimeoutEvent(id: Long): Unit = {
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

    private[core] def invokeChannelRead(msg: AnyRef): Unit = {
        try {
            handler.channelRead(this, msg)
            updatePendingBytesIfNeeded()
        } catch {
            case t: Throwable => invokeChannelExceptionCaught(t)
        }
    }

    override def fireChannelRead(msg: AnyRef, msgId: Long): this.type = {
        val ctx = findContextInbound(ChannelHandlerMask.MASK_CHANNEL_READ_ID)
        ctx.invokeChannelRead(msg, msgId)
        this
    }

    private[core] def invokeChannelRead(msg: AnyRef, id: Long): Unit = {
        try {
            handler.channelRead(this, msg, id)
            updatePendingBytesIfNeeded()
        } catch { case t: Throwable => invokeChannelExceptionCaught(t) }
    }

    override def fireChannelReadComplete(): this.type = {
        val ctx = findContextInbound(ChannelHandlerMask.MASK_CHANNEL_READ_COMPLETE)
        ctx.invokeChannelReadComplete()
        this
    }

    private[core] def invokeChannelReadComplete(): Unit = try {
        handler.channelReadComplete(this)
        updatePendingBytesIfNeeded()
    } catch {
        case t: Throwable => invokeChannelExceptionCaught(t)
    }

    override def fireChannelWritabilityChanged(): this.type = {
        val ctx = findContextInbound(ChannelHandlerMask.MASK_CHANNEL_WRITABILITY_CHANGED)
        ctx.invokeChannelWritabilityChanged()
        this
    }

    def invokeChannelWritabilityChanged(): Unit = try {
        handler.channelWritabilityChanged(this)
        updatePendingBytesIfNeeded()
    } catch {
        case t: Throwable => invokeChannelExceptionCaught(t)
    }

    override def read(readPlan: ReadPlan): this.type = {
        val ctx = findContextOutbound(ChannelHandlerMask.MASK_READ)
        ctx.invokeRead(readPlan)
        this
    }

    private def invokeRead(readPlan: ReadPlan): Unit = try {
        handler.read(this, readPlan)
        updatePendingBytesIfNeeded()
    } catch {
        case t: Throwable => handleOutboundHandlerException(t, false)
    }

    override def read(): this.type = {
        val ctx  = findContextOutbound(ChannelHandlerMask.MASK_READ)
        val plan = AutoReadPlan
        ctx.invokeRead(plan)
        this
    }

    override def flush(): this.type = {
        val ctx = findContextOutbound(ChannelHandlerMask.MASK_FLUSH)
        ctx.invokeFlush()
        this
    }

    private def invokeFlush(): Unit = try {
        handler.flush(this)
        updatePendingBytesIfNeeded()
    } catch {
        case t: Throwable => handleOutboundHandlerException(t, false)
    }

    override def bind(local: SocketAddress, future: ChannelFuture): ChannelFuture = {
        val nextCtx = findContextOutbound(ChannelHandlerMask.MASK_BIND)
        nextCtx.invokeBind(local, future)
        future
    }

    private[core] def invokeBind(local: SocketAddress, future: ChannelFuture): Unit = try {
        handler.bind(this, local, future)
        updatePendingBytesIfNeeded()
    } catch {
        case t: Throwable => future.promise.setFailure(handleOutboundHandlerException(t, false))
    }

    override def connect(remote: SocketAddress, local: Option[SocketAddress], future: ChannelFuture): ChannelFuture = {
        val nextCtx = findContextOutbound(ChannelHandlerMask.MASK_CONNECT)
        nextCtx.invokeConnect(remote, local, future)
        future
    }

    private def invokeConnect(remote: SocketAddress, local: Option[SocketAddress], future: ChannelFuture): Unit =
        try {
            handler.connect(this, remote, local, future)
            updatePendingBytesIfNeeded()
        } catch {
            case t: Throwable => future.promise.setFailure(handleOutboundHandlerException(t, false))
        }

    override def open(
        path: Path,
        options: Seq[OpenOption],
        attrs: Seq[FileAttribute[?]],
        future: ChannelFuture
    ): ChannelFuture = {
        val nextCtx = findContextOutbound(ChannelHandlerMask.MASK_OPEN)
        nextCtx.invokeOpen(path, options, attrs, future)
        future
    }

    private def invokeOpen(
        path: Path,
        options: Seq[OpenOption],
        attrs: Seq[FileAttribute[?]],
        future: ChannelFuture
    ): Unit = try {
        handler.open(this, path, options, attrs, future)
        updatePendingBytesIfNeeded()
    } catch {
        case t: Throwable => future.promise.setFailure(handleOutboundHandlerException(t, false))
    }

    override def disconnect(future: ChannelFuture): ChannelFuture = {
        val nextCtx = findContextOutbound(ChannelHandlerMask.MASK_DISCONNECT)
        nextCtx.invokeDisconnect(future)
        future
    }

    private def invokeDisconnect(future: ChannelFuture): Unit = try {
        handler.disconnect(this, future)
        updatePendingBytesIfNeeded()
    } catch {
        case t: Throwable => future.promise.setFailure(handleOutboundHandlerException(t, false))
    }

    override def close(future: ChannelFuture): ChannelFuture = {
        val ctx = findContextOutbound(ChannelHandlerMask.MASK_CLOSE)
        ctx.invokeClose(future)
        future
    }

    private def invokeClose(future: ChannelFuture): Unit = try {
        handler.close(this, future)
        updatePendingBytesIfNeeded()
    } catch {
        case t: Throwable => future.promise.setFailure(handleOutboundHandlerException(t, true))
    }

    override def shutdown(direction: ChannelShutdownDirection, future: ChannelFuture): ChannelFuture = {
        val ctx = findContextOutbound(ChannelHandlerMask.MASK_SHUTDOWN)
        ctx.invokeShutdown(direction, future)
        future
    }

    private def invokeShutdown(direction: ChannelShutdownDirection, future: ChannelFuture): Unit = try {
        handler.shutdown(this, direction, future)
        updatePendingBytesIfNeeded()
    } catch {
        case t: Throwable => future.promise.setFailure(handleOutboundHandlerException(t, true))
    }

    override def register(future: ChannelFuture): ChannelFuture = {
        val ctx = findContextOutbound(ChannelHandlerMask.MASK_REGISTER)
        ctx.invokeRegister(future)
        future
    }

    private def invokeRegister(future: ChannelFuture): Unit = {
        try {
            handler.register(this, future)
            updatePendingBytesIfNeeded()
        } catch {
            case t: Throwable => future.promise.setFailure(handleOutboundHandlerException(t, false))
        }
    }

    override def deregister(future: ChannelFuture): ChannelFuture = {
        val ctx = findContextOutbound(ChannelHandlerMask.MASK_DEREGISTER)
        ctx.invokeDeregister(future)
        future
    }

    private def invokeDeregister(future: ChannelFuture): Unit = try {
        handler.deregister(this, future)
        updatePendingBytesIfNeeded()
    } catch {
        case t: Throwable => future.promise.setFailure(handleOutboundHandlerException(t, false))
    }

    override def write(msg: AnyRef): Unit = write0(msg, false)

    private def invokeWrite(msg: AnyRef): Unit = try {
        handler.write(this, msg)
        updatePendingBytesIfNeeded()
    } catch {
        case t: Throwable => handleOutboundHandlerException(t, false)
    }

    override def write(msg: AnyRef, msgId: Long): Unit = write0(msg, false, msgId)

    private def invokeWrite(msg: AnyRef, msgId: Long): Unit = try {
        handler.write(this, msg, msgId)
        updatePendingBytesIfNeeded()
    } catch {
        case t: Throwable => handleOutboundHandlerException(t, false)
    }

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
        val next = findContextOutbound(if (flush) MASK_WRITE | MASK_FLUSH else MASK_WRITE)
        if (flush) next.invokeWriteAndFlush(msg) else next.invokeWrite(msg)
    }

    private def write0(msg: AnyRef, flush: Boolean, msgId: Long): Unit = {
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
        handler.sendOutboundEvent(this, event)
        updatePendingBytesIfNeeded()
    } catch {
        case t: Throwable => handleOutboundHandlerException(t, false)
    }

    override def executor: ChannelsActor[?] = pipeline.executor

    private def pendingOutboundBytes(): Long = {
        val pending = handler.pendingOutboundBytes(this)
        if (pending < 0) {
            pipeline.forceCloseTransport()
            val handlerName = handler.getClass.getSimpleName
            val message =
                s"$handlerName.pendingOutboundBytes(ChannelHandlerContext) returned a negative value: $pending. Force closed transport."
            throw new IllegalStateException(message)
        }
        pending
    }

    private def updatePendingBytesIfNeeded(): Unit = if (handlesPendingOutboundBytes(executionMask)) {
        val prev = currentPendingBytes
        try {
            currentPendingBytes = pendingOutboundBytes()
            val delta = currentPendingBytes - prev
            if (delta > 0) pipeline.incrementPendingOutboundBytes(delta)
            else if (delta < 0) pipeline.decrementPendingOutboundBytes(-delta)
        } catch {
            case e: IllegalStateException =>
                logger.error(ThrowableUtil.stackTraceToString(e))
            case e: Throwable => throw e
        }
    }

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

object ChannelHandlerContextImpl {

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
