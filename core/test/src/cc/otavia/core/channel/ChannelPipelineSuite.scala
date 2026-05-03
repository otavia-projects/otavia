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

import cc.otavia.buffer.pool.{AdaptiveBuffer, DirectPooledPageAllocator, HeapPooledPageAllocator}
import cc.otavia.core.channel.ChannelPipelineSuite.*
import cc.otavia.core.channel.internal.ChannelHandlerMask
import cc.otavia.core.stack.{ChannelFuture, ChannelPromise}
import cc.otavia.core.system.ActorSystem
import org.scalatest.funsuite.AnyFunSuiteLike

import java.net.SocketAddress
import java.nio.file.attribute.FileAttribute
import java.nio.file.{OpenOption, Path}
import scala.collection.mutable
import scala.language.unsafeNulls

class ChannelPipelineSuite extends AnyFunSuiteLike {

    private def newPipeline(): ChannelPipelineImpl = new StubChannel().pipeline

    // ============================================================
    // 1. Pipeline Structure via addLast
    // ============================================================

    test("addLast single handler") {
        val p    = newPipeline()
        val h    = new RecordingHandler
        p.addLast(Some("H"), h)
        assert(!p.isEmpty)
        assert(p.firstContext.isDefined)
        assert(p.lastContext.isDefined)
        assert(p.firstContext.get eq p.lastContext.get)
        assert(p.toMap.size == 1)
    }

    test("addLast ordering") {
        val p = newPipeline()
        val a = new RecordingHandler
        val b = new RecordingHandler
        p.addLast(Some("A"), a)
        p.addLast(Some("B"), b)
        val names = p.names.toList
        assert(names == List("A", "B"))
        assert(p.firstContext.get.name == "A")
        assert(p.lastContext.get.name == "B")
    }

    test("addLast varargs") {
        val p = newPipeline()
        val a = new RecordingHandler
        val b = new RecordingHandler
        val c = new RecordingHandler
        p.addLast(Some("A"), a)
        p.addLast(Some("B"), b)
        p.addLast(Some("C"), c)
        assert(p.toMap.size == 3)
        assert(p.names.toList == List("A", "B", "C"))
    }

    test("addLast multiple handlers preserves insertion order") {
        val p = newPipeline()
        p.addLast(Some("H1"), new RecordingHandler)
        p.addLast(Some("H2"), new RecordingHandler)
        val names = p.names.toList
        assert(names == List("H1", "H2"))
    }

    // ============================================================
    // 2. Replace
    // ============================================================

    test("replace by handler swaps correctly") {
        val p   = newPipeline()
        val old = new RecordingHandler
        val rep = new RecordingHandler
        p.addLast(Some("A"), old)
        p.replace(old, Some("R"), rep)
        assert(p.context("A").isEmpty)
        assert(p.context("R").isDefined)
        assert(p.context(rep).isDefined)
        assert(p.context(old).isEmpty)
    }

    test("replace by name") {
        val p   = newPipeline()
        val old = new RecordingHandler
        val rep = new RecordingHandler
        p.addLast(Some("target"), old)
        val removed = p.replace("target", Some("new"), rep)
        assert(removed eq old)
        assert(p.context("new").isDefined)
        assert(p.context("target").isEmpty)
    }

    test("replace by type") {
        val p   = newPipeline()
        val old = new RecordingHandler
        val rep = new RecordingHandler
        p.addLast(Some("A"), old)
        val removed = p.replace(classOf[RecordingHandler], Some("R"), rep)
        assert(removed eq old)
        assert(p.context("R").isDefined)
    }

    test("replace with custom name") {
        val p   = newPipeline()
        val old = new RecordingHandler
        val rep = new RecordingHandler
        p.addLast(Some("old"), old)
        p.replace(old, Some("brand-new"), rep)
        assert(p.context("brand-new").isDefined)
    }

    test("replace calls handlerAdded on new handler") {
        val p   = newPipeline()
        val old = new RecordingHandler
        val lc  = new LifecycleHandler
        p.addLast(Some("A"), old)
        p.replace(old, Some("LC"), lc)
        assert(lc.addedCalls.nonEmpty)
    }

    test("replace calls handlerRemoved on old handler") {
        val p   = newPipeline()
        val lc  = new LifecycleHandler
        val rep = new RecordingHandler
        p.addLast(Some("A"), lc)
        assert(lc.addedCalls.nonEmpty)
        p.replace(lc, Some("R"), rep)
        assert(lc.removedCalls.nonEmpty)
    }

    test("replace throws for non-existent handler") {
        val p   = newPipeline()
        val h   = new RecordingHandler
        val rep = new RecordingHandler
        intercept[NoSuchElementException] {
            p.replace(h, Some("R"), rep)
        }
    }

    // ============================================================
    // 3. Lookup
    // ============================================================

