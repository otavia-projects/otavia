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

import cc.otavia.buffer.pool.{AdaptiveBuffer, PooledPageAllocator}
import cc.otavia.common.ClassUtils
import cc.otavia.core.actor.ChannelsActor
import cc.otavia.core.cache.{ActorThreadLocal, ThreadLocal}
import cc.otavia.core.channel.ChannelPipelineImpl.*
import cc.otavia.core.channel.inflight.QueueMap
import cc.otavia.core.channel.internal.ChannelHandlerMask
import cc.otavia.core.channel.message.FixedReadPlanFactory.FixedReadPlan
import cc.otavia.core.channel.message.{FixedReadPlanFactory, ReadPlan}
import cc.otavia.core.slf4a.Logger
import cc.otavia.core.stack.{ChannelFuture, ChannelPromise, ChannelStack}
import cc.otavia.core.system.ActorSystem
import cc.otavia.util.Resource

import java.net.SocketAddress
import java.nio.file.attribute.FileAttribute
import java.nio.file.{OpenOption, Path}
import scala.collection.mutable
import scala.language.unsafeNulls

class ChannelPipelineImpl(override val channel: AbstractChannel) extends ChannelPipeline {

    protected val logger: Logger = Logger.getLogger(getClass, channel.system)

    private val channelInboundAdaptiveBuffer: AdaptiveBuffer  = AdaptiveBuffer(channel.directAllocator)
    private val channelOutboundAdaptiveBuffer: AdaptiveBuffer = AdaptiveBuffer(channel.directAllocator)

    private val head = new ChannelHandlerContextImpl(this, HEAD_NAME, HEAD_HANDLER)
    private val tail = new ChannelHandlerContextImpl(this, TAIL_NAME, TAIL_HANDLER)

    head.next = tail
    tail.prev = head

    head.setInboundAdaptiveBuffer(channelInboundAdaptiveBuffer)

    head.setAddComplete()
    tail.setAddComplete()

    private val touch: Boolean = true

    private final val handlers = new mutable.ArrayBuffer[ChannelHandlerContextImpl](4)

    private var _pendingOutboundBytes: Long = 0

    private[core] override def channelInboundBuffer: AdaptiveBuffer = channelInboundAdaptiveBuffer

    private[core] override def channelOutboundBuffer: AdaptiveBuffer = channelOutboundAdaptiveBuffer

    private[core] override def closeInboundAdaptiveBuffers(): Unit = {
        channelInboundAdaptiveBuffer.close()
        for { ctx <- handlers if ctx.hasInboundAdaptive } ctx.inboundAdaptiveBuffer.close()
    }

    private[core] override def closeOutboundAdaptiveBuffers(): Unit = {
        channelOutboundAdaptiveBuffer.close()
        for { ctx <- handlers if ctx.hasOutboundAdaptive } ctx.outboundAdaptiveBuffer.close()
    }

    override def inflightFutures: QueueMap[ChannelPromise] = channel.inflightFutures

    override def inflightStacks[T <: AnyRef]: QueueMap[ChannelStack[T]] =
        channel.inflightStacks.asInstanceOf[QueueMap[ChannelStack[T]]]

    final def touch(msg: AnyRef, next: ChannelHandlerContextImpl): AnyRef = {
        if (touch) Resource.touch(msg, next)
        msg
    }

    private def newContext(name: Option[String], handler: ChannelHandler): ChannelHandlerContextImpl = {
        checkMultiplicity(handler) // check multi add
        val value = name match
            case Some(value) =>
                checkDuplicateName(value)
                value
            case None =>
                generateName(handler)

        new ChannelHandlerContextImpl(this, value, handler)
    }

    @throws[IllegalArgumentException]
    private def checkDuplicateName(name: String): Unit =
        if (context(name).nonEmpty) throw new IllegalArgumentException(s"Duplicate handler name: $name")

    private def replaceBufferHead(newCtx: ChannelHandlerContextImpl, oldFirst: ChannelHandlerContextImpl): Unit = {
        setHeadAdaptiveBuffer(newCtx)

        val outbound = AdaptiveBuffer(channel.heapAllocator)
        oldFirst.setOutboundAdaptiveBuffer(outbound)

        if (newCtx.outboundAdaptiveBuffer.readableBytes > 0) {
            outbound.writeBytes(newCtx.outboundAdaptiveBuffer)
        }
    }

