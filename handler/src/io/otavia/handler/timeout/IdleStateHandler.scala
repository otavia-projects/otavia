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

package io.otavia.handler.timeout

import io.otavia.core.channel.{Channel, ChannelHandler, ChannelHandlerContext}
import io.otavia.core.timer.Timer
import io.otavia.core.timer.TimeoutTrigger
import io.otavia.handler.timeout.IdleState.{ALL_IDLE, READER_IDLE, WRITER_IDLE}
import io.otavia.handler.timeout.IdleStateHandler.MIN_TIMEOUT_NANOS

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.MILLISECONDS

/** Triggers an [[IdleStateEvent]] when a [[Channel]] has not performed read, write, or both operation for a while.
 *
 *  <h3>Supported idle states</h3> <table border="1"> <tr> <th>Property</th><th>Meaning</th> </tr> <tr>
 *  <td>[[readerIdleTime]] </td> <td>an [[IdleStateEvent]] whose state is [[IdleState.READER_IDLE]] will be triggered
 *  when no read was performed for the specified period of time. Specify 0 to disable.</td> </tr> <tr>
 *  <td>[[writerIdleTime]]</td> <td>an [[IdleStateEvent]] whose state is [[IdleState.WRITER_IDLE]] will be triggered
 *  when no write was performed for the specified period of time. Specify 0 to disable.</td> </tr> <tr>
 *  <td>[[allIdleTime]]</td> <td>an [[IdleStateEvent]] whose state is [[IdleState.ALL_IDLE]] will be triggered when
 *  neither read nor write was performed for the specified period of time. Specify 0 to disable.</td> </tr> </table>
 *
 *  <pre>An example that sends a ping message when there is no outbound traffic for 30 seconds. The connection is closed
 *  when there is no inbound traffic for 60 seconds. </pre>
 *
 *  {{{
 *  // An example that sends a ping message when there is no outbound traffic
 *  // for 30 seconds.  The connection is closed when there is no inbound traffic
 *  // for 60 seconds.
 *  class MyChannelInitializer extends ChannelInitializer[Channel] {
 *        override protected def initChannel(ch: Channel): Unit = {
 *            ch.pipeline.addLast("idleStateHandler", new IdleStateHandler(60, 30, 0))
 *            ch.pipeline.addLast("myHandler", new MyHandler())
 *        }
 *  }
 *
 *  class MyHandler extends ChannelHandler {
 *      override def channelInboundEvent(ctx: ChannelHandlerContext, evt: AnyRef): Unit = {
 *          evt match
 *              case event: IdleStateEvent =>
 *                  if (event.state == IdleState.READER_IDLE)
 *                      ctx.close()
 *                  else if (event.state == IdleState.WRITER_IDLE)
 *                      ctx.writeAndFlush(new PingMessage())
 *              case _ =>
 *     }
 *  }
 *  }}}
 *
 *  @param readerIdleTime
 *    an [[IdleStateEvent]] whose state is [[IdleState.READER_IDLE]] will be triggered when no read was performed for
 *    the specified period of time. Specify 0 to disable.
 *  @param writerIdleTime
 *    an [[IdleStateEvent]] whose state is [[IdleState.WRITER_IDLE]] will be triggered when no write was performed for
 *    the specified period of time. Specify 0 to disable.
 *  @param allIdleTime
 *    an [[IdleStateEvent]] whose state is [[IdleState.ALL_IDLE]] will be triggered when neither read nor write was
 *    performed for the specified period of time. Specify 0 to disable.
 *  @param unit
 *    the [[TimeUnit]] of [[readerIdleTime]], [[writeIdleTime]], and [[allIdleTime]]
 */