    test("context by handler instance") {
        val p = newPipeline()
        val h = new RecordingHandler
        p.addLast(Some("A"), h)
        assert(p.context(h).isDefined)
        assert(p.context(h).get.handler eq h)
    }

    test("context by name") {
        val p = newPipeline()
        p.addLast(Some("myHandler"), new RecordingHandler)
        assert(p.context("myHandler").isDefined)
        assert(p.context("nonexistent").isEmpty)
    }

    test("context by type with isAssignableFrom") {
        val p = newPipeline()
        val h = new RecordingHandler
        p.addLast(Some("A"), h)
        assert(p.context(classOf[RecordingHandler]).isDefined)
        assert(p.context(classOf[ChannelHandler]).isDefined)
        assert(p.context(classOf[LifecycleHandler]).isEmpty)
    }

    test("first/last/isEmpty") {
        val p = newPipeline()
        assert(p.isEmpty)
        assert(p.firstContext.isEmpty)
        assert(p.lastContext.isEmpty)

        val a = new RecordingHandler
        p.addLast(Some("A"), a)
        assert(!p.isEmpty)
        assert(p.firstContext.get eq p.lastContext.get)

        val b = new RecordingHandler
        p.addLast(Some("B"), b)
        assert(p.firstContext.get.name == "A")
        assert(p.lastContext.get.name == "B")
    }

    test("toMap returns ordered entries") {
        val p = newPipeline()
        val a = new RecordingHandler
        val b = new RecordingHandler
        p.addLast(Some("A"), a)
        p.addLast(Some("B"), b)
        val m = p.toMap
        assert(m.size == 2)
        assert(m("A") eq a)
        assert(m("B") eq b)
    }

    test("get by name and type") {
        val p = newPipeline()
        val h = new RecordingHandler
        p.addLast(Some("myHandler"), h)
        assert(p.get("myHandler").isDefined)
        assert(p.get("myHandler").get eq h)
        assert(p.get(classOf[RecordingHandler]).isDefined)
        assert(p.get(classOf[RecordingHandler]).get eq h)
        assert(p.get("nonexistent").isEmpty)
    }

    // ============================================================
    // 4. Name Management
    // ============================================================

    test("duplicate name throws") {
        val p = newPipeline()
        p.addLast(Some("dup"), new RecordingHandler)
        intercept[IllegalArgumentException] {
            p.addLast(Some("dup"), new RecordingHandler)
        }
    }

    test("context name is the provided name") {
        val p = newPipeline()
        p.addLast(Some("myHandler"), new RecordingHandler)
        assert(p.firstContext.get.name == "myHandler")
    }

    test("multiple handlers have distinct names") {
        val p = newPipeline()
        p.addLast(Some("H1"), new RecordingHandler)
        p.addLast(Some("H2"), new RecordingHandler)
        val names = p.names.toList
        assert(names.length == 2)
        assert(names(0) != names(1))
    }

    // ============================================================
    // 5. Handler Lifecycle
    // ============================================================

    test("handlerAdded called on add") {
        val p  = newPipeline()
        val lc = new LifecycleHandler
        p.addLast(Some("LC"), lc)
        assert(lc.addedCalls.length == 1)
    }

    test("handlerRemoved called on replace removal") {
        val p  = newPipeline()
        val lc = new LifecycleHandler
        p.addLast(Some("LC"), lc)
        p.replace(lc, Some("R"), new RecordingHandler)
        assert(lc.removedCalls.length == 1)
    }

    test("handlerAdded exception removes handler and fires exceptionCaught") {
        val p  = newPipeline()
        val ec = new RecordingHandler
        p.addLast(Some("EC"), ec) // capture the exception
        p.addLast(Some("BAD"), new ThrowingInAddedHandler)
        // handlerAdded throws, pipeline catches it, removes the handler, fires exceptionCaught
        assert(p.context("BAD").isEmpty)     // removed
        assert(ec.hasEvent("channelExceptionCaught")) // exception was fired
    }

    test("handlerRemoved exception fires exceptionCaught") {
        val p  = newPipeline()
        val ec = new RecordingHandler
        p.addLast(Some("EC"), ec)
        val thr = new ThrowingInRemovedHandler
        p.addLast(Some("THR"), thr)
        // replace triggers handlerRemoved on thr, which throws
        // this should fire exceptionCaught but not crash
        p.replace(thr, Some("R"), new RecordingHandler)
        assert(ec.hasEvent("channelExceptionCaught"))
    }

    // ============================================================
    // 6. Buffer Management
    // ============================================================

    test("addLast buffer handler when pipeline empty") {
        val p = newPipeline()
        val b = new BufferHandler
        p.addLast(Some("BUF"), b)
        val ctx = p.context(b).get.asInstanceOf[ChannelHandlerContextImpl]
        assert(ctx.hasOutboundAdaptive)
    }

