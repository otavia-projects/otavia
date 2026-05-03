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

import cc.otavia.core.channel.ChannelHandlerContextSuite.*
import cc.otavia.core.channel.ChannelPipelineSuite.{RecordingHandler, StubChannel}
import org.scalatest.funsuite.AnyFunSuiteLike

import scala.collection.mutable
import scala.language.unsafeNulls

class ChannelHandlerContextSuite extends AnyFunSuiteLike {

    private def newPipeline(): ChannelPipelineImpl = new StubChannel().pipeline

    // ============================================================
    // 1. Handler State Machine
    // ============================================================

    test("handlerAdded called after addLast (INIT -> ADD_COMPLETE)") {
        val p  = newPipeline()
        val lc = new LifecycleHandler
        p.addLast(Some("LC"), lc)
        // handlerAdded was called, meaning state transitioned from INIT to ADD_COMPLETE
        assert(lc.addedCalls.length == 1)
        assert(lc.addedCalls.head.handler eq lc)
    }

    test("handlerRemoved called after replace (ADD_COMPLETE -> REMOVE_COMPLETE)") {
        val p  = newPipeline()
        val lc = new LifecycleHandler
        p.addLast(Some("LC"), lc)
        p.replace(lc, Some("R"), new RecordingHandler)
        assert(lc.removedCalls.length == 1)
    }

    test("isRemoved is false during normal operation") {
        val p  = newPipeline()
        val lc = new LifecycleHandler
        p.addLast(Some("LC"), lc)
        val ctx = p.context(lc).get.asInstanceOf[ChannelHandlerContextImpl]
        assert(!ctx.isRemoved)
    }

    test("isRemoved is true after replacement") {
        val p  = newPipeline()
        val lc = new LifecycleHandler
        p.addLast(Some("LC"), lc)
        val ctx = p.context(lc).get.asInstanceOf[ChannelHandlerContextImpl]
        p.replace(lc, Some("R"), new RecordingHandler)
        assert(ctx.isRemoved)
    }

    // ============================================================
    // 2. Context Properties
    // ============================================================

    test("context name matches provided name") {
        val p = newPipeline()
        p.addLast(Some("myName"), new RecordingHandler)
        val ctx = p.firstContext.get
        assert(ctx.name == "myName")
    }

    test("context handler is the same instance") {
        val p = newPipeline()
        val h = new RecordingHandler
        p.addLast(Some("H"), h)
        val ctx = p.firstContext.get
        assert(ctx.handler eq h)
    }

    test("context pipeline reference") {
        val p = newPipeline()
        p.addLast(Some("H"), new RecordingHandler)
        val ctx = p.firstContext.get
        assert(ctx.pipeline eq p)
    }

    // ============================================================
    // 3. Pending Bytes Tracking
    // ============================================================

    test("default handler pending bytes is 0") {
        val p = newPipeline()
        p.addLast(Some("H"), new RecordingHandler)
        assert(p.pendingOutboundBytes == 0)
    }

    test("pending bytes handler updates pipeline total") {
        val p   = newPipeline()
        val pbh = new PendingBytesHandler(256)
        p.addLast(Some("PB"), pbh)
        assert(p.pendingOutboundBytes == 256)
    }

    test("replace pending bytes handler decreases total") {
        val p   = newPipeline()
        val pbh = new PendingBytesHandler(256)
        p.addLast(Some("PB"), pbh)
        assert(p.pendingOutboundBytes == 256)
        p.replace(pbh, Some("R"), new RecordingHandler)
        assert(p.pendingOutboundBytes == 0)
    }

    // ============================================================
    // 4. Inflight Delegation
    // ============================================================

    test("ctx.inflightFutures delegates to pipeline") {
        val p = newPipeline()
        val h = new RecordingHandler
        p.addLast(Some("H"), h)
        val ctx = p.context(h).get.asInstanceOf[ChannelHandlerContextImpl]
        assert(ctx.inflightFutures eq p.inflightFutures)
    }

    test("ctx.inflightStacks delegates to pipeline") {
        val p = newPipeline()
        val h = new RecordingHandler
        p.addLast(Some("H"), h)
        val ctx = p.context(h).get.asInstanceOf[ChannelHandlerContextImpl]
        assert(ctx.inflightStacks eq p.inflightStacks)
    }

}

object ChannelHandlerContextSuite {

    class LifecycleHandler extends ChannelHandler {
        val addedCalls: scala.collection.mutable.ListBuffer[ChannelHandlerContext]   = scala.collection.mutable.ListBuffer.empty
        val removedCalls: scala.collection.mutable.ListBuffer[ChannelHandlerContext] = scala.collection.mutable.ListBuffer.empty

        override def handlerAdded(ctx: ChannelHandlerContext): Unit   = addedCalls += ctx
        override def handlerRemoved(ctx: ChannelHandlerContext): Unit = removedCalls += ctx
    }

    class PendingBytesHandler(bytes: Long) extends ChannelHandler {
        override def pendingOutboundBytes(ctx: ChannelHandlerContext): Long = bytes
    }

}