class IdleStateHandler(
    readerIdleTime: Long,
    writerIdleTime: Long,
    allIdleTime: Long,
    unit: TimeUnit = TimeUnit.SECONDS
) extends ChannelHandler {

    private val readerIdleTimeNanos: Long =
        if (readerIdleTime <= 0) 0 else math.max(unit.toNanos(readerIdleTime), MIN_TIMEOUT_NANOS)

    private val writerIdleTimeNanos: Long =
        if (writerIdleTime <= 0) 0 else math.max(unit.toNanos(writerIdleTime), MIN_TIMEOUT_NANOS)

    private val allIdleTimeNanos: Long =
        if (allIdleTime <= 0) 0 else math.max(unit.toNanos(allIdleTime), MIN_TIMEOUT_NANOS)

    private var readerIdleRegisterId: Long = Timer.INVALID_TIMEOUT_REGISTER_ID
    private var lastReadTime               = 0L
    private var firstReaderIdleEvent       = true

    private var writerIdleRegisterId: Long = Timer.INVALID_TIMEOUT_REGISTER_ID
    private var lastWriteTime              = 0L
    private var firstWriterIdleEvent       = true

    private var allIdleRegisterId: Long = Timer.INVALID_TIMEOUT_REGISTER_ID
    private var firstAllIdleEvent       = true

    private var state   = 0 // 0 - none, 1 - initialized, 2 - destroyed
    private var reading = false

    def getReaderIdleTimeInMillis(): Long = TimeUnit.NANOSECONDS.toMillis(readerIdleTimeNanos)

    def getWriterIdleTimeInMillis(): Long = TimeUnit.NANOSECONDS.toMillis(writerIdleTimeNanos)

    def getAllIdleTimeInMillis(): Long = TimeUnit.NANOSECONDS.toMillis(allIdleTimeNanos)

    override def handlerAdded(ctx: ChannelHandlerContext): Unit =
        if (ctx.channel.isActive && ctx.channel.isRegistered) {
            // channelActive() event has been fired already, which means this.channelActive() will
            // not be invoked. We have to initialize here instead.
            initialize(ctx)
        } else {
            // channelActive() event has not been fired yet.  this.channelActive() will be invoked
            // and initialization will occur there.
        }

    override def handlerRemoved(ctx: ChannelHandlerContext): Unit = destroy(ctx)

    override def channelRegistered(ctx: ChannelHandlerContext): Unit = {
        // Initialize early if channel is active already.
        if (ctx.channel.isActive) initialize(ctx)

        ctx.fireChannelRegistered()
    }

    override def channelActive(ctx: ChannelHandlerContext): Unit = {
        initialize(ctx)
        ctx.fireChannelActive()
    }

    override def channelInactive(ctx: ChannelHandlerContext): Unit = {
        destroy(ctx)
        ctx.fireChannelInactive()
    }

    override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef): Unit = {
        if (readerIdleTimeNanos > 0 || writerIdleTimeNanos > 0) {
            reading = true
            firstReaderIdleEvent = true
            firstAllIdleEvent = true
            updateReaderIdleTimerTask(ctx)
            updateAllIdleTimerTask(ctx)
        }
        ctx.fireChannelRead(msg)
    }

    override def channelReadComplete(ctx: ChannelHandlerContext): Unit = {
        if ((readerIdleTimeNanos > 0 || allIdleTimeNanos > 0) && reading) {
            lastReadTime = ticksInNanos()
            reading = false
            updateReaderIdleTimerTask(ctx)
            updateAllIdleTimerTask(ctx)
        }
        ctx.fireChannelReadComplete()
    }

    override def write(ctx: ChannelHandlerContext, msg: AnyRef): Unit = {
        ctx.write(ctx, msg)
        if (writerIdleTimeNanos > 0 || allIdleTimeNanos > 0) {
            lastWriteTime = ticksInNanos()
            firstWriterIdleEvent = true
            firstAllIdleEvent = true
            updateWriterIdleTimerTask(ctx)
            updateAllIdleTimerTask(ctx)
        }
    }

    override def channelTimeoutEvent(ctx: ChannelHandlerContext, id: Long): Unit = {
        if (id == readerIdleRegisterId) {
            registerReaderIdle(ctx)
            val first = firstReaderIdleEvent
            firstReaderIdleEvent = false
            try {
                val event = newIdleStateEvent(IdleState.READER_IDLE, first)
                channelIdle(ctx, event)
            } catch { case t: Throwable => ctx.fireChannelExceptionCaught(t) }
        } else if (id == writerIdleRegisterId) {
            registerWriterIdle(ctx)
            val first = firstWriterIdleEvent
            firstWriterIdleEvent = false
            try {
                val event = newIdleStateEvent(IdleState.WRITER_IDLE, first)
                channelIdle(ctx, event)
            } catch { case t: Throwable => ctx.fireChannelExceptionCaught(t) }
        } else if (id == allIdleRegisterId) {
            registerAllIdle(ctx)
            val first = firstAllIdleEvent
            firstAllIdleEvent = false
            try {
                val event = newIdleStateEvent(IdleState.ALL_IDLE, first)
                channelIdle(ctx, first)
            } catch { case t: Throwable => ctx.fireChannelExceptionCaught(t) }
        } else ctx.fireChannelTimeoutEvent(id)
    }

    private def initialize(ctx: ChannelHandlerContext): Unit = if (state != 1 && state != 2) {
        state = 1
        lastReadTime = ticksInNanos()
        lastWriteTime = ticksInNanos()
        initialTimer(ctx)
    }

    private def updateReaderIdleTimerTask(ctx: ChannelHandlerContext): Unit = if (state == 1) {
        val nextDelay = if (!reading) readerIdleTimeNanos - (ticksInNanos() - lastReadTime) else readerIdleTimeNanos
        if (nextDelay <= 0) {
            registerReaderIdle(ctx) // register a new timer task.
        } else {
            val trigger = TimeoutTrigger.DelayTime(nextDelay, TimeUnit.NANOSECONDS)
            ctx.timer.updateTimerTask(trigger, readerIdleRegisterId)
        }
    }

    private def updateWriterIdleTimerTask(ctx: ChannelHandlerContext): Unit = {
        val nextDelay = writerIdleTimeNanos - (ticksInNanos() - lastWriteTime)
        if (nextDelay <= 0) registerWriterIdle(ctx) // register a new timer task.
        else {
            val trigger = TimeoutTrigger.DelayTime(nextDelay, TimeUnit.NANOSECONDS)
            ctx.timer.updateTimerTask(trigger, writerIdleRegisterId)
        }
    }

    private def updateAllIdleTimerTask(ctx: ChannelHandlerContext): Unit = {
        val nextDelay = allIdleTimeNanos - (ticksInNanos() - math.max(lastReadTime, lastWriteTime))
        if (nextDelay <= 0) registerAllIdle(ctx) // register a new timer task.
        else {
            val trigger = TimeoutTrigger.DelayTime(nextDelay, TimeUnit.NANOSECONDS)
            ctx.timer.updateTimerTask(trigger, allIdleRegisterId)
        }
    }

    private def destroy(ctx: ChannelHandlerContext): Unit = {
        state = 2
        cancelTimer(ctx)
    }

    private def initialTimer(ctx: ChannelHandlerContext): Unit = {
        registerReaderIdle(ctx)
        registerWriterIdle(ctx)
        registerAllIdle(ctx)
    }

    private def registerReaderIdle(ctx: ChannelHandlerContext): Unit = if (readerIdleTimeNanos > 0)
        readerIdleRegisterId = ctx.timer.registerChannelTimeout(
          TimeoutTrigger.DelayTime(readerIdleTimeNanos, TimeUnit.NANOSECONDS),
          ctx.channel
        )

    private def registerWriterIdle(ctx: ChannelHandlerContext): Unit = if (writerIdleTimeNanos > 0)
        writerIdleRegisterId =
            ctx.timer.registerChannelTimeout(TimeoutTrigger.DelayTime(writerIdleTimeNanos, TimeUnit.NANOSECONDS))

    private def registerAllIdle(ctx: ChannelHandlerContext): Unit = if (allIdleTimeNanos > 0)
        allIdleRegisterId =
            ctx.timer.registerChannelTimeout(TimeoutTrigger.DelayTime(allIdleTimeNanos, TimeUnit.NANOSECONDS))

    private def cancelTimer(ctx: ChannelHandlerContext): Unit = {
        if (readerIdleRegisterId != Timer.INVALID_TIMEOUT_REGISTER_ID) {
            ctx.timer.cancelTimerTask(readerIdleRegisterId)
            readerIdleRegisterId = Timer.INVALID_TIMEOUT_REGISTER_ID
        }

        if (writerIdleRegisterId != Timer.INVALID_TIMEOUT_REGISTER_ID) {
            ctx.timer.cancelTimerTask(writerIdleRegisterId)
            writerIdleRegisterId = Timer.INVALID_TIMEOUT_REGISTER_ID
        }

        if (allIdleRegisterId != Timer.INVALID_TIMEOUT_REGISTER_ID) {
            ctx.timer.cancelTimerTask(allIdleRegisterId)
            allIdleRegisterId = Timer.INVALID_TIMEOUT_REGISTER_ID
        }
    }

    def ticksInNanos(): Long = System.nanoTime()

    /** Is called when an [[IdleStateEvent]] should be fired. This implementation calls
     *  [[ChannelHandlerContext.fireChannelInboundEvent]] .
     */
    protected def channelIdle(ctx: ChannelHandlerContext, evt: IdleStateEvent): Unit =
        ctx.fireChannelInboundEvent(evt)

    protected def newIdleStateEvent(state: IdleState, first: Boolean): IdleStateEvent = state match
        case READER_IDLE =>
            if (first) IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT else IdleStateEvent.READER_IDLE_STATE_EVENT
        case WRITER_IDLE =>
            if (first) IdleStateEvent.FIRST_WRITER_IDLE_STATE_EVENT else IdleStateEvent.WRITER_IDLE_STATE_EVENT
        case ALL_IDLE =>
            if (first) IdleStateEvent.FIRST_ALL_IDLE_STATE_EVENT else IdleStateEvent.ALL_IDLE_STATE_EVENT

}

object IdleStateHandler {
    private val MIN_TIMEOUT_NANOS: Long = MILLISECONDS.toNanos(1)
}
