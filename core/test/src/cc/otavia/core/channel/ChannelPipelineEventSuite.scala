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

package cc.otavia.core.channel

import cc.otavia.core.channel.ChannelPipelineEventSuite.*
import cc.otavia.core.channel.ChannelPipelineSuite.{RecordingHandler, StubChannel}
import org.scalatest.funsuite.AnyFunSuiteLike

import scala.language.unsafeNulls

class ChannelPipelineEventSuite extends AnyFunSuiteLike {

    private def newPipeline(): ChannelPipelineImpl = new StubChannel().pipeline

    private def addHandlers(n: Int): (ChannelPipelineImpl, Seq[RecordingHandler]) = {
        val p = newPipeline()
        val hs = (0 until n).map(i => { val h = new RecordingHandler; p.addLast(Some(s"H$i"), h); h })
        (p, hs)
    }

    // ============================================================
    // 1. Inbound Event Propagation
    // ============================================================

    test("channelRead forward propagation") {
        val (p, hs) = addHandlers(3)
        p.fireChannelRead("msg")
        assert(hs(0).hasEvent("channelRead"))
        assert(hs(1).hasEvent("channelRead"))
        assert(hs(2).hasEvent("channelRead"))
    }

    test("channelActive forward propagation") {
        val (p, hs) = addHandlers(2)
        p.fireChannelActive()
        assert(hs(0).hasEvent("channelActive"))
        assert(hs(1).hasEvent("channelActive"))
    }

    test("channelReadComplete forward propagation") {
        val (p, hs) = addHandlers(2)
        p.fireChannelReadComplete()
        assert(hs(0).hasEvent("channelReadComplete"))
        assert(hs(1).hasEvent("channelReadComplete"))
    }

    test("channelRegistered forward propagation") {
        val (p, hs) = addHandlers(2)
        p.fireChannelRegistered()
        assert(hs(0).hasEvent("channelRegistered"))
        assert(hs(1).hasEvent("channelRegistered"))
    }

    test("channelInactive forward propagation") {
        val (p, hs) = addHandlers(2)
        p.fireChannelInactive()
        assert(hs(0).hasEvent("channelInactive"))
        assert(hs(1).hasEvent("channelInactive"))
    }

    test("channelShutdown forward propagation") {
        val (p, hs) = addHandlers(2)
        p.fireChannelShutdown(ChannelShutdownDirection.Inbound)
        assert(hs(0).hasEvent("channelShutdown"))
        assert(hs(1).hasEvent("channelShutdown"))
    }

    test("channelInboundEvent forward propagation") {
        val (p, hs) = addHandlers(2)
        p.fireChannelInboundEvent("evt")
        assert(hs(0).hasEvent("channelInboundEvent"))
        assert(hs(1).hasEvent("channelInboundEvent"))
    }

    test("channelTimeoutEvent forward propagation") {
        val (p, hs) = addHandlers(2)
        p.fireChannelTimeoutEvent(42L)
        assert(hs(0).hasEvent("channelTimeoutEvent"))
        assert(hs(1).hasEvent("channelTimeoutEvent"))
    }

    // ============================================================
    // 2. Inbound with msgId
    // ============================================================

    test("channelRead(msg, msgId) forward propagation") {
        val (p, hs) = addHandlers(2)
        p.fireChannelRead("msg", 99L)
        assert(hs(0).hasEvent("channelReadId"))
        assert(hs(1).hasEvent("channelReadId"))
    }

    test("channelExceptionCaught(cause, id) forward propagation") {
        val (p, hs) = addHandlers(2)
        p.fireChannelExceptionCaught(new RuntimeException("test"), 1L)
        assert(hs(0).hasEvent("channelExceptionCaughtId"))
        assert(hs(1).hasEvent("channelExceptionCaughtId"))
    }

    // ============================================================
    // 3. Outbound Propagation via Context
    // ============================================================

    test("write propagates backward via ctx") {
        val (p, hs) = addHandlers(3)
        // C's ctx.write → finds next outbound handler backward → B, A (not C itself)
        p.lastContext.get.write("msg".getBytes) // Array[Byte] is a supported transport message type
        assert(!hs(2).hasEvent("write")) // C did not receive write
        assert(hs(1).hasEvent("write"))  // B received write
        assert(hs(0).hasEvent("write"))  // A received write
    }

    test("flush propagates backward via ctx") {
        val (p, hs) = addHandlers(3)
        p.lastContext.get.flush()
        assert(!hs(2).hasEvent("flush"))
        assert(hs(1).hasEvent("flush"))
        assert(hs(0).hasEvent("flush"))
    }

    test("pipeline flush propagates backward") {
        val (p, hs) = addHandlers(3)
        p.flush()
        assert(hs(2).hasEvent("flush"))
        assert(hs(1).hasEvent("flush"))
        assert(hs(0).hasEvent("flush"))
    }

