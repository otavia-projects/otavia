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

import cc.otavia.core.actor.*
import cc.otavia.core.address.Address
import cc.otavia.core.message.*
import cc.otavia.core.stack.*
import cc.otavia.core.stack.helper.{FutureState, StartState}
import cc.otavia.core.system.ActorSystem
import org.scalatest.funsuite.AnyFunSuite

import scala.language.unsafeNulls

// =========================================================================
// Message types
// =========================================================================

case class Echo(msg: String) extends Ask[EchoReply]
case class EchoReply(msg: String, processedBy: String) extends Reply

case class Drop(msg: String) extends Notice

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

/** Simple pass-through interceptor that records its name in the reply. */
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

/** Short-circuit interceptor that replies without forwarding. */
class ShortCircuitInterceptor(val next: Address[Echo]) extends InterceptorActor[Echo] {

    override protected def resumeAsk(stack: AskStack[Echo & Ask[? <: Reply]]): StackYield = {
        stack.state match {
            case _: StartState =>
                stack.`return`(EchoReply("short-circuited", "short-circuit"))
        }
    }

}

/** Interceptor with async step before forwarding (simulates e.g. auth check). */
class AsyncInterceptor(val next: Address[Echo], checkActor: Address[Echo])
    extends InterceptorActor[Echo] {

    override protected def resumeAsk(stack: AskStack[Echo & Ask[? <: Reply]]): StackYield = {
        stack.state match {
            case _: StartState =>
                // Simulate async check by asking another actor
                val state = FutureState[EchoReply](1)
                checkActor.ask(Echo("check"), state.future)
                stack.suspend(state)
            case state: FutureState[_] if state.id == 1 =>
                // Check passed, forward to next
                forwardAsk(stack)
            case state: FutureState[_] if state.id == ForwardStateId =>
                val reply = state.future.getNow.asInstanceOf[EchoReply]
                stack.`return`(reply.copy(processedBy = reply.processedBy + "+async"))
        }
    }

}

/** Notice interceptor that drops all notices. */
class DropNoticeInterceptor(val next: Address[Echo]) extends InterceptorActor[Echo] {
    override protected def resumeNotice(stack: NoticeStack[Echo & Notice]): StackYield = {
        // Drop - don't call next.notice
        stack.`return`()
    }
}

// =========================================================================
// Actor annotated with @Intercept
// =========================================================================

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

class InterceptorActorSuite extends AnyFunSuite {

    test("intercept: programmatic API creates interceptor chain and returns address") {
        val system  = ActorSystem.global
        val target  = system.buildActor(() => new EchoActor)
        val proxied = system.intercept(target, Seq(next => new PassThroughInterceptor(next)))
        assert(proxied != null)
        assert(proxied ne target)
    }

    test("intercept: chain of 3 interceptors creates correctly") {
        val system  = ActorSystem.global
        val target  = system.buildActor(() => new EchoActor)
        val proxied = system.intercept(
            target,
            Seq(
                next => new PassThroughInterceptor(next), // outermost
                next => new PassThroughInterceptor(next),
                next => new PassThroughInterceptor(next) // innermost
            )
        )
        assert(proxied != null)
    }

    test("intercept: short-circuit interceptor can be created in chain") {
        val system  = ActorSystem.global
        val target  = system.buildActor(() => new EchoActor)
        val proxied = system.intercept(target, Seq(next => new ShortCircuitInterceptor(next)))
        assert(proxied != null)
    }

    test("intercept: async interceptor with extra state can be created") {
        val system      = ActorSystem.global
        val checkActor  = system.buildActor(() => new EchoActor)
        val target      = system.buildActor(() => new EchoActor)
        val proxied     = system.intercept(target, Seq(next => new AsyncInterceptor(next, checkActor)))
        assert(proxied != null)
    }

    test("\\@Intercept: annotated actor can be built and creates interceptor chain") {
        val system  = ActorSystem.global
        val address = system.buildActor(() => new AnnotatedEchoActor)
        assert(address != null)
    }

    test("intercept: intercepted address has same type parameter as target") {
        val system  = ActorSystem.global
        val target  = system.buildActor(() => new EchoActor)
        val proxied: Address[Echo] = system.intercept(target, Seq(next => new PassThroughInterceptor(next)))
        // If this compiles, the type parameter is preserved
        assert(proxied != null)
    }

}
