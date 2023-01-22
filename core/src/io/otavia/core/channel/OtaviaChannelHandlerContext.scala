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
import io.netty5.util.internal.{StringUtil, ThrowableUtil}
import io.otavia.core.actor.ChannelsActor
import io.otavia.core.channel.ChannelHandlerMask.*
import io.otavia.core.channel.OtaviaChannelHandlerContext.*
import io.otavia.core.util.ActorLogger

import java.net.SocketAddress
import scala.util.Try

final class OtaviaChannelHandlerContext(
    override val pipeline: OtaviaChannelPipeline,
    override val name: String,
    override val handler: ChannelHandler
) extends ChannelHandlerContext {

    protected val logger: ActorLogger = ActorLogger.getLogger(getClass)(using channel.executor)
    private val executionMask         = mask(handler.getClass)

    private var currentPendingBytes: Long = 0

    private var handlerState: Int = INIT
    private var removed: Boolean  = _

    protected[channel] var next: OtaviaChannelHandlerContext = _
    protected[channel] var prev: OtaviaChannelHandlerContext = _

    private def handleOutboundHandlerException(cause: Throwable, closeDidThrow: Boolean): Unit = {
        val msg = s"$handler threw an exception while handling an outbound event. This is most likely a bug"
        logger.logWarn(s"$msg. This is most likely a bug, closing the channel.", cause)
        if (closeDidThrow) close() else channel.close()
        throw new IllegalStateException(msg, cause)
    }

    private def findContextInbound(mask: Int): OtaviaChannelHandlerContext = {
        var ctx = this
        while {
            ctx = ctx.next
            (ctx.executionMask & mask) == 0 // || ctx.handlerState == REMOVE_STARTED
        } do ()
        ctx
    }

    private def findContextOutbound(mask: Int): OtaviaChannelHandlerContext = {
        var ctx = this
        while {
            ctx = ctx.prev
            (ctx.executionMask & mask) == 0 // || ctx.handlerState == REMOVE_STARTED
        } do ()
        ctx
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

    override def fireChannelRegistered(): ChannelHandlerContext = {
        val ctx = findContextInbound(ChannelHandlerMask.MASK_CHANNEL_REGISTERED)
        ctx.invokeChannelRegistered()
        this
    }

    private[channel] def invokeChannelRegistered(): Unit = try {
        if (saveCurrentPendingBytesIfNeededDuplex()) handler.channelRegistered(this)
    } catch {
        case t: Throwable => invokeChannelExceptionCaught(t)
    } finally updatePendingBytesIfNeeded()

    override def fireChannelUnregistered(): ChannelHandlerContext = {
        val ctx = findContextInbound(ChannelHandlerMask.MASK_CHANNEL_UNREGISTERED)
        ctx.invokeChannelUnregistered()
        this
    }

    private[channel] def invokeChannelUnregistered(): Unit = try {
        if (saveCurrentPendingBytesIfNeededDuplex()) handler.channelUnregistered(this)
    } catch {
        case t: Throwable => invokeChannelExceptionCaught(t)
    } finally updatePendingBytesIfNeeded()

    override def fireChannelActive(): ChannelHandlerContext = {
        val ctx = findContextInbound(ChannelHandlerMask.MASK_CHANNEL_ACTIVE)
        ctx.invokeChannelActive()
        this
    }

    private[channel] def invokeChannelActive(): Unit = try {
        if (saveCurrentPendingBytesIfNeededDuplex()) handler.channelActive(this)
    } catch {
        case t: Throwable => invokeChannelExceptionCaught(t)
    } finally updatePendingBytesIfNeeded()

    override def fireChannelInactive(): ChannelHandlerContext = {
        val ctx = findContextInbound(ChannelHandlerMask.MASK_CHANNEL_INACTIVE)
        ctx.invokeChannelInactive()
        this
    }

    private[channel] def invokeChannelInactive(): Unit = try {
        if (saveCurrentPendingBytesIfNeededDuplex()) handler.channelInactive(this)
    } catch {
        case t: Throwable => invokeChannelExceptionCaught(t)
    } finally updatePendingBytesIfNeeded()

    override def fireChannelShutdown(direction: ChannelShutdownDirection): ChannelHandlerContext = {
        val ctx = findContextInbound(ChannelHandlerMask.MASK_CHANNEL_SHUTDOWN)
        ctx.invokeChannelShutdown(direction)
        this
    }

    private[channel] def invokeChannelShutdown(direction: ChannelShutdownDirection): Unit = try {
        if (saveCurrentPendingBytesIfNeededDuplex()) handler.channelShutdown(this, direction)
    } catch {
        case t: Throwable => invokeChannelExceptionCaught(t)
    } finally updatePendingBytesIfNeeded()

    override def fireChannelExceptionCaught(cause: Throwable): ChannelHandlerContext = {
        val ctx = findContextInbound(ChannelHandlerMask.MASK_CHANNEL_EXCEPTION_CAUGHT)
        ctx.invokeChannelExceptionCaught(cause)
        this
    }

    def invokeChannelExceptionCaught(cause: Throwable): Unit = if (saveCurrentPendingBytesIfNeededDuplex()) {
        try { handler.channelExceptionCaught(this, cause) }
        catch {
            case error: Throwable =>
                logger.logDebug(
                  s"An exception ${ThrowableUtil.stackTraceToString(error)} was thrown by a user handler's " +
                      "exceptionCaught() method while handling the following exception:",
                  cause
                )
                logger.logWarn(
                  s"An exception '$error' [enable DEBUG level for full stacktrace] "
                      + "was thrown by a user handler's exceptionCaught() "
                      + "method while handling the following exception:",
                  cause
                )
        } finally updatePendingBytesIfNeeded()
    }

    override def fireChannelInboundEvent(evt: AnyRef): ChannelHandlerContext = {
        val ctx = findContextInbound(ChannelHandlerMask.MASK_CHANNEL_INBOUND_EVENT)
        ctx.invokeChannelInboundEvent(evt)
        this
    }

    private[channel] def invokeChannelInboundEvent(event: AnyRef): Unit = try {
        if (saveCurrentPendingBytesIfNeededDuplex()) handler.channelInboundEvent(this, event)
        else Resource.dispose(event)
    } catch {
        case t: Throwable => invokeChannelExceptionCaught(t)
    } finally updatePendingBytesIfNeeded()

    override def fireChannelTimeoutEvent(id: Long): ChannelHandlerContext = {
        val ctx = findContextInbound(ChannelHandlerMask.MASK_CHANNEL_TIMEOUT_EVENT)
        ctx.invokeChannelTimeoutEvent(id)
        this
    }

    private[channel] def invokeChannelTimeoutEvent(id: Long): ChannelHandlerContext = {
        try handler.channelTimeoutEvent(id)
        catch { case t: Throwable => invokeChannelExceptionCaught(t) }
    }

    override def fireChannelRead(msg: AnyRef): ChannelHandlerContext = {
        val ctx = findContextInbound(ChannelHandlerMask.MASK_CHANNEL_READ)
        ctx.invokeChannelRead(msg)
        this
    }

    private[channel] def invokeChannelRead(msg: AnyRef): Unit = if (saveCurrentPendingBytesIfNeededDuplex()) {
        try handler.channelRead(this, msg)
        catch {
            case t: Throwable => invokeChannelExceptionCaught(t)
        } finally updatePendingBytesIfNeeded()
    } else Resource.dispose(msg)

    override def fireChannelReadComplete(): ChannelHandlerContext = {
        val ctx = findContextInbound(ChannelHandlerMask.MASK_CHANNEL_READ_COMPLETE)
        ctx.invokeChannelReadComplete()
        this
    }

    private[channel] def invokeChannelReadComplete(): Unit = try {
        if (saveCurrentPendingBytesIfNeededDuplex()) handler.channelReadComplete(this)
    } catch {
        case t: Throwable => invokeChannelExceptionCaught(t)
    } finally updatePendingBytesIfNeeded()

    override def fireChannelWritabilityChanged(): ChannelHandlerContext = {
        val ctx = findContextInbound(ChannelHandlerMask.MASK_CHANNEL_WRITABILITY_CHANGED)
        ctx.invokeChannelWritabilityChanged()
        this
    }

    def invokeChannelWritabilityChanged(): Unit = try {
        if (saveCurrentPendingBytesIfNeededDuplex()) handler.channelWritabilityChanged(this)
    } catch {
        case t: Throwable => invokeChannelExceptionCaught(t)
    } finally updatePendingBytesIfNeeded()

    override def read(readBufferAllocator: ReadBufferAllocator): ChannelHandlerContext = {
        val ctx = findContextOutbound(ChannelHandlerMask.MASK_READ)
        ctx.invokeRead(readBufferAllocator)
        this
    }

    private def invokeRead(allocator: ReadBufferAllocator): Unit = try {
        if (saveCurrentPendingBytesIfNeededDuplex()) handler.read(this, allocator)
    } catch {
        case t: Throwable => handleOutboundHandlerException(t, false)
    } finally updatePendingBytesIfNeeded()

    override def read(): ChannelHandlerContext = {
        val ctx = findContextOutbound(ChannelHandlerMask.MASK_READ)
        ctx.invokeRead(OtaviaChannelPipeline.DEFAULT_READ_BUFFER_ALLOCATOR)
        this
    }

    override def flush(): ChannelHandlerContext = {
        val ctx = findContextOutbound(ChannelHandlerMask.MASK_FLUSH)
        ctx.invokeFlush()
        this
    }

    private def invokeFlush(): Unit = try {
        if (saveCurrentPendingBytesIfNeededDuplex()) handler.flush(this)
    } catch {
        case t: Throwable => handleOutboundHandlerException(t, false)
    } finally updatePendingBytesIfNeeded()

    override def bind(): Unit = {
        val ctx = findContextOutbound(ChannelHandlerMask.MASK_BIND)
        ctx.invokeBind()
    }

    private[channel] def invokeBind(): Unit = try {
        if (saveCurrentPendingBytesIfNeededDuplex()) handler.bind(this)
    } catch {
        case t: Throwable => handleOutboundHandlerException(t, false)
    } finally updatePendingBytesIfNeeded()

    override def connect(): Unit = {
        val ctx = findContextOutbound(ChannelHandlerMask.MASK_CONNECT)
        ctx.invokeConnect()
    }

    private def invokeConnect(): Unit = try {
        if (saveCurrentPendingBytesIfNeededDuplex()) handler.connect(this)
    } catch {
        case t: Throwable => handleOutboundHandlerException(t, false)
    } finally updatePendingBytesIfNeeded()

    override def disconnect(): Unit = {
        val abstractChannel = channel.asInstanceOf[AbstractChannel[?, ?]]
        if (!abstractChannel.supportingDisconnect) close()
        else {
            val ctx = findContextOutbound(ChannelHandlerMask.MASK_DISCONNECT)
            ctx.invokeDisconnect()
        }
    }

    private def invokeDisconnect(): Unit = try {
        if (saveCurrentPendingBytesIfNeededDuplex()) handler.disconnect(this)
    } catch {
        case t: Throwable => handleOutboundHandlerException(t, false)
    } finally updatePendingBytesIfNeeded()

    override def close(): Unit = {
        val ctx = findContextOutbound(ChannelHandlerMask.MASK_CLOSE)
        ctx.invokeClose()
    }

    private def invokeClose(): Unit = try {
        if (saveCurrentPendingBytesIfNeededDuplex()) handler.close(this)
    } catch {
        case t: Throwable => handleOutboundHandlerException(t, false)
    } finally updatePendingBytesIfNeeded()

    override def shutdown(direction: ChannelShutdownDirection): Unit = {
        val ctx = findContextOutbound(ChannelHandlerMask.MASK_SHUTDOWN)
        ctx.invokeShutdown(direction)
    }

    private def invokeShutdown(direction: ChannelShutdownDirection): Unit = try {
        if (saveCurrentPendingBytesIfNeededDuplex()) handler.shutdown(this, direction)
    } catch {
        case t: Throwable => handleOutboundHandlerException(t, false)
    } finally updatePendingBytesIfNeeded()

    override def register(): Unit = {
        val ctx = findContextOutbound(ChannelHandlerMask.MASK_REGISTER)
        ctx.invokeRegister()
    }

    private def invokeRegister(): Unit = try {
        if (saveCurrentPendingBytesIfNeededDuplex()) handler.register(this)
    } catch {
        case t: Throwable => handleOutboundHandlerException(t, false)
    } finally updatePendingBytesIfNeeded()

    override def deregister(): Unit = {
        val ctx = findContextOutbound(ChannelHandlerMask.MASK_DEREGISTER)
        ctx.invokeDeregister()
    }

    private def invokeDeregister(): Unit = try {
        if (saveCurrentPendingBytesIfNeededDuplex()) handler.deregister(this)
    } catch {
        case t: Throwable => handleOutboundHandlerException(t, false)
    } finally updatePendingBytesIfNeeded()

    override def write(msg: AnyRef): Unit = write(msg, false)

    private def invokeWrite(msg: AnyRef): Unit = try {
        val m = pipeline.touch(msg, this)
        if (saveCurrentPendingBytesIfNeededDuplex()) handler.write(this, m)
    } catch {
        case t: Throwable => handleOutboundHandlerException(t, false)
    } finally updatePendingBytesIfNeeded()
    override def write(msg: AnyRef, msgId: Long): Unit = write(msg, false, msgId)

    private def invokeWrite(msg: AnyRef, msgId: Long): Unit = try {
        val m = pipeline.touch(msg, this)
        if (saveCurrentPendingBytesIfNeededDuplex()) handler.write(this, m, msgId)
    } catch {
        case t: Throwable => handleOutboundHandlerException(t, false)
    } finally updatePendingBytesIfNeeded()

    override def writeAndFlush(msg: AnyRef): Unit = write(msg, true)

    private def invokeWriteAndFlush(msg: AnyRef): Unit = {
        invokeWrite(msg)
        invokeFlush()
    }
    override def writeAndFlush(msg: AnyRef, msgId: Long): Unit = write(msg, true, msgId)

    private def invokeWriteAndFlush(msg: AnyRef, msgId: Long): Unit = {
        invokeWrite(msg, msgId)
        invokeFlush()
    }
    private def write(msg: AnyRef, flush: Boolean): Unit = {
        channel.assertExecutor() // check the method is called in ChannelsActor which the channel registered
        val next = findContextOutbound(if (flush) MASK_WRITE | MASK_FLUSH else MASK_WRITE)
        if (flush) next.invokeWriteAndFlush(msg) else next.invokeWrite(msg)
    }

    private def write(msg: AnyRef, flush: Boolean, msgId: Long): Unit = {
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
        if (saveCurrentPendingBytesIfNeededDuplex()) handler.sendOutboundEvent(this, event)
    } catch {
        case t: Throwable => handleOutboundHandlerException(t, false)
    } finally updatePendingBytesIfNeeded()

    override def executor: ChannelsActor[_] = pipeline.executor

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
            } catch { case e: IllegalStateException => logger.logError(e) }
        }
    } else assert(currentPendingBytes == 0)

    private def saveCurrentPendingBytesIfNeededDuplex(): Boolean =
        saveCurrentPendingBytesIfNeeded() match
            case Some(value) => logger.logError(value); false
            case None        => true

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
    final val INIT: Int = 0

    /** [[ChannelHandler.handlerAdded(ChannelHandlerContext)]] was called. */
    final val ADD_COMPLETE: Int = 1

    /** [[ChannelHandler.handlerRemoved(ChannelHandlerContext)]] is about to be called. */
    final val REMOVE_STARTED: Int = 2

    /** [[ChannelHandler.handlerRemoved(ChannelHandlerContext)]] was called. */
    final val REMOVE_COMPLETE: Int = 3

    final def handlesPendingOutboundBytes(mask: Int): Boolean = (mask & MASK_PENDING_OUTBOUND_BYTES) != 0

}