    test("addLast buffer handler after buffer handler") {
        val p = newPipeline()
        p.addLast(Some("B1"), new BufferHandler)
        val b2 = new BufferHandler
        p.addLast(Some("B2"), b2)
        val ctx = p.context(b2).get.asInstanceOf[ChannelHandlerContextImpl]
        assert(ctx.hasOutboundAdaptive)
    }

    test("addLast buffer handler after non-buffer throws") {
        val p = newPipeline()
        p.addLast(Some("NB"), new RecordingHandler) // non-buffer
        intercept[IllegalStateException] {
            p.addLast(Some("BUF"), new BufferHandler)
        }
    }

    test("replace buffer with buffer transfers buffers") {
        val p   = newPipeline()
        val old = new BufferHandler
        p.addLast(Some("OLD"), old)
        val oldCtx = p.context(old).get.asInstanceOf[ChannelHandlerContextImpl]
        assert(oldCtx.hasOutboundAdaptive)

        val rep = new BufferHandler
        p.replace(old, Some("NEW"), rep)
        val repCtx = p.context(rep).get.asInstanceOf[ChannelHandlerContextImpl]
        assert(repCtx.hasOutboundAdaptive)
    }

    test("replace non-buffer with buffer throws") {
        val p   = newPipeline()
        val old = new RecordingHandler
        p.addLast(Some("NB"), old)
        intercept[IllegalStateException] {
            p.replace(old, Some("BUF"), new BufferHandler)
        }
    }

    // ============================================================
    // 7. Pending Outbound Bytes
    // ============================================================

    test("pendingOutboundBytes starts at 0") {
        val p = newPipeline()
        assert(p.pendingOutboundBytes == 0)
    }

    test("replace with pending bytes handler updates total") {
        val p   = newPipeline()
        val old = new RecordingHandler
        p.addLast(Some("H"), old)
        assert(p.pendingOutboundBytes == 0)
        val pbh = new PendingBytesHandler(100)
        p.replace(old, Some("PB"), pbh)
        assert(p.pendingOutboundBytes == 100)
    }

}

object ChannelPipelineSuite {

    private[channel] class StubChannel extends AbstractChannel(ActorSystem.global) {

        locally {
            // ChannelHandlerContextImpl constructor accesses pipeline.system -> executor -> actorHouse.actor.
            // Wire up a minimal actor house graph so the pipeline can be created without NPE.
            val thread  = new cc.otavia.core.system.ActorThread(ActorSystem.global, 0)
            val manager = new cc.otavia.core.system.HouseManager(thread)
            val house   = new cc.otavia.core.system.ActorHouse(manager)
            val actor   = new StubActor()

            // StubActor.context -> house -> manager -> thread -> system
            val actorHouseField = Class.forName("cc.otavia.core.actor.AbstractActor").getDeclaredField("house")
            actorHouseField.setAccessible(true)
            actorHouseField.set(actor, house)

            // house.actor -> StubActor
            val dwellerField = classOf[cc.otavia.core.system.ActorHouse].getDeclaredField("dweller")
            dwellerField.setAccessible(true)
            dwellerField.set(house, actor)

            // channel.actorHouse -> house
            val channelField = classOf[AbstractChannel].getDeclaredField("actorHouse")
            channelField.setAccessible(true)
            channelField.set(this, house)
        }

        private val _directAlloc = new DirectPooledPageAllocator(4096)
        private val _heapAlloc   = new HeapPooledPageAllocator(4096)

        override def localAddress: Option[SocketAddress]  = None
        override def remoteAddress: Option[SocketAddress] = None

        private lazy val _pipeline: ChannelPipelineImpl = new ChannelPipelineImpl(this)

        override def pipeline: ChannelPipelineImpl = _pipeline

        override def directAllocator: cc.otavia.buffer.pool.AbstractPooledPageAllocator = _directAlloc

        override def heapAllocator: cc.otavia.buffer.pool.AbstractPooledPageAllocator = _heapAlloc

        private[core] def bindTransport(local: SocketAddress, channelPromise: ChannelPromise): Unit         = {}
        private[core] def connectTransport(remote: SocketAddress, local: Option[SocketAddress], promise: ChannelPromise): Unit = {}
        private[core] def openTransport(path: Path, options: Seq[OpenOption], attrs: Seq[FileAttribute[?]], promise: ChannelPromise): Unit = {}
        private[core] def disconnectTransport(promise: ChannelPromise): Unit                                = {}
        private[core] def closeTransport(promise: ChannelPromise): Unit                                     = {}
        private[core] def shutdownTransport(direction: ChannelShutdownDirection, promise: ChannelPromise): Unit = {}
        private[core] def registerTransport(promise: ChannelPromise): Unit                                  = {}
        private[core] def deregisterTransport(promise: ChannelPromise): Unit                                = {}

    }

