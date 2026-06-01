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

package cc.otavia.core.interceptor

import cc.otavia.core.actor.StateActor
import cc.otavia.core.address.Address
import cc.otavia.core.interceptor.*
import cc.otavia.core.message.*
import cc.otavia.core.stack.*
import cc.otavia.core.stack.helper.{FutureState, StartState}
import cc.otavia.core.system.ActorSystem
import cc.otavia.testkit.TestProbe
import org.scalatest.funsuite.AnyFunSuite

import scala.language.unsafeNulls

// =========================================================================
// Message types
// =========================================================================

case class Echo(msg: String)         extends Ask[EchoReply]
case class EchoReply(msg: String, processedBy: String) extends Reply

// =========================================================================
// Target actor
// =========================================================================

class EchoActor extends StateActor[Echo] {
    override protected def resumeAsk(stack: AskStack[Echo]): StackYield = {
        stack.state match {
            case _: StartState =>
                stack.`return`(EchoReply(stack.ask.msg, "target"))
        }
    }
}

// =========================================================================
// Interceptor actors
// =========================================================================

class PassThroughInterceptor(val next: Address[Echo]) extends InterceptorActor[Echo] {
    override protected def resumeAsk(stack: AskStack[Echo & Ask[? <: Reply]]): StackYield = {
        stack.state match {
            case _: StartState =>
                forwardAsk(stack)
            case state: FutureState[_] if state.id == ForwardStateId =>
                val reply = state.future.getNow.asInstanceOf[EchoReply]
                stack.`return`(reply.copy(processedBy = reply.processedBy + "+passthrough"))
        }
    }
}

class ShortCircuitInterceptor(val next: Address[Echo]) extends InterceptorActor[Echo] {
    override protected def resumeAsk(stack: AskStack[Echo & Ask[? <: Reply]]): StackYield = {
        stack.state match {
            case _: StartState =>
                stack.`return`(EchoReply("short-circuited", "short-circuit"))
        }
    }
}

class AsyncInterceptor(val next: Address[Echo], checkActor: Address[Echo])
    extends InterceptorActor[Echo] {
    override protected def resumeAsk(stack: AskStack[Echo & Ask[? <: Reply]]): StackYield = {
        stack.state match {
            case _: StartState =>
                val state = FutureState[EchoReply](1)
                checkActor.ask(Echo("check"), state.future)
                stack.suspend(state)
            case state: FutureState[_] if state.id == 1 =>
                forwardAsk(stack)
            case state: FutureState[_] if state.id == ForwardStateId =>
                val reply = state.future.getNow.asInstanceOf[EchoReply]
                stack.`return`(reply.copy(processedBy = reply.processedBy + "+async"))
        }
    }
}

@Intercept(Array(classOf[PassThroughInterceptor]))
class AnnotatedEchoActor extends StateActor[Echo] {
    override protected def resumeAsk(stack: AskStack[Echo]): StackYield = {
        stack.state match {
            case _: StartState =>
                stack.`return`(EchoReply(stack.ask.msg, "annotated-target"))
        }
    }
}

// =========================================================================
// Tests
// =========================================================================

class InterceptorE2ESuite extends AnyFunSuite {

    private val system = ActorSystem.global
    private val probe  = new TestProbe(system)

    test("intercept: single interceptor modifies reply") {
        val target  = system.buildActor(() => new EchoActor)
        val proxied = system.intercept(target, Seq(next => new PassThroughInterceptor(next)))

        val result = probe.askAndExpect[Echo, EchoReply](
            proxied,
            Echo("hello"),
            f => f.getNow.processedBy == "target+passthrough"
        )
        assert(result)
    }

    test("intercept: chain of 3 interceptors applies in order") {
        val target  = system.buildActor(() => new EchoActor)
        val proxied = system.intercept(
            target,
            Seq(
                next => new PassThroughInterceptor(next), // outermost
                next => new PassThroughInterceptor(next),
                next => new PassThroughInterceptor(next) // innermost
            )
        )

        val result = probe.askAndExpect[Echo, EchoReply](
            proxied,
            Echo("chain"),
            f => f.getNow.processedBy == "target+passthrough+passthrough+passthrough"
        )
        assert(result)
    }

    test("intercept: short-circuit interceptor returns without forwarding") {
        val target  = system.buildActor(() => new EchoActor)
        val proxied = system.intercept(target, Seq(next => new ShortCircuitInterceptor(next)))

        val result = probe.askAndExpect[Echo, EchoReply](
            proxied,
            Echo("blocked"),
            f => f.getNow.processedBy == "short-circuit" && f.getNow.msg == "short-circuited"
        )
        assert(result)
    }

    test("intercept: async interceptor with pre-check forwards correctly") {
        val checkActor = system.buildActor(() => new EchoActor)
        val target     = system.buildActor(() => new EchoActor)
        val proxied    = system.intercept(target, Seq(next => new AsyncInterceptor(next, checkActor)))

        val result = probe.askAndExpect[Echo, EchoReply](
            proxied,
            Echo("async"),
            f => f.getNow.processedBy == "target+async"
        )
        assert(result)
    }

    test("@Intercept: annotated actor applies interceptor chain") {
        val address = system.buildActor(() => new AnnotatedEchoActor)

        val result = probe.askAndExpect[Echo, EchoReply](
            address,
            Echo("annotated"),
            f => f.getNow.processedBy == "annotated-target+passthrough"
        )
        assert(result)
    }

    test("intercept: direct vs intercepted address gives different reply") {
        val target = system.buildActor(() => new EchoActor)

        val directResult = probe.askAndExpect[Echo, EchoReply](
            target,
            Echo("direct"),
            f => f.getNow.processedBy == "target"
        )
        assert(directResult)

        val proxied = system.intercept(target, Seq(next => new PassThroughInterceptor(next)))

        val interceptedResult = probe.askAndExpect[Echo, EchoReply](
            proxied,
            Echo("intercepted"),
            f => f.getNow.processedBy == "target+passthrough"
        )
        assert(interceptedResult)
    }

}