    private def setAdaptiveBuffer(ctx: ChannelHandlerContextImpl, allocator: PooledPageAllocator): Unit = {
        if (ctx.hasInboundAdaptive) {
            val inbound = AdaptiveBuffer(allocator)
            ctx.setInboundAdaptiveBuffer(inbound)
        }

        if (ctx.hasOutboundAdaptive) {
            val outbound = AdaptiveBuffer(allocator)
            ctx.setOutboundAdaptiveBuffer(outbound)
        }
    }

    private def setHeadAdaptiveBuffer(newCtx: ChannelHandlerContextImpl): Unit = {
        if (newCtx.hasInboundAdaptive) {
            val inbound = AdaptiveBuffer(channel.heapAllocator)
            newCtx.setInboundAdaptiveBuffer(inbound)
        }
        if (newCtx.hasOutboundAdaptive) {
            newCtx.setOutboundAdaptiveBuffer(channelOutboundAdaptiveBuffer)
        }
    }

    override def addFirst(name: Option[String], handler: ChannelHandler): ChannelPipeline = {
        channel.assertExecutor() // check the method is called in ChannelsActor which it registered
        val newCtx = newContext(name, handler)
        if (handlers.nonEmpty) {
            val oldFirst = handlers.head
            if (newCtx.hasOutboundAdaptive && oldFirst.hasOutboundAdaptive) {
                replaceBufferHead(newCtx, oldFirst)
            } else if (!newCtx.hasOutboundAdaptive && oldFirst.hasOutboundAdaptive) {
                throw new IllegalStateException(
                  "can't add no outbound buffered handler before at outbound buffered handler!"
                )
            } else if (newCtx.isBufferHandlerContext && !oldFirst.isBufferHandlerContext) {
                setHeadAdaptiveBuffer(newCtx)
            }
        } else if (newCtx.isBufferHandlerContext) setHeadAdaptiveBuffer(newCtx)

        handlers.insert(0, newCtx)
        resetIndices()
        val nextCtx = head.next
        newCtx.prev = head
        newCtx.next = nextCtx
        head.next = newCtx
        nextCtx.prev = newCtx

        callHandlerAdded0(newCtx)
        this
    }

    override def addFirst(handlers: ChannelHandler*): ChannelPipeline = {
        for (handler <- handlers) addFirst(None, handler)
        this
    }

    override def addLast(name: Option[String], handler: ChannelHandler): ChannelPipeline = {
        val newCtx = newContext(name, handler)
        if (handlers.isEmpty && newCtx.isBufferHandlerContext) setHeadAdaptiveBuffer(newCtx)
        if (handlers.nonEmpty && newCtx.isBufferHandlerContext) {
            if (!handlers.last.isBufferHandlerContext)
                throw new IllegalStateException(
                  s"buffered handler $handler can't add after no buffered handler ${handlers.last.handler}"
                )
            else setAdaptiveBuffer(newCtx, channel.heapAllocator)
        }

        handlers.addOne(newCtx)
        resetIndices()
        val prevCtx = tail.prev
        newCtx.prev = prevCtx
        newCtx.next = tail
        prevCtx.next = newCtx
        tail.prev = newCtx

        callHandlerAdded0(newCtx)
        this
    }

    override def addBefore(baseName: String, name: Option[String], handler: ChannelHandler): ChannelPipeline = {
        channel.assertExecutor() // check the method is called in ChannelsActor whit it registered
        assert(HEAD_NAME != baseName, s"Can't add handler $handler before HeadHandler: $HEAD_NAME")

        if (TAIL_NAME == baseName) addLast(name, handler)
        else {
            handlers.zipWithIndex.find(_._1.name == baseName) match
                case Some((ctx, index)) =>
                    if (index == 0) addFirst(name, handler)
                    else {
                        val newCtx = newContext(name, handler)
                        if (newCtx.isBufferHandlerContext) {
                            assert(
                              ctx.isBufferHandlerContext,
                              s"buffered handler $handler can't add after no buffered handler ${ctx.handler}"
                            )
                            setAdaptiveBuffer(newCtx, channel.heapAllocator)
                        }
                        handlers.insert(index, newCtx)
                        resetIndices()
                        newCtx.prev = ctx.prev
                        newCtx.next = ctx
                        ctx.prev.next = newCtx
                        ctx.prev = newCtx
                        callHandlerAdded0(newCtx)
                    }
                case None => throw new NoSuchElementException(baseName)
        }

        this
    }

