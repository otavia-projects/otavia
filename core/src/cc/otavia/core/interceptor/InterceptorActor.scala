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
import cc.otavia.core.message.*
import cc.otavia.core.stack.{AskStack, NoticeStack, StackYield}
import cc.otavia.core.stack.helper.FutureState

/** Base class for interceptor actors that wrap a target [[Address]] and forward messages through a chain.
 *
 *  Interceptors are normal actors that use the standard Stack coroutine pattern (override [[resumeAsk]] / [[resumeNotice]]
 *  with state matching). The [[forwardAsk]] helper encapsulates the common pattern of forwarding to the next address and
 *  suspending until the reply arrives.
 *
 *  Concept mapping (for understanding, not API):
 *  {{{
 *  // Industry concept        -> Otavia equivalent
 *  // chain.proceed()         -> forwardAsk(stack)
 *  // preHandle return false  -> stack.return(rejectReply) without calling forwardAsk
 *  // postHandle              -> match FutureState with ForwardStateId, process reply, then stack.return
 *  // onion model             -> Stack StartState -> suspend -> FutureState naturally implements this
 *  }}}
 *
 *  Example - logging interceptor:
 *  {{{
 *  class LoggingInterceptor(val next: Address[HttpRequest]) extends InterceptorActor[HttpRequest] {
 *    override protected def resumeAsk(stack: AskStack[HttpRequest & Ask[? <: Reply]]): StackYield = {
 *      stack.state match {
 *        case _: StartState =>
 *          stack.attach(System.nanoTime())
 *          forwardAsk(stack)
 *        case state: FutureState[_] if state.id == ForwardStateId =>
 *          val elapsed = (System.nanoTime() - stack.attach[Long]) / 1_000_000
 *          logger.info(s"${stack.ask.method} ${stack.ask.path} -> ${elapsed}ms")
 *          stack.`return`(state.future.getNow.asInstanceOf)
 *      }
 *    }
 *  }
 *  }}}
 *
 *  @tparam M
 *    the message type this interceptor handles, must match the target actor's type
 */
abstract class InterceptorActor[M <: Call] extends StateActor[M] {

    /** The next interceptor or target actor in the chain. Users provide this via a constructor parameter. */
    protected def next: Address[M]

    /** State ID used internally by [[forwardAsk]]. Users should pick positive IDs for their own [[FutureState]] instances
     *  to avoid collisions.
     */
    protected val ForwardStateId: Int = -1

    /** Forward the ask to [[next]] and suspend the current stack. When the reply arrives, [[resumeAsk]] is called again
     *  with a [[FutureState]] whose [[FutureState.id]] equals [[ForwardStateId]].
     */
    protected def forwardAsk(stack: AskStack[M & Ask[? <: Reply]]): StackYield = {
        val state = FutureState[Reply](ForwardStateId)
        next.askUnsafe(stack.ask, state.future)
        stack.suspend(state)
    }

    /** Forward the notice to [[next]] and complete the stack. */
    protected def forwardNotice(stack: NoticeStack[M & Notice]): StackYield = {
        next.notice(stack.notice)
        stack.`return`()
    }

}