    test("writeAndFlush via ctx") {
        val p  = newPipeline()
        val sw = new StopWriteHandler // absorbs write, auto-forwards flush (@Skip)
        val b  = new RecordingHandler
        val c  = new RecordingHandler
        p.addLast(Some("SW"), sw)
        p.addLast(Some("B"), b)
        p.addLast(Some("C"), c)
        p.context(c).get.writeAndFlush("msg".getBytes)
        assert(!c.hasEvent("write"))
        assert(b.hasEvent("write"))
        assert(sw.receivedWrite)
        assert(!c.hasEvent("flush"))
        assert(b.hasEvent("flush"))
    }

    // ============================================================
    // 4. Mask Skipping
    // ============================================================

    test("default handler skipped for channelRead") {
        val p = newPipeline()
        val a = new RecordingHandler
        val b = new ChannelHandler {}     // default: all @Skip
        val c = new RecordingHandler
        p.addLast(Some("A"), a)
        p.addLast(Some("B"), b)
        p.addLast(Some("C"), c)
        p.fireChannelRead("msg")
        assert(a.hasEvent("channelRead"))
        assert(c.hasEvent("channelRead"))
        // B was skipped because it has @Skip on channelRead
    }

    test("default handler skipped for write") {
        val p = newPipeline()
        val a = new RecordingHandler
        val b = new ChannelHandler {}
        val c = new RecordingHandler
        p.addLast(Some("A"), a)
        p.addLast(Some("B"), b)
        p.addLast(Some("C"), c)
        // C ctx.write → goes backward, B is skipped, A receives
        p.context(c).get.write("msg".getBytes)
        assert(a.hasEvent("write"))
    }

    test("all default handlers - events reach tail/head") {
        val p = newPipeline()
        p.addLast(Some("D1"), new ChannelHandler {})
        p.addLast(Some("D2"), new ChannelHandler {})
        // No crash — TailHandler absorbs inbound, HeadHandler absorbs outbound
        p.fireChannelRead("msg")
        p.flush()
    }

    // ============================================================
    // 5. Propagation Stopping
    // ============================================================

    test("handler stops channelRead propagation") {
        val p = newPipeline()
        val a = new RecordingHandler
        val s = new StopReadHandler
        val c = new RecordingHandler
        p.addLast(Some("A"), a)
        p.addLast(Some("S"), s)
        p.addLast(Some("C"), c)
        p.fireChannelRead("msg")
        assert(a.hasEvent("channelRead"))
        assert(s.receivedRead)
        assert(!c.hasEvent("channelRead")) // C not reached
    }

    test("handler stops write propagation") {
        val p = newPipeline()
        val a = new RecordingHandler
        val s = new StopWriteHandler
        val c = new RecordingHandler
        p.addLast(Some("A"), a)
        p.addLast(Some("S"), s)
        p.addLast(Some("C"), c)
        // C ctx.write → backward → S absorbs, A not reached
        p.context(c).get.write("msg")
        assert(s.receivedWrite)
        assert(!a.hasEvent("write"))
    }

    // ============================================================
    // 6. Exception Handling
    // ============================================================

    test("exception in handler routes to exceptionCaught") {
        val p = newPipeline()
        val a = new RecordingHandler
        val t = new ThrowingReadHandler
        val c = new RecordingHandler
        p.addLast(Some("A"), a)
        p.addLast(Some("T"), t)
        p.addLast(Some("C"), c)
        p.fireChannelRead("msg")
        // T throws, pipeline catches and routes to next handler's exceptionCaught
        assert(c.hasEvent("channelExceptionCaught"))
    }

    test("exception in exceptionCaught is logged without crash") {
        val p = newPipeline()
        val t = new ThrowingExceptionCaughtHandler
        p.addLast(Some("T"), t)
        // Should not crash even though exceptionCaught itself throws
        p.fireChannelExceptionCaught(new RuntimeException("outer"))
    }

    test("pipeline-level fireChannelExceptionCaught") {
        val (p, hs) = addHandlers(3)
        p.fireChannelExceptionCaught(new RuntimeException("test"))
        assert(hs(0).hasEvent("channelExceptionCaught"))
        assert(hs(1).hasEvent("channelExceptionCaught"))
        assert(hs(2).hasEvent("channelExceptionCaught"))
    }

    // ============================================================
    // 7. ChannelWritabilityChanged
    // ============================================================

    test("writabilityChanged forward propagation") {
        val (p, hs) = addHandlers(2)
        p.fireChannelWritabilityChanged()
        assert(hs(0).hasEvent("channelWritabilityChanged"))
        assert(hs(1).hasEvent("channelWritabilityChanged"))
    }

}

object ChannelPipelineEventSuite {

    class StopReadHandler extends ChannelHandler {
        var receivedRead = false
        override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef): Unit =
            receivedRead = true // do NOT call ctx.fireChannelRead
    }

    class StopWriteHandler extends ChannelHandler {
        var receivedWrite = false
        override def write(ctx: ChannelHandlerContext, msg: AnyRef): Unit =
            receivedWrite = true // do NOT call ctx.write
    }

    class ThrowingReadHandler extends ChannelHandler {
        override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef): Unit =
            throw new RuntimeException("boom in channelRead")
    }

    class ThrowingExceptionCaughtHandler extends ChannelHandler {
        override def channelExceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit =
            throw new RuntimeException("boom in exceptionCaught")
    }

}