    override def addAfter(baseName: String, name: Option[String], handler: ChannelHandler): ChannelPipeline = {
        channel.assertExecutor() // check the method is called in ChannelsActor which the channel registered
        assert(TAIL_NAME != baseName, s"Can't add handler after TailHandler: $TAIL_NAME")

        if (HEAD_NAME == baseName) addFirst(name, handler)
        else {
            handlers.zipWithIndex.find(_._1.name == baseName) match
                case Some((ctx, index)) =>
                    val newCtx = newContext(name, handler)
                    if (newCtx.isBufferHandlerContext) {
                        val before = handlers(index)
                        assert(
                          before.isBufferHandlerContext,
                          s"buffered handler $handler can't add after no buffered handler ${before.handler}"
                        )
                        setAdaptiveBuffer(newCtx, channel.heapAllocator)
                    }
                    handlers.insert(index + 1, newCtx)
                    resetIndices()
                    newCtx.prev = ctx
                    newCtx.next = ctx.next
                    ctx.next.prev = newCtx
                    ctx.next = newCtx
                    callHandlerAdded0(newCtx)
                case None => throw new NoSuchElementException(baseName)
        }

        this
    }

    private def callHandlerAdded0(ctx: ChannelHandlerContextImpl): Unit = try {
        ctx.callHandlerAdded()
    } catch {
        case t: Throwable =>
            var removed = false
            try {
                handlers.zipWithIndex.find(_._1 == ctx) match
                    case Some((_, index)) =>
                        handlers.remove(index)
                        resetIndices()
                    case None => // do nothing
                ctx.callHandlerRemoved()
                removed = true
            } catch {
                case t2: Throwable =>
                    logger.warn(s"Failed to remove a handler: ${ctx.name}", t2)
            } finally {
                ctx.remove(true)
            }

            if (removed) {
                val error = s"${ctx.handler.getClass.getName}.handlerAdded() has thrown an exception; removed."
                fireChannelExceptionCaught(new ChannelPipelineException(error, t))
            } else {
                val error =
                    s"${ctx.handler.getClass.getName}.handlerAdded() has thrown an exception; also failed to remove."
                fireChannelExceptionCaught(new ChannelPipelineException(error, t))
            }
    }

    private final def callHandlerRemoved0(ctx: ChannelHandlerContextImpl): Unit = try {
        ctx.callHandlerRemoved()
    } catch {
        case t: Throwable =>
            val error = s"${ctx.handler.getClass.getName}.handlerRemoved() has thrown an exception."
            fireChannelExceptionCaught(new RuntimeException(error, t))
    }

    private def generateName(handler: ChannelHandler): String = {
        val cache      = nameCaches.get()
        val handlerClz = handler.getClass
        var name: String = cache.get(handlerClz) match
            case Some(value) => value
            case None =>
                val name0 = generateName0(handlerClz)
                cache.put(handlerClz, name0)
                name0

        // It's not very likely for a user to put more than one handler of the same type, but make sure to avoid
        // any name conflicts.  Note that we don't cache the names generated here.
        if (context(name).nonEmpty) {
            val baseName          = name.substring(0, name.length - 1).nn // Strip the trailing '0'.
            var i: Int            = 1
            var continue: Boolean = true
            while (continue) {
                val newName = baseName + i
                if (context(newName).isEmpty) {
                    name = newName; continue = false
                }
                i += 1
            }
        }
        name
    }

    override def remove(handler: ChannelHandler): ChannelPipeline = {
        channel.assertExecutor() // check the method is called in ChannelsActor which the channel registered
        handlers.zipWithIndex.find(_._1.handler == handler) match
            case Some((ctx, index)) =>
                handlers.remove(index)
                resetIndices()
                remove0(ctx)
            case None => logger.warn(s"Handler $handler not be added to the channel's pipeline")
        this
    }

    override def remove(name: String): Option[ChannelHandler] = {
        channel.assertExecutor() // check the method is called in ChannelsActor which the channel registered
        handlers.zipWithIndex.find(_._1.name == name) match
            case Some((ctx, index)) =>
                handlers.remove(index)
                resetIndices()
                remove0(ctx)
                Some(ctx.handler)
            case None =>
                logger.warn(s"Handler name $name not be added to the channel's pipeline")
                None
    }

    override def remove[T <: ChannelHandler](handlerType: Class[T]): Option[T] =
        removeIfExists(ctx => handlerType.isAssignableFrom(ctx.handler.getClass))

