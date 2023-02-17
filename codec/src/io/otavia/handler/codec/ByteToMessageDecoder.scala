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

package io.otavia.handler.codec

import io.netty5.buffer.internal.AdaptableBuffer
import io.netty5.buffer.{Buffer, BufferAllocator, BufferStub, CompositeBuffer}
import io.netty5.util.Send
import io.netty5.util.internal.{MathUtil, StringUtil}
import io.otavia.core.channel.*
import io.otavia.core.channel.internal.DelegatingChannelHandlerContext
import io.otavia.core.util.CompressionBooleanInt
import io.otavia.handler.codec.ByteToMessageDecoder.*
import io.otavia.handler.codec.ByteToMessageDecoder.CompositeBufferCumulator.prepareInForCompose
import io.otavia.handler.codec.ByteToMessageDecoder.MergeCumulator.expandAccumulationAndWrite

import java.util

/** [[ChannelHandler]] which decodes bytes in a stream-like fashion from one [[Buffer]] to an other Message type. For
 *  example here is an implementation which reads all readable bytes from the input [[Buffer]], creates a new [[Buffer]]
 *  and forward it to the next [[ChannelHandler]] in the [[ChannelPipeline]].
 *
 *  {{{
 *      class SquareDecoder extends ByteToMessageDecoder {
 *          override protected def decode(ctx: ChannelHandlerContext, in: Buffer): Unit = {
 *              val data = new Array[Byte](in.readableBytes())
 *              in.readBytes(data, 0, data.length)
 *              ctx.fireChannelRead(data)
 *          }
 *      }
 *  }}}
 *
 *  <h3>Frame detection</h3>
 *
 *  Generally frame detection should be handled earlier in the pipeline by adding a [[DelimiterBasedFrameDecoder]],
 *  [[FixedLengthFrameDecoder]], [[LengthFieldBasedFrameDecoder]], or [[LineBasedFrameDecoder]].
 *
 *  If a custom frame decoder is required, then one needs to be careful when implementing one with
 *  [[ByteToMessageDecoder]]. Ensure there are enough bytes in the buffer for a complete frame by checking
 *  Buffer.readableBytes(). If there are not enough bytes for a complete frame, return without modifying the reader
 *  index to allow more bytes to arrive.
 *
 *  To check for complete frames without modifying the reader index, use methods like [[Buffer.getInt]]. One
 *  <strong>MUST</strong> use the reader index when using methods like [[Buffer.getInt(int)]]. For example calling
 *  in.getInt(0) is assuming the frame starts at the beginning of the buffer, which is not always the case. Use
 *  in.getInt(in.readerIndex()) instead.
 *
 *  @param cumulator
 *    Buffer [[Cumulator]]
 */
