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

package cc.otavia.core.address

import cc.otavia.core.actor.{AbstractActor, Actor}
import cc.otavia.core.message.*
import cc.otavia.core.stack.helper.FutureState
import cc.otavia.core.stack.{AskStack, MessageFuture}

import scala.reflect.ClassTag

/** Address is the facade of an actor. Actors cannot call each other directly, only send messages to the actor via its
 *  address.
 *
 *  @tparam M
 *    the message type that this actor can receive.
 */
trait Address[-M <: Call] extends EventableAddress {

    /** send notice message to this address
     *  @param notice
     *    notice message to send
     */
    def notice(notice: M & Notice): Unit

    /** send ask message to this address
     *  @param ask
     *    ask message to send
     *  @param future
     *    reply message future for this ask message
     *  @param sender
     *    who send this ask message
     *  @tparam A
     *    the type of ask message
     */
    // format: off
    def ask[A <: M & Ask[? <: Reply]](ask: A, future: MessageFuture[ReplyOf[A]])(using sender: AbstractActor[?]): MessageFuture[ReplyOf[A]]
    // format: on

    def askUnsafe(ask: Ask[?], f: MessageFuture[?])(using sender: AbstractActor[?]): MessageFuture[?]

    // format: off
     final def ask[A <: M & Ask[? <: Reply], R <: ReplyOf[A] : ClassTag](ask: A)(using sender: AbstractActor[?]): FutureState[R] = {
         // format: on
        val state = FutureState[R]()
        this.ask(ask, state.future)
        state
    }

    /** send ask message to this address, and set [[timeout]] milliseconds to get the respect [[Reply]], otherwise the
     *  [[MessageFuture]] will set [[scala.concurrent.TimeoutException]].
     *
     *  @param ask
     *    ask message to send
     *  @param f
     *    reply message future for this ask message
     *  @param timeout
     *    max time to wait for the [[Reply]] message.
     *  @param sender
     *    who send this ask message
     *  @tparam A
     *    the type of ask message
     */
    // format: off
    def ask[A <: M & Ask[? <: Reply]](ask: A, f: MessageFuture[ReplyOf[A]], timeout: Long)(using sender: AbstractActor[?]): MessageFuture[ReplyOf[A]]
    // format: on

    /** send a reply message to this address, this method can not be use by user, use [[AskStack.`return`]] instead.
     *  @param reply
     *    reply message to send
     *  @param sender
     *    who send this reply message
     */
    private[core] def reply(reply: Reply, sender: AbstractActor[?]): Unit

    private[core] def `throw`(cause: ExceptionMessage, sender: AbstractActor[?]): Unit

}