    private def remove0(ctx: ChannelHandlerContextImpl): Unit = try {
        callHandlerRemoved0(ctx)
    } finally { ctx.remove(true) }

    override def context(handler: ChannelHandler): Option[ChannelHandlerContext] = handlers.find(_.handler == handler)

    override def context(name: String): Option[ChannelHandlerContext] = handlers.find(_.name == name)

    override def context(handlerType: Class[? <: ChannelHandler]): Option[ChannelHandlerContext] =
        handlers.find(ctx => handlerType.isAssignableFrom(ctx.handler.getClass))

    /** Inserts [[ChannelHandler]]s at the last position of this pipeline. `null` handlers will be skipped.
     *
     *  @param handlers
     *    the handlers to insert last
     */
    override def addLast(handlers: ChannelHandler*): ChannelPipeline = {
        for (handler <- handlers) addLast(None, handler)
        this
    }

    /** Removes the [[ChannelHandler]] with the specified name from this pipeline if it exists.
     *
     *  @param name
     *    the name under which the [[ChannelHandler]] was stored.
     *  @return
     *    the removed handler
     *  @throws NoSuchElementException
     *    if there's no such handler with the specified name in this pipeline
     *  @throws NullPointerException
     *    if the specified name is `null`
     */
    override def removeIfExists[T <: ChannelHandler](name: String): Option[T] =
        removeIfExists(_.name == name)

    /** Removes the [[ChannelHandler]] of the specified type from this pipeline if it exists.
     *
     *  @param <
     *    T> the type of the handler
     *  @param handlerType
     *    the type of the handler
     *  @return
     *    the removed handler or `null` if it didn't exist.
     *  @throws NullPointerException
     *    if the specified handler type is `null`
     */
    override def removeIfExists[T <: ChannelHandler](handlerType: Class[T]): Option[T] =
        removeIfExists(ctx => handlerType.isAssignableFrom(ctx.handler.getClass))

    /** Removes the specified [[ChannelHandler]] from this pipeline if it exists
     *
     *  @param handler
     *    the [[ChannelHandler]] to remove
     *  @return
     *    the removed handler or `null` if it didn't exist.
     *  @throws NullPointerException
     *    if the specified handler is `null`
     */
    override def removeIfExists[T <: ChannelHandler](handler: ChannelHandler): Option[T] =
        removeIfExists(_.handler == handler)

    private final def removeIfExists[T <: ChannelHandler](finder: ChannelHandlerContext => Boolean): Option[T] = {
        channel.assertExecutor() // check the method is called in ChannelsActor which the channel registered
        handlers.zipWithIndex.find((ctx, _) => finder(ctx)) match
            case Some((ctx, index)) =>
                handlers.remove(index)
                resetIndices()
                remove0(ctx)
                Some(ctx.handler.asInstanceOf[T])
            case None =>
                logger.warn(s"Handler not be added to the channel's pipeline")
                None
    }

    /** Removes the first [[ChannelHandler]] in this pipeline.
     *
     *  @return
     *    the removed handler, None if this pipeline is empty
     */
    override def removeFirst(): Option[ChannelHandler] = {
        channel.assertExecutor() // check the method is called in ChannelsActor which the channel registered
        if (handlers.nonEmpty) {
            val ctx = handlers.remove(0)
            resetIndices()
            remove0(ctx)
            Some(ctx.handler)
        } else None
    }

    /** Removes the last [[ChannelHandler]] in this pipeline.
     *
     *  @return
     *    the removed handler, None if this pipeline is empty
     */
    override def removeLast(): Option[ChannelHandler] = {
        channel.assertExecutor() // check the method is called in ChannelsActor which the channel registered
        if (handlers.nonEmpty) {
            val ctx = handlers.remove(handlers.size - 1)
            resetIndices()
            remove0(ctx)
            Some(ctx.handler)
        } else None
    }

    /** Replaces the specified [[ChannelHandler]] with a new handler in this pipeline.
     *
     *  @param old
     *    the [[ChannelHandler]] to be replaced
     *  @param newName
     *    the name under which the replacement should be added
     *  @param newHandler
     *    the [[ChannelHandler]] which is used as replacement
     *  @return
     *    itself
     *  @throws NoSuchElementException
     *    if the specified old handler does not exist in this pipeline
     *  @throws IllegalArgumentException
     *    if a handler with the specified new name already exists in this pipeline, except for the handler to be
     *    replaced
     *  @throws NullPointerException
     *    if the specified old handler or new handler is `null`
     */
    override def replace(old: ChannelHandler, newName: Option[String], newHandler: ChannelHandler): ChannelPipeline = {
        replace(_.handler == old, newName, newHandler)
        this
    }