abstract class ByteToMessageDecoder(private val cumulator: Cumulator)
    extends ChannelHandlerAdapter
    with CompressionBooleanInt {

    def this() = this(COMPOSITE_CUMULATOR)

    private def singleDecode_=(value: Boolean): Unit = setAt(0, value)
    private def singleDecode: Boolean                = getAt(0)

    private def first_=(value: Boolean): Unit = setAt(1, value)
    private def first: Boolean                = getAt(1)

    private def firedChannelRead_=(value: Boolean): Unit = setAt(2, value)
    private def firedChannelRead: Boolean                = getAt(2)

    private def discardAfterReads = 16

    private var accumulation: Buffer | Null = null

    /** This flag is used to determine if we need to call [[ChannelOutboundInvoker.read]] to consume more data when
     *  [[io.otavia.core.channel.ChannelOption.AUTO_READ]] is false.
     */
    private var numReads                                    = 0
    private var context: ByteToMessageDecoderContext | Null = null

    override final def isSharable: Boolean = false

    /** If set then only one message is decoded on each [[channelRead]] call. This may be useful if you need to do some
     *  protocol upgrade and want to make sure nothing is mixed up. Default is false as this has performance impacts.
     */
    def setSingleDecode(single: Boolean): Unit = singleDecode = single

    /** If true then only one message is decoded on each [[channelRead]] call. Default is false as this has performance
     *  impacts.
     */
    def isSingleDecode: Boolean = singleDecode

    /** Returns the actual number of readable bytes in the internal cumulative buffer of this decoder. You usually do
     *  not need to rely on this value to write a decoder. Use it only when you must use it at your own risk. This
     *  method is a shortcut to [[internalBuffer.nn.readableBytes]].
     */
    protected def actualReadableBytes: Int = internalBuffer.nn.readableBytes()

    /** Returns the internal cumulative buffer of this decoder, if exists, else null. You usually do not need to access
     *  the internal buffer directly to write a decoder. Use it only when you must use it at your own risk.
     *
     *  @return
     *    Internal Buffer if exists, else null.
     */
    protected def internalBuffer: Buffer | Null = accumulation

    override final def handlerAdded(ctx: ChannelHandlerContext): Unit = {
        context = new ByteToMessageDecoderContext(ctx)
        handlerAdded0(context.nn)
    }

    @throws[Exception]
    protected def handlerAdded0(ctx: ChannelHandlerContext): Unit = {}

    override def handlerRemoved(ctx: ChannelHandlerContext): Unit = {
        accumulation match
            case buffer: Buffer =>
                accumulation = null
                numReads = 0
                val readable = buffer.readableBytes()
                if (readable > 0) {
                    ctx.fireChannelRead(buffer)
                    ctx.fireChannelReadComplete()
                } else buffer.close()

            case null: Null =>

        handlerRemoved0(context.nn)
    }

    /** Gets called after the [[ByteToMessageDecoder]] was removed from the actual context and it doesn't handle events
     *  anymore.
     */
    @throws[Exception]
    protected def handlerRemoved0(ctx: ChannelHandlerContext): Unit = {}

    override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef): Unit = msg match
        case buffer: Buffer =>
            first = accumulation == null
            if (first) accumulation = buffer
            else accumulation = cumulator.cumulate(ctx.directAllocator(), accumulation.nn, buffer)
            try {
                assertContext(ctx)
                callDecode(context, accumulation.nn)
            } catch {
                case e: DecoderException => throw e
                case e: Exception        => throw new DecoderException(e)
            } finally {
                if (accumulation != null && accumulation.nn.readableBytes() == 0) {
                    numReads = 0
                    if (accumulation.nn.isAccessible) accumulation.nn.close()
                    accumulation = null
                } else if (numReads + 1 > discardAfterReads) {
                    // We did enough reads already try to discard some bytes so we not risk to see a OOME.
                    // See https://github.com/netty/netty/issues/4275
                    numReads = 0
                    discardSomeReadBytes()
                }
                fireChannelRead = fireChannelRead | (context.fireChannelReadCallCount() > 0)
                context.reset()
            }

        case _ => ctx.fireChannelRead(msg)

    override def channelReadComplete(ctx: ChannelHandlerContext): Unit = {
        numReads = 0
        discardSomeReadBytes()
        if (!firedChannelRead && !ctx.channel.getOption(ChannelOption.AUTO_READ)) ctx.read()
        firedChannelRead = false
        ctx.fireChannelReadComplete()
    }

    protected final def discardSomeReadBytes(): Unit = accumulation match
        case buffer: Buffer if !first => cumulator.discardSomeReadBytes(buffer)
        case _                        =>

    private inline def assertContext(ctx: ChannelHandlerContext): Unit =
        assert(context.nn.delegatingCtx == ctx || ctx == context)

    override def channelInactive(ctx: ChannelHandlerContext): Unit = {
        assertContext(ctx)
        channelInputClosed(context.nn, true)
    }

    override def channelShutdown(ctx: ChannelHandlerContext, direction: ChannelShutdownDirection): Unit = {
        ctx.fireChannelShutdown(direction)
        if (direction == ChannelShutdownDirection.Inbound) {
            // The decodeLast method is invoked when a channelInactive event is encountered.
            // This method is responsible for ending requests in some situations and must be called
            // when the input has been shutdown.
            assertContext(ctx)
            channelInputClosed(context.nn, false)
        }
    }

    private def channelInputClosed(ctx: ByteToMessageDecoderContext, callChannelInactive: Boolean): Unit = try {
        channelInputClosed(ctx)
    } catch {
        case e: DecoderException => throw e
        case e: Exception        => throw new DecoderException(e)
        case _                   =>
    } finally {
        accumulation match
            case buf: Buffer => buf.close()
            case null: Null  =>
        accumulation = null
        if (ctx.fireChannelReadCallCount > 0) {
            ctx.reset()
            ctx.fireChannelReadComplete()
        }
        if (callChannelInactive) ctx.fireChannelInactive()
    }

    @throws[Exception]
    def channelInputClosed(ctx: ByteToMessageDecoderContext): Unit = accumulation match
        case null: Null =>
            val buffer = ctx.directAllocator().allocate(0)
            decodeLast(ctx, buffer)
            buffer.close()
        case buffer: Buffer =>
            callDecode(ctx, buffer)
            // If callDecode(...) removed the handle from the pipeline we should not call decodeLast(...) as this would
            // be unexpected.
            if (!ctx.isRemoved) {
                // Use Unpooled.EMPTY_BUFFER if accumulation become null after calling callDecode(...).
                // See https://github.com/netty/netty/issues/10802.
                accumulation match
                    case null: Null =>
                        val buffer = ctx.directAllocator().allocate(0)
                        decodeLast(ctx, buffer)
                        buffer.close()
                    case _ => decodeLast(ctx, buffer)
            }

    /** Called once data should be decoded from the given [[Buffer]]. This method will call [[decode]] as long as
     *  decoding should take place.
     *  @param ctx
     *    the [[ChannelHandlerContext]] which this [[ByteToMessageDecoder]] belongs to
     *  @param in
     *    the Buffer from which to read data
     */
    def callDecode(ctx: ByteToMessageDecoderContext, in: Buffer): Unit = try {
        var continue: Boolean = true
        while (in.readableBytes() > 0 && !ctx.isRemoved && continue) {
            val oldLen     = in.readableBytes()
            val readCalled = ctx.fireChannelReadCallCount
            decodeRemovalReentryProtection(ctx, in) // try decode

            // Check if this handler was removed before continuing the loop.
            // If it was removed, it is not safe to continue to operate on the buffer.
            //
            // See https://github.com/netty/netty/issues/1664
            if (!ctx.isRemoved) {
                if (readCalled != ctx.fireChannelReadCallCount) { // sub class fireChannelRead in decode.
                    if (oldLen == in.readableBytes()) {           // fire but not consumed any data.
                        val clzName = StringUtil.simpleClassName(getClass)
                        val msg     = s"$clzName.decode() did not read anything but decoded a message."
                        throw new DecoderException(msg)
                    }
                    if (isSingleDecode) continue = false
                } else {                                               // sub class not fireChannelRead in decode.
                    if (oldLen == in.readableBytes()) continue = false // sub class decode not consumed any data.
                    else continue = true                               // sub class decode skip some data.
                }
            } else continue = false
        }
    } catch {
        case e: DecoderException => throw e
        case cause: Exception    => throw new DecoderException(cause)
        case _                   => ()
    }

    /** Decode the from one [[Buffer]] to another. This method will be called till either the input [[Buffer]] has
     *  nothing to read when return from this method or till nothing was read from the input [[Buffer]].
     *  @param ctx
     *    the [[ChannelHandlerContext]] which this [[ByteToMessageDecoder]] belongs to
     *  @param in
     *    the [[Buffer]] from which to read data
     *  @throws Exception
     *    is thrown if an error occurs
     */
    @throws[Exception]
    protected def decode(ctx: ChannelHandlerContext, in: Buffer): Unit

    /** Decode the from one [[Buffer]] to an other. This method will be called till either the input [[Buffer]] has
     *  nothing to read when return from this method or till nothing was read from the input [[Buffer]].
     *  @param ctx
     *    the [[ChannelHandlerContext]] which this [[ByteToMessageDecoder]] belongs to
     *  @param in
     *    the [[Buffer]] from which to read data
     *  @throws Exception
     *    is thrown if an error occurs
     */
    @throws[Exception]
    final def decodeRemovalReentryProtection(ctx: ChannelHandlerContext, in: Buffer): Unit = decode(ctx, in)

    /** Is called one last time when the [[ChannelHandlerContext]] goes in-active. Which means the [[channelInactive]]
     *  was triggered. By default this will just call [[decode]] but sub-classes may override this for some special
     *  cleanup operation.
     */
    protected def decodeLast(ctx: ChannelHandlerContext, in: Buffer): Unit = if (in.readableBytes() > 0)
        // Only call decode() if there is something left in the buffer to decode.
        // See https://github.com/netty/netty/issues/4386
        decodeRemovalReentryProtection(ctx, in)

}

