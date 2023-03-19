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

package io.otavia.core.address

import io.otavia.core.actor.{AbstractActor, Actor}
import io.otavia.core.message.*
import io.otavia.core.reactor.Event
import io.otavia.core.stack.{AskStack, ReplyFuture}

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
     *  @param sender
     *    who send this notice message
     */
    def notice(notice: M & Notice)(using sender: AbstractActor[?]): Unit

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
    def ask[A <: M & Ask[_ <: Reply]](ask: A, future: ReplyFuture[ReplyOf[A]])(using
        sender: AbstractActor[?]
    ): ReplyFuture[ReplyOf[A]]
    
    /** send ask message to this address, and set [[timeout]] milliseconds to get the respect [[Reply]], otherwise the
     *  [[ReplyFuture]] will set [[scala.concurrent.TimeoutException]].
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
    def ask[A <: M & Ask[_ <: Reply]](ask: A, f: ReplyFuture[ReplyOf[A]], timeout: Long)(using
        sender: AbstractActor[?]
    ): ReplyFuture[ReplyOf[A]]

    /** send a reply message to this address, this method can not be use by user, use [[AskStack.`return`]] instead.
     *  @param reply
     *    reply message to send
     *  @param sender
     *    who send this reply message
     */
    private[core] def reply(reply: Reply, sender: AbstractActor[?]): Unit

}