    /** Replaces the [[ChannelHandler]] of the specified name with a new handler in this pipeline.
     *
     *  @param old
     *    the name of the [[ChannelHandler]] to be replaced
     *  @param newName
     *    the name under which the replacement should be added
     *  @param newHandler
     *    the [[ChannelHandler]] which is used as replacement
     *  @return
     *    the removed handler
     *  @throws NoSuchElementException
     *    if the handler with the specified old name does not exist in this pipeline
     *  @throws IllegalArgumentException
     *    if a handler with the specified new name already exists in this pipeline, except for the handler to be
     *    replaced
     *  @throws NullPointerException
     *    if the specified old handler or new handler is `null`
     */
    override def replace(old: String, newName: Option[String], newHandler: ChannelHandler): ChannelHandler =
        replace(_.name == old, newName, newHandler)

    /** Replaces the [[ChannelHandler]] of the specified type with a new handler in this pipeline.
     *
     *  @param old
     *    the type of the handler to be removed
     *  @param newName
     *    the name under which the replacement should be added
     *  @param newHandler
     *    the [[ChannelHandler]] which is used as replacement
     *  @return
     *    the removed handler
     *  @throws NoSuchElementException
     *    if the handler of the specified old handler type does not exist in this pipeline
     *  @throws IllegalArgumentException
     *    if a handler with the specified new name already exists in this pipeline, except for the handler to be
     *    replaced
     *  @throws NullPointerException
     *    if the specified old handler or new handler is `null`
     */
    override def replace[T <: ChannelHandler](old: Class[T], newName: Option[String], newHandler: ChannelHandler): T =
        replace(ctx => old.isAssignableFrom(ctx.handler.getClass), newName, newHandler).asInstanceOf[T]

    private final def replace(
        predicate: ChannelHandlerContext => Boolean,
        newName: Option[String],
        newHandler: ChannelHandler
    ): ChannelHandler = {
        handlers.zipWithIndex.find((ctx, _) => predicate(ctx)) match
            case Some((old, index)) =>
                val newCtx = newContext(newName, newHandler)
            // TODO
            case None =>
        ???
    }

    /** Returns the context of the first [[ChannelHandler]] in this pipeline.
     *
     *  @return
     *    the context of the first handler. [[None]] if this pipeline is empty.
     */
    override def firstContext: Option[ChannelHandlerContext] = if (handlers.nonEmpty) Some(handlers(0)) else None

    /** Returns the context of the last [[ChannelHandler]] in this pipeline.
     *
     *  @return
     *    the context of the last handler. `null` if this pipeline is empty.
     */
    override def lastContext: Option[ChannelHandlerContext] =
        if (handlers.nonEmpty) Some(handlers(handlers.size - 1)) else None

    /** Returns the [[ChannelHandler]] with the specified name in this pipeline.
     *
     *  @return
     *    the handler with the specified name. [[None]] if there's no such handler in this pipeline.
     */
    override def get(name: String): Option[ChannelHandler] = context(name).map(_.handler)

    /** Returns the [[ChannelHandler]] of the specified type in this pipeline.
     *
     *  @return
     *    the handler of the specified handler type. `null` if there's no such handler in this pipeline.
     */
    override def get[T <: ChannelHandler](handlerType: Class[T]): Option[T] =
        context(handlerType).map(_.handler.asInstanceOf[T])

    /** Converts this pipeline into an [[Map]] whose keys are handler names and whose values are handlers. */
    override def toMap: Map[String, ChannelHandler] = handlers.map(ctx => ctx.name -> ctx.handler).toMap

    override def fireChannelRegistered(): this.type = {
        head.invokeChannelRegistered()
        this
    }

    override def fireChannelUnregistered(): this.type = {
        head.invokeChannelUnregistered()
        this
    }

    override def fireChannelActive(): this.type = {
        head.invokeChannelActive()
        this
    }

    override def fireChannelInactive(): this.type = {
        head.invokeChannelInactive()
        this
    }

    override def fireChannelShutdown(direction: ChannelShutdownDirection): this.type = {
        head.invokeChannelShutdown(direction)
        this
    }

