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
import cc.otavia.core.channel.inflight.QueueMap
import cc.otavia.core.message.*
import cc.otavia.core.system.ActorHouse.*
import cc.otavia.core.util.Nextable

import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable
import scala.language.unsafeNulls

/** House is [[cc.otavia.core.actor.AbstractActor]] instance mount point. when a actor is creating by actor system, a
 *  house is creating at the same time, and mount the actor instance to the house instance.
 */
final private[core] class ActorHouse(val manager: HouseManager) extends ActorContext {

    // actor part
    private var dweller: AbstractActor[? <: Call] = _
    private var actorAddress: ActorAddress[Call]  = _
    private var dwellerId: Long                   = -1
    private var atp: Int                          = 0
    private var inBarrier: Boolean                = false
    private var revAsks: Long                     = 0
    private var sendAsks: Long                    = 0

    private var currentSendMessageId: Long = Long.MinValue

    // dispatch part
    private var isLB: Boolean = false

    private val noticeMailbox: MailBox    = new MailBox(this)
    private val askMailbox: MailBox       = new MailBox(this)
    private val replyMailbox: MailBox     = new MailBox(this)
    private val exceptionMailbox: MailBox = new MailBox(this)
    private val eventMailbox: MailBox     = new MailBox(this)

    private var tmpAskCursor: Nextable    = _
    private var tmpNoticeCursor: Nextable = _

    private[system] val status: AtomicInteger = new AtomicInteger(CREATED)

    @volatile private var preHouse: ActorHouse  = _
    @volatile private var nextHouse: ActorHouse = _

    @volatile private var _inHighPriorityQueue: Boolean = false

    private var pendingChannels: QueueMap[AbstractChannel] = _

    /** Generate a unique id for send [[Ask]] message. */
    def generateSendMessageId(): Long = {
        val id = currentSendMessageId
        currentSendMessageId += 1
        id
    }

    private def stackEndRate: Float =
        if (revAsks != 0) sendAsks.toFloat / revAsks.toFloat else Float.MaxValue

    def increaseSendCounter(): Unit = sendAsks += 1

    override def mountedThreadId: Int = manager.thread.index

    def highPriority: Boolean = (replyMailbox.size() > HIGH_PRIORITY_REPLY_SIZE) ||
        (eventMailbox.size() > HIGH_PRIORITY_EVENT_SIZE) || (stackEndRate < 0.6)

    def inHighPriorityQueue: Boolean = _inHighPriorityQueue

    def inHighPriorityQueue_=(value: Boolean): Unit =
        _inHighPriorityQueue = value

    def isReady: Boolean = status.get() == READY

    def isRunning: Boolean = status.get() == RUNNING

    def isWaiting: Boolean = status.get() == RUNNING

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
            case _: ChannelsActor[?] =>
                atp = CHANNELS_ACTOR
                pendingChannels = new QueueMap[AbstractChannel]()
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

    def actorType: Int = atp

    def cleanBarrier(): Unit = inBarrier = false

    def isBarrier: Boolean = inBarrier

    def pendingChannel(channel: AbstractChannel): Unit =
        if (!pendingChannels.contains(channel.entityId)) pendingChannels.append(channel)

    /** True if this house has not received [[Message]] or [[Event]] */
    def isEmpty: Boolean = askMailbox.isEmpty && noticeMailbox.isEmpty && replyMailbox.isEmpty &&
        eventMailbox.isEmpty && exceptionMailbox.nonEmpty

    /** True if this house has received some [[Message]] or [[Event]] */
    def nonEmpty: Boolean =
        askMailbox.nonEmpty || noticeMailbox.nonEmpty || replyMailbox.nonEmpty || eventMailbox.nonEmpty || exceptionMailbox.nonEmpty

    /** True if this house has received some [[Reply]] or [[Event]] */
    private def barrierNonEmpty: Boolean = replyMailbox.nonEmpty || eventMailbox.nonEmpty || exceptionMailbox.nonEmpty

    /** Call this method to async mount actor when created actor instance. */
    def mount(): Unit = if (status.compareAndSet(CREATED, MOUNTING)) manager.mount(this)

    /** Mount actor by schedule system. */
    def doMount(): Unit = {
        if (status.compareAndSet(MOUNTING, WAITING)) {
            dweller.mount()
            if (this.nonEmpty) waiting2ready()
        }
    }

    def putNotice(envelope: Envelope[?]): Unit = {
        if (system.isBusy && !ActorThread.currentThreadIsActorThread) {
            System.gc()
            Thread.sleep(ActorSystem.MEMORY_OVER_SLEEP)
        }
        put(envelope, noticeMailbox)
    }

    def putAsk(envelope: Envelope[?]): Unit = put(envelope, askMailbox)

    def putReply(envelope: Envelope[?]): Unit = put(envelope, replyMailbox)

    def putException(envelope: Envelope[?]): Unit = put(envelope, exceptionMailbox)

    def putEvent(event: Event): Unit = put(event, eventMailbox)

    private def put(msg: Nextable, mailBox: MailBox): Unit = {
        mailBox.put(msg)
        if (status.get() == WAITING) waiting2ready()
    }

    private[core] def putCallToHead(envelope: Envelope[?]): Unit = {
        noticeMailbox.putHead(envelope)
    }

    private def waiting2ready(): Unit = if (status.compareAndSet(WAITING, READY)) manager.ready(this)

    def schedule(): Unit = if (status.compareAndSet(READY, SCHEDULED)) {}

    def run(): Unit = {
        if (status.compareAndSet(SCHEDULED, RUNNING)) {
            if (replyMailbox.nonEmpty) dispatchReplies()
            if (exceptionMailbox.nonEmpty) dispatchExceptions()

            if (!inBarrier && askMailbox.nonEmpty) dispatchAsks()
            if (!inBarrier && noticeMailbox.nonEmpty) dispatchNotices()

            if (eventMailbox.nonEmpty) dispatchEvents()

            if (atp == CHANNELS_ACTOR) dispatchChannels()

            runLaterTasks()

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

    private def dispatchChannels(): Unit = {
        pendingChannels.resetIterator()
        for (channel <- pendingChannels) {
            channel.processPendingFutures()
            if (!channel.isPending) pendingChannels.remove(channel.entityId)
        }
    }

    private def dispatchAsks(): Unit = if (dweller.batchable) dispatchBatchAsks() else dispatchAsks0()

    private def dispatchAsks0(): Unit = {
        if (tmpAskCursor == null) tmpAskCursor = askMailbox.getAll
        while (tmpAskCursor != null && !inBarrier) {
            val msg = tmpAskCursor
            tmpAskCursor = msg.next
            msg.deChain()
            val envelope = msg.asInstanceOf[Envelope[Ask[?]]]
            inBarrier = dweller.isBarrier(envelope.message)
            revAsks += 1
            dweller.receiveAsk(envelope)
        }
    }

    private def dispatchBatchAsks(): Unit = {
        if (tmpAskCursor == null) tmpAskCursor = askMailbox.getAll
        val buf = ActorThread.threadBuffer[Envelope[Ask[?]]]
        while (tmpAskCursor != null && !inBarrier) {
            val envelope = tmpAskCursor.asInstanceOf[Envelope[Ask[?]]]
            tmpAskCursor = envelope.next
            envelope.deChain()
            val ask = envelope.message
            if (dweller.batchAskFilter(ask)) buf.addOne(envelope)
            else {
                if (buf.nonEmpty) handleBatchAsk(buf)
                inBarrier = dweller.isBarrier(ask)
                revAsks += 1
                dweller.receiveAsk(envelope)
            }
        }
        if (buf.nonEmpty) handleBatchAsk(buf)
    }

    private def dispatchNotices(): Unit = if (dweller.batchable) dispatchBatchNotices() else dispatchNotices0()

    private def dispatchNotices0(): Unit = {
        if (tmpNoticeCursor == null) tmpNoticeCursor = noticeMailbox.getAll
        while (tmpNoticeCursor != null && !inBarrier) {
            val msg = tmpNoticeCursor
            tmpNoticeCursor = msg.next
            msg.deChain()
            val envelop = msg.asInstanceOf[Envelope[Notice]]
            inBarrier = dweller.isBarrier(envelop.message)
            dweller.receiveNotice(envelop)
        }
    }

    private def dispatchBatchNotices(): Unit = {
        if (tmpNoticeCursor == null) tmpNoticeCursor = noticeMailbox.getAll
        val buf = ActorThread.threadBuffer[Notice]
        while (tmpNoticeCursor != null && !inBarrier) {
            val envelope = tmpNoticeCursor.asInstanceOf[Envelope[Notice]]
            tmpNoticeCursor = envelope.next
            envelope.deChain()
            val notice = envelope.message
            if (dweller.batchNoticeFilter(notice)) buf.addOne(notice)
            else {
                if (buf.nonEmpty) handleBatchNotice(buf)
                inBarrier = dweller.isBarrier(envelope.message)
                dweller.receiveNotice(envelope)
            }
        }
        if (buf.nonEmpty) handleBatchNotice(buf)
    }

    private def dispatchReplies(): Unit = {
        var cursor = replyMailbox.getAll
        while (cursor != null) {
            val msg = cursor
            cursor = msg.next
            msg.deChain()
            dweller.receiveReply(msg.asInstanceOf[Envelope[?]])
        }
    }

    private def dispatchExceptions(): Unit = {
        var cursor = exceptionMailbox.getAll
        while (cursor != null) {
            val msg = cursor
            cursor = msg.next
            msg.deChain()
            dweller.receiveExceptionReply(msg.asInstanceOf[Envelope[?]])
        }
    }

    private def dispatchEvents(): Unit = {
        var cursor = eventMailbox.getAll
        while (cursor != null) {
            val msg = cursor.asInstanceOf[Event]
            cursor = msg.next
            msg.deChain()
            dweller.receiveEvent(msg)
        }
    }

    private def handleBatchNotice(buf: mutable.ArrayBuffer[Notice]): Unit = {
        val notices = buf.toSeq
        buf.clear()
        dweller.receiveBatchNotice(notices)
    }

    private def handleBatchAsk(buf: mutable.ArrayBuffer[Envelope[Ask[?]]]): Unit = {
        val asks = buf.toSeq
        buf.clear()
        dweller.receiveBatchAsk(asks)
    }

    private def runLaterTasks(): Unit = {
        if (actorType == CHANNELS_ACTOR) {
            while (manager.laterTasks.nonEmpty) {
                val task = manager.laterTasks.removeHead()
                try {
                    task.run()
                } catch {
                    case t: Throwable =>
                        throw t
                }
            }
        }
    }

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
        SystemPropertyUtil.getInt("cc.otavia.core.priority.reply.size", HIGH_PRIORITY_REPLY_SIZE_DEFAULT)

    private val HIGH_PRIORITY_EVENT_SIZE_DEFAULT = 4
    private val HIGH_PRIORITY_EVENT_SIZE =
        SystemPropertyUtil.getInt("cc.otavia.core.priority.event.size", HIGH_PRIORITY_EVENT_SIZE_DEFAULT)

    // actor type
    val STATE_ACTOR           = 0
    val CHANNELS_ACTOR        = 1
    val SERVER_CHANNELS_ACTOR = 2

}
