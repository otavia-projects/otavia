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

package cc.otavia.core.system

import cc.otavia.common.SystemPropertyUtil
import cc.otavia.core.actor.*
import cc.otavia.core.address.ActorAddress
import cc.otavia.core.channel.AbstractChannel
import cc.otavia.core.message.*
import cc.otavia.core.system.ActorHouse.*
import cc.otavia.core.util.Nextable

import java.util.concurrent.atomic.AtomicInteger
import scala.language.unsafeNulls

/** The per-actor scheduling unit and mailbox container. Each [[AbstractActor]] is mounted to exactly one ActorHouse,
 *  which is owned by a [[HouseManager]] on a specific [[ActorThread]].
 *
 *  ActorHouse serves three roles:
 *    1. '''Scheduling state machine''': manages the lifecycle state (CREATED → MOUNTING → WAITING → READY → SCHEDULED
 *       → RUNNING) with atomic CAS transitions
 *       2. '''Mailbox container''': holds five separate [[Mailbox]] instances with priority-ordered dispatch
 *       3. '''[[cc.otavia.core.actor.ActorContext]] implementation''': provides runtime context to the actor
 *
 *  @param manager
 *    the [[HouseManager]] that owns this house
 */
final private[core] class ActorHouse(val manager: HouseManager) extends ActorContext {

    // =========================================================================
    // Section 1: ACTOR BINDING
    // =========================================================================

    private[system] var dweller: AbstractActor[? <: Call] = _
    private var actorAddress: ActorAddress[Call]  = _
    private var dwellerId: Long                   = -1
    private var atp: Int                          = 0
    private[system] var inBarrier: Boolean        = false

    private var currentSendMessageId: Long = Long.MinValue

    /** Whether this actor uses round-robin load balancing (RobinAddress with same-thread affinity). */
    private var isLB: Boolean = false

    // =========================================================================
    // Section 2: MAILBOXES
    // =========================================================================

    private[system] val noticeMailbox: Mailbox    = new Mailbox(this)
    private[system] val askMailbox: Mailbox       = new Mailbox(this)
    private[system] val replyMailbox: Mailbox     = new Mailbox(this)
    private[system] val exceptionMailbox: Mailbox = new Mailbox(this)
    private[system] val eventMailbox: Mailbox     = new Mailbox(this)

    private val dispatcher = new MailboxDispatcher(this)

    // =========================================================================
    // Section 3: SCHEDULING STATE MACHINE
    // =========================================================================

    private val status: AtomicInteger = new AtomicInteger(CREATED)

    @volatile private var preHouse: ActorHouse  = _
    @volatile private var nextHouse: ActorHouse = _

    @volatile private var _inHighPriorityQueue: Boolean = false

    // =========================================================================
    // Section 4: MESSAGE ID GENERATION
    // =========================================================================

    /** Generate a unique id for send [[Ask]] messages. Monotonically increasing per actor. */
    def generateSendMessageId(): Long = {
        val id = currentSendMessageId
        currentSendMessageId += 1
        id
    }

    def increaseSendCounter(): Unit = dispatcher.increaseSendCounter()

    // =========================================================================
    // Section 5: ActorContext IMPLEMENTATION
    // =========================================================================

    override def mountedThreadId: Int = manager.thread.index

    /** Whether this house should be scheduled with high priority. High priority is triggered by:
     *    - Excessive pending replies (> [[HIGH_PRIORITY_REPLY_SIZE]])
     *    - Excessive pending events (> [[HIGH_PRIORITY_EVENT_SIZE]])
     *    - Low stack-end-rate (more awaiting replies than sends — a backpressure signal)
     */
    def highPriority: Boolean = (replyMailbox.size() > HIGH_PRIORITY_REPLY_SIZE) ||
        (eventMailbox.size() > HIGH_PRIORITY_EVENT_SIZE) || (dispatcher.stackEndRate < 3)

    def inHighPriorityQueue: Boolean = _inHighPriorityQueue

    def inHighPriorityQueue_=(value: Boolean): Unit =
        _inHighPriorityQueue = value

    def isReady: Boolean = status.get() == READY
    def isRunning: Boolean = status.get() == RUNNING
    def isWaiting: Boolean = status.get() == WAITING

    // =========================================================================
    // Section 6: DOUBLY-LINKED LIST NODE (for HouseQueue insertion)
    // =========================================================================

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

    // =========================================================================
    // Section 7: ACTOR BINDING (called during ActorSystem.createActor)
    // =========================================================================

    /** Bind an actor instance to this house and classify it by type. */
    def setActor(actor: AbstractActor[? <: Call]): Unit = {
        dweller = actor
        actor match
            case _: AcceptorActor[?] => atp = SERVER_CHANNELS_ACTOR
            case _: ChannelsActor[?] =>
                atp = CHANNELS_ACTOR
                dispatcher.initPendingChannels()
            case _: StateActor[?] => atp = STATE_ACTOR
            case _                => throw new IllegalStateException("")
    }

    def setActorId(id: Long): Unit = dwellerId = id

    def setLB(boolean: Boolean): Unit = isLB = boolean

    override def isLoadBalance: Boolean = isLB

    def actor: AbstractActor[? <: Call] = this.dweller

    override def system: ActorSystem = manager.system

    override def address: ActorAddress[_ <: Message] = actorAddress

    override def actorId: Long = dwellerId

    /** Actor type classification — determines which scheduling queue the house enters. */
    def actorType: Int = atp

    // =========================================================================
    // Section 8: BARRIER MANAGEMENT
    // =========================================================================

    def cleanBarrier(): Unit = inBarrier = false

    def isBarrier: Boolean = inBarrier

    // =========================================================================
    // Section 9: CHANNEL INFLIGHT (for ChannelsActor only)
    // =========================================================================

    /** Register a channel as having pending outbound work. Only used for CHANNELS_ACTOR type houses. */
    def pendingChannel(channel: AbstractChannel): Unit = dispatcher.registerPendingChannel(channel)

    // =========================================================================
    // Section 10: MAILBOX QUERIES
    // =========================================================================

    /** True if all mailboxes are empty. */
    def isEmpty: Boolean = askMailbox.isEmpty && noticeMailbox.isEmpty && replyMailbox.isEmpty &&
        eventMailbox.isEmpty && exceptionMailbox.isEmpty

    /** True if any mailbox has messages. */
    def nonEmpty: Boolean =
        askMailbox.nonEmpty || noticeMailbox.nonEmpty || replyMailbox.nonEmpty || eventMailbox.nonEmpty || exceptionMailbox.nonEmpty

    /** True if barrier-relevant mailboxes (reply, event, exception) have messages. */
    private def barrierNonEmpty: Boolean = replyMailbox.nonEmpty || eventMailbox.nonEmpty || exceptionMailbox.nonEmpty

    // =========================================================================
    // Section 11: LIFECYCLE TRANSITIONS
    // =========================================================================

    /** Schedule this house for mounting. Transition: CREATED → MOUNTING. */
    def mount(): Unit = if (status.compareAndSet(CREATED, MOUNTING)) manager.mount(this)

    /** Execute the mount on the owning ActorThread. Transition: MOUNTING → WAITING. Calls the actor's afterMount
     *  hook and immediately transitions to READY if messages are already pending.
     */
    def doMount(): Unit = {
        if (status.compareAndSet(MOUNTING, WAITING)) {
            dweller.mount()
            if (this.nonEmpty) waiting2ready()
        }
    }

    /** Enqueue a notice message. Applies backpressure via sleep when the system is under memory pressure and the
     *  caller is not an ActorThread.
     */
    def putNotice(envelope: Envelope[?]): Unit = {
        if (system.isBusy && !ActorThread.currentThreadIsActorThread) {
            Thread.sleep(ActorSystem.MEMORY_OVER_SLEEP)
        }
        put(envelope, noticeMailbox)
    }

    def putAsk(envelope: Envelope[?]): Unit = put(envelope, askMailbox)

    def putReply(envelope: Envelope[?]): Unit = put(envelope, replyMailbox)

    def putException(envelope: Envelope[?]): Unit = put(envelope, exceptionMailbox)

    def putEvent(event: Event): Unit = put(event, eventMailbox)

    private def put(msg: Nextable, mailBox: Mailbox): Unit = {
        mailBox.put(msg)
        if (status.get() == WAITING) waiting2ready()
    }

    /** Place a notice at the head of the notice mailbox for priority processing. */
    private[core] def putCallToHead(envelope: Envelope[?]): Unit = {
        noticeMailbox.putHead(envelope)
    }

    private def waiting2ready(): Unit = if (status.compareAndSet(WAITING, READY)) manager.ready(this)

    /** Transition: READY → SCHEDULED. Called by the HouseQueue during dequeue. */
    def schedule(): Unit = if (status.compareAndSet(READY, SCHEDULED)) {}

    // =========================================================================
    // Section 12: DISPATCH ENGINE
    // =========================================================================

    /** Main dispatch loop. Transition: SCHEDULED → RUNNING. Delegates to [[MailboxDispatcher]] for priority-ordered
     *  message processing, then transitions to either READY or WAITING.
     */
    def run(): Unit = {
        if (status.compareAndSet(SCHEDULED, RUNNING)) {
            dispatcher.dispatch()
            completeRunning()
        }
    }

    private def completeRunning(): Unit = {
        if (!inBarrier) {
            if (nonEmpty) {
                if (status.compareAndSet(RUNNING, READY)) manager.ready(this)
            } else {
                if (status.compareAndSet(RUNNING, WAITING) && nonEmpty) waiting2ready()
            }
        } else {
            if (barrierNonEmpty) {
                if (status.compareAndSet(RUNNING, READY)) manager.ready(this)
            } else {
                if (status.compareAndSet(RUNNING, WAITING) && barrierNonEmpty) waiting2ready()
            }
        }
    }

    // =========================================================================
    // Section 13: ADDRESS MANAGEMENT
    // =========================================================================

    private[core] def createActorAddress[M <: Call](): ActorAddress[M] = {
        val address = new ActorAddress[M](this)
        if (actor.isInstanceOf[AutoCleanable])
            manager.thread.registerAddressRef(address)
        address
    }

    private[core] def createUntypedAddress(): Unit = {
        val address = new ActorAddress[Call](this)
        if (actor.isInstanceOf[AutoCleanable])
            manager.thread.registerAddressRef(address)
        actorAddress = address
    }

    override def toString: String = s"events=${eventMailbox.size()}, notices=${noticeMailbox.size()}, " +
        s"asks=${askMailbox.size()}, replies=${replyMailbox.size()}"

}