    override def fireChannelExceptionCaught(cause: Throwable): this.type = {
        head.invokeChannelExceptionCaught(cause)
        this
    }

    override def fireChannelExceptionCaught(cause: Throwable, id: Long): ChannelPipelineImpl.this.type = {
        head.invokeChannelExceptionCaught(cause, id)
        this
    }

    override def fireChannelInboundEvent(event: AnyRef): this.type = {
        head.invokeChannelInboundEvent(event)
        this
    }

    override def fireChannelTimeoutEvent(id: Long): this.type = {
        head.invokeChannelTimeoutEvent(id)
        this
    }

    override def fireChannelRead(msg: AnyRef): this.type = {
        val ctx = findContextInbound(0, ChannelHandlerMask.MASK_CHANNEL_READ)
        try {
            ctx.handler.channelRead(ctx, msg)
        } catch {
            case t: Throwable => ctx.invokeChannelExceptionCaught(t)
        }
        this
    }

    override def fireChannelRead(msg: AnyRef, msgId: Long): this.type = {
        head.invokeChannelRead(msg, msgId)
        this
    }

    override def fireChannelReadComplete(): this.type = {
        head.invokeChannelReadComplete()
        this
    }

    override def fireChannelWritabilityChanged(): this.type = {
        head.invokeChannelWritabilityChanged()
        this
    }

    override def flush(): this.type = {
        tail.flush()
        this
    }

    override def read(readPlan: ReadPlan): this.type = {
        tail.read(readPlan)
        this
    }

    override def read(): this.type = {
        tail.read()
        this
    }

    override def bind(local: SocketAddress, future: ChannelFuture): ChannelFuture = {
        assertInExecutor()
        tail.bind(local, future)
    }

    override def connect(remote: SocketAddress, local: Option[SocketAddress], future: ChannelFuture): ChannelFuture = {
        assertInExecutor()
        tail.connect(remote, local, future)
    }

    override def open(ph: Path, opts: Seq[OpenOption], as: Seq[FileAttribute[?]], fu: ChannelFuture): ChannelFuture = {
        assertInExecutor()
        tail.open(ph, opts, as, fu)
    }

    override def disconnect(future: ChannelFuture): ChannelFuture = {
        assertInExecutor()
        tail.disconnect(future)
    }

    override def close(future: ChannelFuture): ChannelFuture = {
        assertInExecutor()
        tail.close(future)
    }

    override def shutdown(direction: ChannelShutdownDirection, future: ChannelFuture): ChannelFuture = {
        assertInExecutor()
        tail.shutdown(direction, future)
    }

    override def register(future: ChannelFuture): ChannelFuture = {
        assertInExecutor()
        tail.register(future)
    }

    override def deregister(future: ChannelFuture): ChannelFuture = {
        assertInExecutor()
        tail.deregister(future)
    }

    override def write(msg: AnyRef): Unit = {
        assertInExecutor()
        tail.write(msg)
    }

    override def write(msg: AnyRef, msgId: Long): Unit = {
        assertInExecutor()
        tail.write(msg, msgId)
    }

    override def writeAndFlush(msg: AnyRef): Unit = {
        assertInExecutor()
        tail.writeAndFlush(msg)
    }

    override def writeAndFlush(msg: AnyRef, msgId: Long): Unit = {
        assertInExecutor()
        tail.writeAndFlush(msg, msgId)
    }

    /** Send a custom outbound event via this [[ChannelOutboundInvoker]] through the [[ChannelPipeline]]. This will
     *  result in having the {{{ChannelHandler.sendOutboundEvent(ChannelHandlerContext, Object)}}} method called of the
     *  next [[ChannelHandler]] contained in the [[ChannelPipeline]] of the [[Channel]].
     *
     *  @param event
     */
    override def sendOutboundEvent(event: AnyRef): Unit = {
        assertInExecutor()
        tail.sendOutboundEvent(event)
    }

    final def forceCloseTransport(): Unit = {
        val abstractChannel = channel.asInstanceOf[AbstractChannel]
        abstractChannel.closeTransport(ChannelPromise())
    }

    override def executor: ChannelsActor[?] = channel.executor

    /** Called once a [[Throwable]] hit the end of the [[ChannelPipeline]] without been handled by the user in
     *  [[ChannelHandler.channelExceptionCaught()]].
     */
    private[core] def onUnhandledInboundException(cause: Throwable): Unit = {
        logger.warn(
          "An exceptionCaught() event was fired, and it reached at the tail of the pipeline. " +
              "It usually means the last handler in the pipeline did not handle the exception.",
          cause
        )
        Resource.dispose(cause)
    }

