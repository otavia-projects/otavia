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

import io.otavia.core.actor.*
import io.otavia.core.address.ActorAddress
import io.otavia.core.message.*
import io.otavia.core.reactor.Event
import io.otavia.core.system.ActorHouse.*
import io.otavia.core.util.{Nextable, SystemPropertyUtil}

import java.util.concurrent.atomic.AtomicInteger
import scala.language.unsafeNulls

/** House is [[io.otavia.core.actor.StateActor]] instance mount point. when a actor is creating by actor system, a house
 *  is creating at the same time, and mount the actor instance to the house instance.
 *
 *  @tparam M
 *    the message type of the mounted actor instance can handle
 */
private[core] class ActorHouse(val manager: HouseQueueManager) extends Runnable with AutoCloseable {

    private var dweller: AbstractActor[? <: Call] = _
    private var atp: Int                          = 0

    private val noticeMailbox: MailBox = new MailBox(this)
    private val askMailbox: MailBox    = new MailBox(this)
    private val replyMailbox: MailBox  = new MailBox(this)
    private val eventMailbox: MailBox  = new MailBox(this)

    private val status: AtomicInteger      = new AtomicInteger(CREATED)
    @volatile private var running: Boolean = false

    @volatile private var preHouse: ActorHouse  = _
    @volatile private var nextHouse: ActorHouse = _

    def highPriority: Boolean = (replyMailbox.size() > HIGH_PRIORITY_REPLY_SIZE) ||
        (eventMailbox.size() > HIGH_PRIORITY_EVENT_SIZE)

    def next_=(house: ActorHouse): Unit = nextHouse = house

    def next: ActorHouse | Null = nextHouse

    def isTail: Boolean = nextHouse == null

    def pre_=(house: ActorHouse): Unit = preHouse = house

    def pre: ActorHouse | Null = preHouse

    def isHead: Boolean = preHouse == null

    def deChain(): Unit = {
        nextHouse = null
        preHouse = null
    }

    def setActor(actor: AbstractActor[? <: Call]): Unit = {
        dweller = actor
        actor match
            case _: AcceptorActor[?] => atp = SERVER_CHANNELS_ACTOR
            case _: ChannelsActor[?] => atp = CHANNELS_ACTOR
            case _: StateActor[?]    => atp = STATE_ACTOR
            case _                   => throw new IllegalStateException("")
    }

    def actor: AbstractActor[? <: Call] = this.dweller

    def actorType: Int = atp

    /** True if this house has not received [[Message]] or [[Event]] */
    def isEmpty: Boolean = askMailbox.isEmpty && noticeMailbox.isEmpty && replyMailbox.isEmpty && eventMailbox.isEmpty

    /** True if this house has received some [[Message]] or [[Event]] */
    def nonEmpty: Boolean =
        askMailbox.nonEmpty || noticeMailbox.nonEmpty || replyMailbox.nonEmpty || eventMailbox.nonEmpty

    /** Call this method to async mount actor when created actor instance. */
    def mount(): Unit = {
        if (status.compareAndSet(CREATED, MOUNTING)) {
            manager.mount(this)
        }
    }

    /** Mount actor by schedule system. */
    def doMount(): Unit = {
        if (status.compareAndSet(MOUNTING, EMPTY)) {
            dweller.mount()
            if (this.nonEmpty) doReady()
        }
    }

    def putNotice(notice: Notice): Unit = put(notice, noticeMailbox)

    def putAsk(ask: Ask[?]): Unit = put(ask, askMailbox)

    def putReply(reply: Reply): Unit = put(reply, replyMailbox)

    def putEvent(event: Event): Unit = put(event, eventMailbox)

    private final def put(nextable: Nextable, mailBox: MailBox): Unit = {
        mailBox.put(nextable)

        if (status.get() == EMPTY) {
            doReady()
        } else if (status.get() == READY) {
            //
        } else if (status.get() == RUNNING) {
            //
        }

    }

    private def doReady(): Unit = if (status.compareAndSet(EMPTY, READY)) {
        manager.ready(this)
    }

    override def run(): Unit = {
        running = true
        // TODO
        running = false
    }

    private[core] def createActorAddress[M <: Call](): ActorAddress[M] = {
        val address = new ActorAddress[M](this)
        address
    }

    private[core] def createUntypedAddress(): ActorAddress[?] = {
        new ActorAddress[Call](this)
    }

    override def close(): Unit = {
        dweller.stop()
    }

}

object ActorHouse {

    private type HOUSE_STATUS = Int
    private val CREATED: HOUSE_STATUS  = 0
    private val MOUNTING: HOUSE_STATUS = 1
    private val EMPTY: HOUSE_STATUS    = 2
    private val READY: HOUSE_STATUS    = 3
    private val RUNNING: HOUSE_STATUS  = 4

    private val HIGH_PRIORITY_REPLY_SIZE_DEFAULT = 4
    private val HIGH_PRIORITY_REPLY_SIZE =
        SystemPropertyUtil.getInt("io.otavia.core.priority.reply.size", HIGH_PRIORITY_REPLY_SIZE_DEFAULT)

    private val HIGH_PRIORITY_EVENT_SIZE_DEFAULT = 4
    private val HIGH_PRIORITY_EVENT_SIZE =
        SystemPropertyUtil.getInt("io.otavia.core.priority.event.size", HIGH_PRIORITY_EVENT_SIZE_DEFAULT)

    // actor type
    val STATE_ACTOR           = 0
    val CHANNELS_ACTOR        = 1
    val SERVER_CHANNELS_ACTOR = 2

}
