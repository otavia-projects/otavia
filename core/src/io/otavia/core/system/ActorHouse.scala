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
private[core] class ActorHouse(val manager: HouseManager) extends Runnable with AutoCloseable {

    private var dweller: AbstractActor[? <: Call] = _
    private var atp: Int                          = 0

    private val noticeMailbox: MailBox = new MailBox(this)
    private val askMailbox: MailBox    = new MailBox(this)
    private val replyMailbox: MailBox  = new MailBox(this)
    private val eventMailbox: MailBox  = new MailBox(this)

    private[system] val status: AtomicInteger = new AtomicInteger(CREATED)

    @volatile private var preHouse: ActorHouse  = _
    @volatile private var nextHouse: ActorHouse = _

    @volatile private var _inHighPriorityQueue: Boolean = false

    def highPriority: Boolean = (replyMailbox.size() > HIGH_PRIORITY_REPLY_SIZE) ||
        (eventMailbox.size() > HIGH_PRIORITY_EVENT_SIZE)

    def inHighPriorityQueue: Boolean = _inHighPriorityQueue

    def inHighPriorityQueue_=(value: Boolean): Unit =
        _inHighPriorityQueue = value

    def statusReady: Boolean = status.get() == READY

    def statusRunning: Boolean = status.get() == RUNNING

    def statusWaiting: Boolean = status.get() == RUNNING

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
        if (status.compareAndSet(MOUNTING, WAITING)) {
            dweller.mount()
            if (this.nonEmpty) empty2ready()
        }
    }

    def putNotice(notice: Notice): Unit = put(notice, noticeMailbox)

    def putAsk(ask: Ask[?]): Unit = put(ask, askMailbox)

    def putReply(reply: Reply): Unit = put(reply, replyMailbox)

    def putEvent(event: Event): Unit = put(event, eventMailbox)

    private final def put(nextable: Nextable, mailBox: MailBox): Unit = {
        mailBox.put(nextable)

        if (status.get() == WAITING) {
            empty2ready()
        } else {
            manager.change(this)
        }

    }

    private def empty2ready(): Unit = if (status.compareAndSet(WAITING, READY)) {
        manager.ready(this)
    }

    def schedule(): Unit = if (status.compareAndSet(READY, SCHEDULED)) {}

    override def run(): Unit = { // TODO: support batch receive
        if (status.compareAndSet(SCHEDULED, RUNNING)) {
            if (replyMailbox.nonEmpty) {
                var cursor = replyMailbox.getChain(dweller.niceReply)
                while (cursor != null) {
                    val msg = cursor
                    cursor = msg.next
                    msg.dechain()
                    dweller.receiveReply(msg.asInstanceOf[Reply])
                }
            }
            if (eventMailbox.nonEmpty) {
                var cursor = eventMailbox.getChain(dweller.niceEvent)
                while (cursor != null) {
                    val msg = cursor
                    cursor = msg.next
                    msg.dechain()
                    dweller.receiveEvent(msg.asInstanceOf[Event])
                }
            }
            if (askMailbox.nonEmpty) {
                var cursor = askMailbox.getChain(dweller.niceAsk)
                while (cursor != null) {
                    val msg = cursor
                    cursor = msg.next
                    msg.dechain()
                    dweller.receiveAsk(msg.asInstanceOf[Ask[? <: Reply]])
                }
            }
            if (noticeMailbox.nonEmpty) {
                var cursor = noticeMailbox.getChain(dweller.niceNotice)
                while (cursor != null) {
                    val msg = cursor
                    cursor = msg.next
                    msg.dechain()
                    dweller.receiveNotice(msg.asInstanceOf[Notice])
                }
            }

            if (nonEmpty) {
                if (status.compareAndSet(RUNNING, READY)) manager.ready(this)
            } else {
                status.compareAndSet(RUNNING, WAITING)
            }
        }
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

    /** When [[Actor]] created and still not schedule to mount */
    private val CREATED: HOUSE_STATUS = 0

    /** When [[Actor]] has schedule mounting */
    private val MOUNTING: HOUSE_STATUS = 1

    /** When [[Actor]] has mounted, and not have any [[Message]] or [[Event]] to handle. */
    private val WAITING: HOUSE_STATUS = 2

    /** When [[Actor]] has some [[Message]] or [[Event]] wait to handle. */
    private val READY: HOUSE_STATUS = 3

    private val SCHEDULED: HOUSE_STATUS = 4

    /** The [[ActorThread]] is running this [[Actor]] */
    private val RUNNING: HOUSE_STATUS = 5

    private val HIGH_PRIORITY_REPLY_SIZE_DEFAULT = 2
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