    /** Called once the [[pendingOutboundBytes]] were changed.
     *
     *  @param pendingOutboundBytes
     *    the new [[pendingOutboundBytes]].
     */
    private def pendingOutboundBytesUpdated(pendingOutboundBytes: Long): Unit = {
        val abstractChannel = channel.asInstanceOf[AbstractNetworkChannel]
        abstractChannel.updateWritabilityIfNeeded(true, false)
    }

    /** The number of the outbound bytes that are buffered / queued in this [[ChannelPipeline]]. This number will affect
     *  the writability of the [[Channel]] together the buffered / queued bytes in the [[Channel]] itself.
     *
     *  @return
     *    the number of buffered / queued bytes.
     */
    override def pendingOutboundBytes: Long = _pendingOutboundBytes

    /** Increments the [[pendingOutboundBytes]] of the pipeline by the given delta.
     *
     *  @param delta
     *    the number of bytes to add.
     */
    final def incrementPendingOutboundBytes(delta: Long): Unit = {
        assert(delta > 0)
        _pendingOutboundBytes += delta
        if (_pendingOutboundBytes < 0) {
            forceCloseTransport()
            throw new IllegalStateException("pendingOutboundBytes overflowed, force closed transport.")
        }
        pendingOutboundBytesUpdated(_pendingOutboundBytes)
    }

    /** Decrements the [[pendingOutboundBytes]] of the pipeline by the given delta.
     *
     *  @param delta
     *    the number of bytes to remove.
     */
    final def decrementPendingOutboundBytes(delta: Long): Unit = {
        assert(delta > 0)
        _pendingOutboundBytes -= delta
        if (_pendingOutboundBytes < 0) {
            forceCloseTransport()
            throw new IllegalStateException("pendingOutboundBytes underflowed, force closed transport.")
        }
        pendingOutboundBytesUpdated(_pendingOutboundBytes)
    }

    private def resetIndices(): Unit = handlers.indices.foreach(idx => handlers(idx).setIndex(idx))

    private[core] def findContextInbound(from: Int, mask: Int): ChannelHandlerContextImpl = {
        var cursor                         = from
        var ctx: ChannelHandlerContextImpl = null
        while {
            ctx = if (cursor < handlers.length) handlers(cursor) else tail
            cursor += 1
            (ctx.executionMask & mask) == 0
        } do ()

        ctx
    }

}

object ChannelPipelineImpl {

    private final class HeadHandler extends ChannelHandler {

        override def isBufferHandler: Boolean = true

        override def bind(ctx: ChannelHandlerContext, local: SocketAddress, future: ChannelFuture): ChannelFuture = {
            val abstractChannel: AbstractChannel = ctx.channel.asInstanceOf[AbstractChannel]
            abstractChannel.bindTransport(local, future.promise)
            future
        }

        override def connect(
            ctx: ChannelHandlerContext,
            remote: SocketAddress,
            local: Option[SocketAddress],
            future: ChannelFuture
        ): ChannelFuture = {
            val abstractChannel: AbstractChannel = ctx.channel.asInstanceOf[AbstractChannel]
            abstractChannel.connectTransport(remote, local, future.promise)
            future
        }

        override def open(
            ctx: ChannelHandlerContext,
            path: Path,
            options: Seq[OpenOption],
            attrs: Seq[FileAttribute[?]],
            future: ChannelFuture
        ): ChannelFuture = {
            val abstractChannel = ctx.channel.asInstanceOf[AbstractChannel]
            abstractChannel.openTransport(path, options, attrs, future.promise)
            future
        }

        override def disconnect(ctx: ChannelHandlerContext, future: ChannelFuture): ChannelFuture = {
            val abstractChannel: AbstractChannel = ctx.channel.asInstanceOf[AbstractChannel]
            abstractChannel.disconnectTransport(future.promise)
            future
        }

        override def close(ctx: ChannelHandlerContext, future: ChannelFuture): ChannelFuture = {
            val abstractChannel: AbstractChannel = ctx.channel.asInstanceOf[AbstractChannel]
            abstractChannel.closeTransport(future.promise)
            future
        }