object ByteToMessageDecoder {

    final val MERGE_CUMULATOR: Cumulator = new MergeCumulator()

    final val COMPOSITE_CUMULATOR: Cumulator = new CompositeBufferCumulator()

    final class ByteToMessageDecoderContext(ctx: ChannelHandlerContext) extends DelegatingChannelHandlerContext(ctx) {

        private var fireChannelReadCalled: Int = 0

        def reset(): Unit = fireChannelReadCalled = 0

        def fireChannelReadCallCount: Int = fireChannelReadCalled

        override def fireChannelRead(msg: AnyRef): ChannelHandlerContext = {
            fireChannelReadCalled += 1
            super.fireChannelRead(msg)
            this
        }

    }
    trait Cumulator {

        /** Cumulate the given [[Buffer]]s and return the [[Buffer]] that holds the cumulated bytes. The implementation
         *  is responsible to correctly handle the life-cycle of the given [[Buffer]]s and so call [[Buffer.close]] if a
         *  [[Buffer]] is fully consumed.
         */
        def cumulate(alloc: BufferAllocator, accumulation: Buffer, in: Buffer): Buffer

        /** Consume the given buffer and return a new buffer with the same readable data, but where any data before the
         *  read offset may have been removed. The returned buffer may be the same buffer instance as the buffer passed
         *  in.
         *
         *  @param accumulation
         *    The buffer we wish to trim already processed bytes from.
         *  @return
         *    A buffer where the bytes before the reader-offset have been removed.
         */
        def discardSomeReadBytes(accumulation: Buffer): Buffer

    }

