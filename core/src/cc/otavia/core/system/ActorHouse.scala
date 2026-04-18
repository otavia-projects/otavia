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
    private var actorTypeKind: Int                          = 0
    private[system] var inBarrier: Boolean        = false

    private var currentSendMessageId: Long = Long.MinValue

    /** Whether this actor uses round-robin load balancing (RobinAddress with same-thread affinity). */
    private var loadBalanced: Boolean = false

    // =========================================================================
    // Section 2: MAILBOXES
    // =========================================================================

    private[system] val noticeMailbox: Mailbox    = new Mailbox(this)
    private[system] val askMailbox: Mailbox       = new Mailbox(this)
    private[system] val replyMailbox: Mailbox     = new Mailbox(this)
    private[system] val exceptionMailbox: Mailbox = new Mailbox(this)
    private[system] val eventMailbox: Mailbox     = new Mailbox(this)

    private val dispatcher = new MailboxDispatcher(this)

    /** Aggregated hint flag for mailbox occupancy. Replaces 5 volatile reads in the dispatch fast-path with a single
     *  volatile read.
     *
     *  Write side: set to true by any thread in [[put]] via lazySet (unordered, lowest overhead — a store-store
     *  barrier only). The write may be delayed relative to other threads' reads, but this is safe because:
     *    - If the flag reads false but a mailbox is non-empty, [[completeRunning]] will detect it via the full
     *      [[nonEmpty]] check and re-transition the house to READY.
     *    - If the flag reads true but all mailboxes are empty, [[MailboxDispatcher.dispatch]] performs a few cheap
     *      volatile reads on individual mailbox counts and finds nothing — a benign false positive.
     *
     *  Read side: checked by the owning ActorThread in [[MailboxDispatcher.dispatch]] and [[run]] before incurring
     *  the cost of 5 individual mailbox nonEmpty checks.
     */
    @volatile private var _hasMessages: Boolean = false

    // =========================================================================
    // Section 3: SCHEDULING STATE MACHINE
    // =========================================================================

    private val status: AtomicInteger = new AtomicInteger(CREATED)

    @volatile private var prevHouse: ActorHouse  = _
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

    def prev_=(house: ActorHouse): Unit = prevHouse = house
    def prev: ActorHouse | Null = prevHouse
    def isHead: Boolean = prevHouse == null

    def unlink(): Unit = {
        nextHouse = null
        prevHouse = null
    }

    // =========================================================================
    // Section 7: ACTOR BINDING (called during ActorSystem.createActor)
    // =========================================================================

    /** Bind an actor instance to this house and classify it by type. */
    def setActor(actor: AbstractActor[? <: Call]): Unit = {
        dweller = actor
        actor match
            case _: AcceptorActor[?] => actorTypeKind = SERVER_CHANNELS_ACTOR
            case _: ChannelsActor[?] =>
                actorTypeKind = CHANNELS_ACTOR
                dispatcher.initPendingChannels()
            case _: StateActor[?] => actorTypeKind = STATE_ACTOR
            case _                => throw new IllegalStateException("")
    }

    def setActorId(id: Long): Unit = dwellerId = id

    def setLoadBalanced(boolean: Boolean): Unit = loadBalanced = boolean

    override def isLoadBalance: Boolean = loadBalanced

    def actor: AbstractActor[? <: Call] = this.dweller

    override def system: ActorSystem = manager.system

    override def address: ActorAddress[_ <: Message] = actorAddress

    override def actorId: Long = dwellerId

    /** Actor type classification — determines which scheduling queue the house enters. */
    def actorType: Int = actorTypeKind

    // =========================================================================
    // Section 8: BARRIER MANAGEMENT
    // =========================================================================

    def clearBarrier(): Unit = inBarrier = false

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

    /** Single-read hint for mailbox occupancy — see [[_hasMessages]] for semantics. */
    def hasMessages: Boolean = _hasMessages

    /** Clear the aggregated hint after a dispatch cycle that drained all mailboxes. Only called by the owning
     *  ActorThread. A subsequent [[put]] from any thread will re-set the flag.
     */
    def clearHasMessages(): Unit = _hasMessages = false

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
            if (this.hasMessages && this.nonEmpty) waitingToReady()
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

    private def put(msg: Nextable, mailbox: Mailbox): Unit = {
        mailbox.put(msg)
        _hasMessages = true // relaxed write is sufficient — see _hasMessages scaladoc
        if (status.get() == WAITING) waitingToReady()
    }

    /** Place a notice at the head of the notice mailbox for priority processing. */
    private[core] def putCallToHead(envelope: Envelope[?]): Unit = {
        noticeMailbox.putHead(envelope)
        _hasMessages = true
    }

    private def waitingToReady(): Unit = if (status.compareAndSet(WAITING, READY)) manager.ready(this)

    /** Transition: READY → SCHEDULED. Called by the HouseQueue during dequeue. */
    def schedule(): Unit = if (status.compareAndSet(READY, SCHEDULED)) {}

    // =========================================================================
    // Section 12: DISPATCH ENGINE
    // =========================================================================

    /** Main dispatch entry point. Transition: SCHEDULED → RUNNING.
     *
     *  Dispatch-loop optimization: after draining mailboxes, if new messages have arrived during dispatch and no
     *  other actors on this thread's scheduling queue are waiting, the house re-enters dispatch immediately instead
     *  of going through the full state machine cycle (RUNNING → READY → enqueue → dequeue → SCHEDULED → RUNNING).
     *  This eliminates 3 CAS operations + 1 SpinLock-protected queue enqueue/dequeue per batch for continuously
     *  busy actors.
     *
     *  The guard `!manager.hasOtherReady(this)` prevents starvation: when other actors are waiting in the thread's
     *  scheduling queue, this house must surrender and go through normal re-scheduling to give them a chance to run.
     */
    def run(): Unit = {
        if (status.compareAndSet(SCHEDULED, RUNNING)) {
            dispatcher.dispatch()
            dispatchLoop()
        }
    }

    /** Try to re-dispatch without leaving the RUNNING state. Falls through to [[completeRunning]] (full state
     *  machine transition) when other actors are waiting or this actor enters a barrier.
     */
    private def dispatchLoop(): Unit = {
        var continue = true
        while continue do
            if inBarrier then
                // Barrier blocks ask/notice dispatch — must exit loop for state machine transition
                continue = false
            else if !hasMessages then
                // No new messages arrived during dispatch — idle, exit for state machine transition
                continue = false
            else if manager.hasOtherReady(this) then
                // Other actors are waiting — surrender to prevent starvation
                continue = false
            else
                // Messages arrived and no competition — re-dispatch in-place
                dispatcher.dispatch()
        completeRunning()
    }

    private def completeRunning(): Unit = {
        if (!inBarrier) {
            if (nonEmpty) {
                if (status.compareAndSet(RUNNING, READY)) manager.ready(this)
            } else {
                if (status.compareAndSet(RUNNING, WAITING) && nonEmpty) waitingToReady()
            }
        } else {
            if (barrierNonEmpty) {
                if (status.compareAndSet(RUNNING, READY)) manager.ready(this)
            } else {
                if (status.compareAndSet(RUNNING, WAITING) && barrierNonEmpty) waitingToReady()
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
