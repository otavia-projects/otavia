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

import io.otavia.core.actor.Actor
import io.otavia.core.message.*
import io.otavia.core.reactor.Event
import io.otavia.core.stack.ReplyWaiter

/** Address is the facade of an actor. Actors cannot call each other directly, only send messages to the actor via its
 *  address.
 *
 *  @tparam M
 *    the message type that this actor can receive.
 */
trait Address[-M <: Ask[? <: Reply] | Notice] extends EventableAddress {

    /** send notice message to this address
     *
     *  @param notice
     *    notice message to send
     */
    def notice(notice: M & Notice): Unit

    /** send ask message to this address
     *  @param ask
     *    ask message
     *  @param waiter
     *    reply message waiter for this ask message
     *  @param sender
     *    who send this ask message
     *  @tparam A
     *    the type of ask message
     *  @tparam R
     *    the type of reply message
     */
    def ask[A <: M & Ask[? <: Reply]](ask: A, waiter: ReplyWaiter[ReplyOf[A]])(using sender: Actor[?]): Unit

    /** send a reply message to this address
     *  @param reply
     *    reply message
     */
    private[core] def reply(reply: Reply): Unit

}
