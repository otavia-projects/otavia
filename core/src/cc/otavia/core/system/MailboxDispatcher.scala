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

import cc.otavia.core.actor.*
import cc.otavia.core.channel.AbstractChannel
import cc.otavia.core.channel.inflight.QueueMap
import cc.otavia.core.message.*
import cc.otavia.core.system.ActorHouse.*
import cc.otavia.core.util.Nextable

import scala.collection.mutable
import scala.language.unsafeNulls

/** Message dispatch engine for an [[ActorHouse]]. Handles priority-ordered dispatch of all mailbox contents:
 *  replies → exceptions → asks → notices → events → channels → deferred tasks.
 *
 *  Supports both individual and batch dispatch modes, barrier semantics, and transient cursors that survive
 *  across multiple [[run]] calls when a dispatch doesn't fully drain.
 *
 *  @param house
 *    the owning ActorHouse, providing access to mailboxes, the actor, and scheduling state
 */
private[core] class MailboxDispatcher(private val house: ActorHouse) {

    // Transient cursors for batch dispatch. Survive across multiple run() calls if a dispatch doesn't fully drain.
    private var tmpAskCursor: Nextable    = _
    private var tmpNoticeCursor: Nextable = _

    // Dispatch counters for priority calculation.
    private[system] var revAsks: Long  = 0
    private var sendAsks: Long = 0

    // Channel inflight tracking for ChannelsActor dispatch.
    private var pendingChannels: QueueMap[AbstractChannel] = _

    // =========================================================================
    // Counter & inflight management
    // =========================================================================

    private[system] def initPendingChannels(): Unit =
        pendingChannels = new QueueMap[AbstractChannel]()

    private[system] def increaseSendCounter(): Unit = sendAsks += 1

    private[system] def stackEndRate: Int =
        if (revAsks != 0) (sendAsks * 5 / revAsks).toInt else Int.MaxValue

    private[system] def registerPendingChannel(channel: AbstractChannel): Unit =
        if (!pendingChannels.contains(channel.entityId)) pendingChannels.append(channel)

    /** Dispatch all pending messages in strict priority order.
     *
     *  Order: replies → exceptions → asks → notices → events → channels → deferred tasks.
     *  Barrier messages block subsequent asks/notices until all pending stacks complete.
     */
    def dispatch(): Unit = {
        if (house.replyMailbox.nonEmpty) dispatchReplies()
        if (house.exceptionMailbox.nonEmpty) dispatchExceptions()

        if (!house.inBarrier && house.askMailbox.nonEmpty) dispatchAsks()
        if (!house.inBarrier && house.noticeMailbox.nonEmpty) dispatchNotices()

        if (house.eventMailbox.nonEmpty) dispatchEvents()

        if (house.actorType == CHANNELS_ACTOR) dispatchChannels()

        runLaterTasks()
    }

    // =========================================================================
    // Replies & exceptions — highest priority, no barrier
    // =========================================================================

    private def dispatchReplies(): Unit = {
        var cursor = house.replyMailbox.getAll
        while (cursor != null) {
            val msg = cursor
            cursor = msg.next
            msg.deChain()
            house.dweller.receiveReply(msg.asInstanceOf[Envelope[?]])
        }
    }

    private def dispatchExceptions(): Unit = {
        var cursor = house.exceptionMailbox.getAll
        while (cursor != null) {
            val msg = cursor
            cursor = msg.next
            msg.deChain()
            house.dweller.receiveExceptionReply(msg.asInstanceOf[Envelope[?]])
        }
    }

    // =========================================================================
    // Asks — supports individual and batch modes
    // =========================================================================

    private def dispatchAsks(): Unit =
        if (house.dweller.batchable) dispatchBatchAsks() else dispatchAsks0()

    private def dispatchAsks0(): Unit = {
        if (tmpAskCursor == null) tmpAskCursor = house.askMailbox.getAll
        while (tmpAskCursor != null && !house.inBarrier) {
            val msg = tmpAskCursor
            tmpAskCursor = msg.next
            msg.deChain()
            val envelope = msg.asInstanceOf[Envelope[Ask[?]]]
            house.inBarrier = house.dweller.isBarrier(envelope.message)
            revAsks += 1
            house.dweller.receiveAsk(envelope)
        }
    }

    private def dispatchBatchAsks(): Unit = {
        if (tmpAskCursor == null) tmpAskCursor = house.askMailbox.getAll
        val buf = ActorThread.threadBuffer[Envelope[Ask[?]]]
        while (tmpAskCursor != null && !house.inBarrier) {
            val envelope = tmpAskCursor.asInstanceOf[Envelope[Ask[?]]]
            tmpAskCursor = envelope.next
            envelope.deChain()
            val ask = envelope.message
            if (house.dweller.batchAskFilter(ask)) buf.addOne(envelope)
            else {
                if (buf.nonEmpty) handleBatchAsk(buf)
                house.inBarrier = house.dweller.isBarrier(ask)
                revAsks += 1
                house.dweller.receiveAsk(envelope)
            }
        }
        if (buf.nonEmpty) handleBatchAsk(buf)
    }

    // =========================================================================
    // Notices — supports individual and batch modes
    // =========================================================================

    private def dispatchNotices(): Unit =
        if (house.dweller.batchable) dispatchBatchNotices() else dispatchNotices0()

    private def dispatchNotices0(): Unit = {
        if (tmpNoticeCursor == null) tmpNoticeCursor = house.noticeMailbox.getAll
        while (tmpNoticeCursor != null && !house.inBarrier) {
            val msg = tmpNoticeCursor
            tmpNoticeCursor = msg.next
            msg.deChain()
            val envelope = msg.asInstanceOf[Envelope[Notice]]
            house.inBarrier = house.dweller.isBarrier(envelope.message)
            house.dweller.receiveNotice(envelope)
        }
    }

    private def dispatchBatchNotices(): Unit = {
        if (tmpNoticeCursor == null) tmpNoticeCursor = house.noticeMailbox.getAll
        val buf = ActorThread.threadBuffer[Notice]
        while (tmpNoticeCursor != null && !house.inBarrier) {
            val envelope = tmpNoticeCursor.asInstanceOf[Envelope[Notice]]
            tmpNoticeCursor = envelope.next
            envelope.deChain()
            val notice = envelope.message
            if (house.dweller.batchNoticeFilter(notice)) {
                buf.addOne(notice)
                envelope.recycle()
            } else {
                if (buf.nonEmpty) handleBatchNotice(buf)
                house.inBarrier = house.dweller.isBarrier(envelope.message)
                house.dweller.receiveNotice(envelope)
            }
        }
        if (buf.nonEmpty) handleBatchNotice(buf)
    }

    // =========================================================================
    // Events
    // =========================================================================

    private def dispatchEvents(): Unit = {
        var cursor = house.eventMailbox.getAll
        while (cursor != null) {
            val msg = cursor.asInstanceOf[Event]
            cursor = msg.next
            msg.deChain()
            house.dweller.receiveEvent(msg)
        }
    }

    // =========================================================================
    // Channel inflight (ChannelsActor only)
    // =========================================================================

    private def dispatchChannels(): Unit = {
        pendingChannels.resetIterator()
        for (channel <- pendingChannels) {
            channel.processPendingFutures()
            if (!channel.isPending) pendingChannels.remove(channel.entityId)
        }
    }

    // =========================================================================
    // Batch helpers
    // =========================================================================

    private def handleBatchNotice(buf: mutable.ArrayBuffer[Notice]): Unit = {
        val notices = buf.toSeq
        buf.clear()
        house.dweller.receiveBatchNotice(notices)
    }

    private def handleBatchAsk(buf: mutable.ArrayBuffer[Envelope[Ask[?]]]): Unit = {
        val asks = buf.toSeq
        buf.clear()
        house.dweller.receiveBatchAsk(asks)
    }

    // =========================================================================
    // Deferred tasks
    // =========================================================================

    private def runLaterTasks(): Unit = {
        if (house.actorType == CHANNELS_ACTOR) {
            val tasks = house.manager.laterTasks
            while (tasks.nonEmpty) {
                val task = tasks.removeHead()
                try task.run()
                catch { case t: Throwable => throw t }
            }
        }
    }

}