        override def shutdown(
            ctx: ChannelHandlerContext,
            direction: ChannelShutdownDirection,
            future: ChannelFuture
        ): ChannelFuture = {
            val abstractChannel: AbstractChannel = ctx.channel.asInstanceOf[AbstractChannel]
            abstractChannel.shutdownTransport(direction, future.promise)
            future
        }

        override def register(ctx: ChannelHandlerContext, future: ChannelFuture): ChannelFuture = {
            val abstractChannel: AbstractChannel = ctx.channel.asInstanceOf[AbstractChannel]
            abstractChannel.registerTransport(future.promise)
            future
        }

        override def deregister(ctx: ChannelHandlerContext, future: ChannelFuture): ChannelFuture = {
            val abstractChannel: AbstractChannel = ctx.channel.asInstanceOf[AbstractChannel]
            abstractChannel.deregisterTransport(future.promise)
            future
        }

        override def read(ctx: ChannelHandlerContext, readPlan: ReadPlan): Unit = {
            val abstractChannel: AbstractChannel = ctx.channel.asInstanceOf[AbstractChannel]
            abstractChannel.readTransport(readPlan)
        }

        override def write(ctx: ChannelHandlerContext, msg: AnyRef): Unit = {
            val abstractChannel: AbstractChannel = ctx.channel.asInstanceOf[AbstractChannel]
            abstractChannel.writeTransport(msg)
        }

        override def write(ctx: ChannelHandlerContext, msg: AnyRef, msgId: Long): Unit = write(ctx, msg)

        override def flush(ctx: ChannelHandlerContext): Unit = {
            val abstractChannel: AbstractChannel = ctx.channel.asInstanceOf[AbstractChannel]
            abstractChannel.flushTransport()
        }

        override def sendOutboundEvent(ctx: ChannelHandlerContext, event: AnyRef): Unit = ???

    }

    private final class TailHandler extends ChannelHandler {

        override def channelRegistered(ctx: ChannelHandlerContext): Unit = {} // Just swallow event

        override def channelUnregistered(ctx: ChannelHandlerContext): Unit = {} // Just swallow event

        override def channelActive(ctx: ChannelHandlerContext): Unit = {} // Just swallow event

        override def channelInactive(ctx: ChannelHandlerContext): Unit = {} // Just swallow event

        override def channelShutdown(ctx: ChannelHandlerContext, direction: ChannelShutdownDirection): Unit = {
            // Just swallow event
        }

        override def channelWritabilityChanged(ctx: ChannelHandlerContext): Unit = {} // Just swallow event

        override def channelInboundEvent(ctx: ChannelHandlerContext, evt: AnyRef): Unit = {
            Resource.dispose(evt)
        }

        override def channelTimeoutEvent(ctx: ChannelHandlerContext, id: Long): Unit = {} // Just swallow event

        override def channelExceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit =
            cause.printStackTrace()

        override def channelExceptionCaught(ctx: ChannelHandlerContext, cause: Throwable, id: Long): Unit =
            ctx.channel.onInboundMessage(cause, true, id)

        override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef): Unit =
            ctx.channel.onInboundMessage(msg, false)

        override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef, msgId: Long): Unit =
            ctx.channel.onInboundMessage(msg, false, msgId)

        override def channelReadComplete(ctx: ChannelHandlerContext): Unit = {} // Just swallow event

    }

    private val HEAD_NAME = generateName0(classOf[HeadHandler])
    private val TAIL_NAME = generateName0(classOf[TailHandler])

    private val HEAD_HANDLER = new HeadHandler
    private val TAIL_HANDLER = new TailHandler

    final val DEFAULT_READ_PLAN: ReadPlan = new FixedReadPlan(Int.MaxValue, 4096)

    private def generateName0(handlerType: Class[?]) = ClassUtils.simpleClassName(handlerType) + "#0"

    private final val nameCaches: ActorThreadLocal[collection.mutable.HashMap[Class[?], String]] =
        new ActorThreadLocal[mutable.HashMap[Class[?], String]] {
            override protected def initialValue(): mutable.HashMap[Class[?], String] = collection.mutable.HashMap.empty
        }

    private def checkMultiplicity(handler: ChannelHandler): Unit = handler match {
        case h: ChannelHandlerAdapter =>
            if (!h.isSharable && h.added)
                throw new RuntimeException(
                  s"${h.getClass.getName} is not a @Sharable handler, so can't be added or removed multiple times."
                )
            h.added = true
        case _ =>
    }

}