object ActorHouse {

    private type HOUSE_STATUS = Int

    /** Actor created but not yet scheduled for mounting. */
    private val CREATED: HOUSE_STATUS = 0

    /** Actor has been scheduled for mounting on its owning ActorThread. */
    private val MOUNTING: HOUSE_STATUS = 1

    /** Actor is mounted and idle — no messages or events to process. */
    private val WAITING: HOUSE_STATUS = 2

    /** Actor has messages or events waiting to be processed. */
    private val READY: HOUSE_STATUS = 3

    /** Actor has been dequeued and is waiting for the ActorThread to execute it. */
    private val SCHEDULED: HOUSE_STATUS = 4

    /** Actor is currently executing on the ActorThread. */
    private val RUNNING: HOUSE_STATUS = 5

    private val HIGH_PRIORITY_REPLY_SIZE_DEFAULT = 2
    private val HIGH_PRIORITY_REPLY_SIZE =
        SystemPropertyUtil.getInt("cc.otavia.core.priority.reply.size", HIGH_PRIORITY_REPLY_SIZE_DEFAULT)

    private val HIGH_PRIORITY_EVENT_SIZE_DEFAULT = 4
    private val HIGH_PRIORITY_EVENT_SIZE =
        SystemPropertyUtil.getInt("cc.otavia.core.priority.event.size", HIGH_PRIORITY_EVENT_SIZE_DEFAULT)

    // Actor type classification — determines scheduling queue assignment
    val STATE_ACTOR           = 0
    val CHANNELS_ACTOR        = 1
    val SERVER_CHANNELS_ACTOR = 2

}