    private final class CompositeBufferCumulator extends Cumulator {

        override def cumulate(alloc: BufferAllocator, accumulation: Buffer, in: Buffer): Buffer =
            if (accumulation.readableBytes() == 0) {
                accumulation.close()
                in
            } else if (in.readableBytes() == 0) {
                in.close()
                accumulation
            } else {
                val buffer = if (accumulation.readOnly()) {
                    val tmp = accumulation.copy()
                    accumulation.close()
                    tmp
                } else accumulation
                val composite = buffer match
                    case composite: CompositeBuffer =>
                        composite.extendWith(prepareInForCompose(in))
                        composite
                    case _ =>
                        alloc.compose(util.Arrays.asList(buffer.send(), prepareInForCompose(in)))
                in.close()
                composite
            }

        override def discardSomeReadBytes(accumulation: Buffer): Buffer = {
            // Compact is slow on composite buffers, and we also need to avoid leaving any writable space at the end.
            // Using readSplit(0), we grab zero readable bytes in the split-off buffer, but all the already-read
            // bytes get cut off from the accumulation buffer.

            accumulation.readSplit(0).close()
            accumulation

        }
        override def toString: String = "CompositeBufferCumulator"

    }

    object CompositeBufferCumulator {
        private def prepareInForCompose(in: Buffer): Send[Buffer] = if (in.readOnly()) in.copy().send() else in.send()

    }

    private final class MergeCumulator extends Cumulator {

        override def cumulate(alloc: BufferAllocator, accumulation: Buffer, in: Buffer): Buffer =
            if (accumulation.readableBytes() == 0) {
                accumulation.close()
                in
            } else {
                val required = in.readableBytes()
                if (required > accumulation.writableBytes() || accumulation.readOnly())
                    expandAccumulationAndWrite(alloc, accumulation, in)
                else {
                    accumulation.writeBytes(in)
                    in.close()
                    accumulation
                }
            }

        override def discardSomeReadBytes(accumulation: Buffer): Buffer = {
            if (accumulation.readerOffset() > accumulation.writableBytes()) accumulation.compact()
            accumulation
        }

        override def toString: String = "MergeCumulator"

    }

    object MergeCumulator {
        private def expandAccumulationAndWrite(allocator: BufferAllocator, oldAcc: Buffer, in: Buffer): Buffer = {
            val newSize = MathUtil.safeFindNextPositivePowerOfTwo(oldAcc.readableBytes() + in.readableBytes())

            if (oldAcc.readOnly()) {
                val newAcc = allocator.allocate(newSize)
                newAcc.writeBytes(oldAcc)
                oldAcc.close()
                newAcc.writeBytes(in)
                in.close()
                newAcc
            } else {
                oldAcc.ensureWritable(newSize)
                oldAcc.writeBytes(in)
                in.close()
                oldAcc
            }
        }
    }

}