    // Minimal ChannelsActor for wiring the house graph. Only needs .system to work (via context -> house -> manager -> thread).
    private class StubActor extends cc.otavia.core.actor.ChannelsActor[cc.otavia.core.message.Call]

    class RecordingHandler extends ChannelHandler {
        val events: mutable.ListBuffer[(String, AnyRef)] = mutable.ListBuffer.empty

        override def channelRegistered(ctx: ChannelHandlerContext): Unit   = { events += (("channelRegistered", ctx)); ctx.fireChannelRegistered() }
        override def channelUnregistered(ctx: ChannelHandlerContext): Unit = { events += (("channelUnregistered", ctx)); ctx.fireChannelUnregistered() }
        override def channelActive(ctx: ChannelHandlerContext): Unit       = { events += (("channelActive", ctx)); ctx.fireChannelActive() }
        override def channelInactive(ctx: ChannelHandlerContext): Unit     = { events += (("channelInactive", ctx)); ctx.fireChannelInactive() }
        override def channelShutdown(ctx: ChannelHandlerContext, direction: ChannelShutdownDirection): Unit = { events += (("channelShutdown", direction)); ctx.fireChannelShutdown(direction) }
        override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef): Unit    = { events += (("channelRead", msg)); ctx.fireChannelRead(msg) }
        override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef, msgId: Long): Unit = { events += (("channelReadId", msg)); ctx.fireChannelRead(msg, msgId) }
        override def channelReadComplete(ctx: ChannelHandlerContext): Unit         = { events += (("channelReadComplete", ctx)); ctx.fireChannelReadComplete() }
        override def channelWritabilityChanged(ctx: ChannelHandlerContext): Unit   = { events += (("channelWritabilityChanged", ctx)); ctx.fireChannelWritabilityChanged() }
        override def channelInboundEvent(ctx: ChannelHandlerContext, evt: AnyRef): Unit = { events += (("channelInboundEvent", evt)); ctx.fireChannelInboundEvent(evt) }
        override def channelTimeoutEvent(ctx: ChannelHandlerContext, id: Long): Unit = { events += (("channelTimeoutEvent", id.asInstanceOf[AnyRef])); ctx.fireChannelTimeoutEvent(id) }
        override def channelExceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = { events += (("channelExceptionCaught", cause)); ctx.fireChannelExceptionCaught(cause) }
        override def channelExceptionCaught(ctx: ChannelHandlerContext, cause: Throwable, id: Long): Unit = { events += (("channelExceptionCaughtId", cause)); ctx.fireChannelExceptionCaught(cause, id) }

        override def write(ctx: ChannelHandlerContext, msg: AnyRef): Unit      = { events += (("write", msg)); ctx.write(msg) }
        override def write(ctx: ChannelHandlerContext, msg: AnyRef, msgId: Long): Unit = { events += (("writeId", msg)); ctx.write(msg, msgId) }
        override def flush(ctx: ChannelHandlerContext): Unit                    = { events += (("flush", ctx)); ctx.flush() }
        override def read(ctx: ChannelHandlerContext, readPlan: cc.otavia.core.channel.message.ReadPlan): Unit = { events += (("read", readPlan)); ctx.read(readPlan) }

        def hasEvent(method: String): Boolean = events.exists(_._1 == method)
        def eventsOf(method: String): Seq[AnyRef] = events.collect { case (m, arg) if m == method => arg }.toSeq
    }

    class LifecycleHandler extends ChannelHandler {
        val addedCalls: mutable.ListBuffer[ChannelHandlerContext]   = mutable.ListBuffer.empty
        val removedCalls: mutable.ListBuffer[ChannelHandlerContext] = mutable.ListBuffer.empty

        override def handlerAdded(ctx: ChannelHandlerContext): Unit   = addedCalls += ctx
        override def handlerRemoved(ctx: ChannelHandlerContext): Unit = removedCalls += ctx
    }

    class BufferHandler extends ChannelHandler {
        override def isBufferHandler: Boolean        = true
        override def hasInboundAdaptive: Boolean     = true
        override def hasOutboundAdaptive: Boolean    = true
    }

    class PendingBytesHandler(bytes: Long) extends ChannelHandler {
        override def pendingOutboundBytes(ctx: ChannelHandlerContext): Long = bytes
    }

    class ThrowingInAddedHandler extends ChannelHandler {
        override def handlerAdded(ctx: ChannelHandlerContext): Unit =
            throw new RuntimeException("boom in handlerAdded")
    }

    class ThrowingInRemovedHandler extends ChannelHandler {
        override def handlerRemoved(ctx: ChannelHandlerContext): Unit =
            throw new RuntimeException("boom in handlerRemoved")
    }

}
