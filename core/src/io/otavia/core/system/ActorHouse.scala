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

package io.otavia.core.system

import io.otavia.core.actor.{AbstractActor, StateActor}
import io.otavia.core.address.ActorAddress
import io.otavia.core.message.*
import io.otavia.core.reactor.Event
import io.otavia.core.system.ActorHouse.{EMPTY, HOUSE_STATUS}
import io.otavia.core.util.Nextable

import java.util.concurrent.atomic.AtomicInteger

/** House is [[io.otavia.core.actor.StateActor]] instance mount point. when a actor is creating by actor system, a house
 *  is creating at the same time, and mount the actor instance to the house instance.
 *
 *  @tparam M
 *    the message type of the mounted actor instance can handle
 */
private[core] class ActorHouse(val houseQueueHolder: HouseQueueHolder)
    extends Runnable
    with Nextable
    with AutoCloseable {

    private var dweller: AbstractActor[? <: Call] = _

    private val mailBox: MailBox = new MailBox(this)

    private var status: AtomicInteger      = new AtomicInteger(EMPTY)
    @volatile private var running: Boolean = false

    def setActor(actor: AbstractActor[? <: Call]): Unit = dweller = actor

    def actor: AbstractActor[? <: Call] = this.dweller

    def putNotice(notice: Notice): Unit = {}

    def putAsk(ask: Ask[?]): Unit = {}

    def putReply(reply: Reply): Unit = {}

    def putEvent(event: Event): Unit = { ??? }

    override def run(): Unit = {
        running = true
        // TODO
        running = false
    }

    private[core] def createActorAddress[M <: Call](): ActorAddress[M] = {
        val address = new ActorAddress[M](this)
        address
    }

    override def close(): Unit = {
        dweller.stop()
    }

}

object ActorHouse {

    type HOUSE_STATUS = Int
    val EMPTY: HOUSE_STATUS   = 0
    val READY: HOUSE_STATUS   = 1
    val RUNNING: HOUSE_STATUS = 2

}
