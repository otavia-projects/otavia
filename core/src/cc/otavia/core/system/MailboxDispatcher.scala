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
    private var askCursor: Nextable    = _
    private var noticeCursor: Nextable = _

    // Dispatch counters — reserved for future use if needed.

    // Channel inflight tracking for ChannelsActor dispatch.
    private var pendingChannels: QueueMap[AbstractChannel] = _

    // =========================================================================
    // Counter & inflight management
    // =========================================================================

    private[system] def initPendingChannels(): Unit =
        pendingChannels = new QueueMap[AbstractChannel]()

    private[system] def registerPendingChannel(channel: AbstractChannel): Unit =
        if (!pendingChannels.contains(channel.entityId)) pendingChannels.append(channel)

    /** Dispatch all pending messages in strict priority order.
     *
     *  Order: replies → exceptions → asks → notices → events → channels → deferred tasks.
     *  Barrier messages block subsequent asks/notices until all pending stacks complete.
     *
     *  Optimization: the [[house.hasMessages]] flag gates entry into the individual mailbox checks. This replaces 5
     *  volatile reads per dispatch with a single volatile read in the common case (actor is idle, no messages). The
     *  flag is a hint — false positives (flag true but all mailboxes empty) cause a few wasted volatile reads; false
     *  negatives (flag false but a mailbox is non-empty) are caught by [[ActorHouse.completeRunning]] which will
     *  re-schedule the house.
     */
    def dispatch(): Unit = {
        if !house.hasMessages then return

        if (house.replyMailbox.nonEmpty) dispatchReplies()
        if (house.exceptionMailbox.nonEmpty) dispatchExceptions()

        if (!house.inBarrier && house.askMailbox.nonEmpty) dispatchAsks()
        if (!house.inBarrier && house.noticeMailbox.nonEmpty) dispatchNotices()

        if (house.eventMailbox.nonEmpty) dispatchEvents()

        if (house.actorType == CHANNELS_ACTOR) dispatchChannels()

        runLaterTasks()

        // Clear the hint after a full dispatch cycle. If messages arrived during dispatch (concurrent put), the flag
        // will be re-set by the producer, and completeRunning will detect nonEmpty and re-schedule this house.
        house.clearHasMessages()
    }

    // =========================================================================
    // Replies & exceptions — highest priority, no barrier
    // =========================================================================

    private def dispatchReplies(): Unit = {
        var cursor = house.replyMailbox.getAll
        while (cursor != null) {
            val msg = cursor
            cursor = msg.next
            msg.unlink()
            house.dweller.receiveReply(msg.asInstanceOf[Envelope[?]])
        }
    }

    private def dispatchExceptions(): Unit = {
        var cursor = house.exceptionMailbox.getAll
        while (cursor != null) {
            val msg = cursor
            cursor = msg.next
            msg.unlink()
            house.dweller.receiveExceptionReply(msg.asInstanceOf[Envelope[?]])
        }
    }

    // =========================================================================
    // Asks — supports individual and batch modes
    // =========================================================================

    private def dispatchAsks(): Unit =
        if (house.dweller.batchable) dispatchBatchAsks() else dispatchIndividualAsks()

    private def dispatchIndividualAsks(): Unit = {
        if (askCursor == null) askCursor = house.askMailbox.getAll
        while (askCursor != null && !house.inBarrier) {
            val msg = askCursor
            askCursor = msg.next
            msg.unlink()
            val envelope = msg.asInstanceOf[Envelope[Ask[?]]]
            house.inBarrier = house.dweller.isBarrier(envelope.message)
            house.dweller.receiveAsk(envelope)
        }
    }

    private def dispatchBatchAsks(): Unit = {
        if (askCursor == null) askCursor = house.askMailbox.getAll
        val buf = ActorThread.threadBuffer[Envelope[Ask[?]]]
        while (askCursor != null && !house.inBarrier) {
            val envelope = askCursor.asInstanceOf[Envelope[Ask[?]]]
            askCursor = envelope.next
            envelope.unlink()
            val ask = envelope.message
            if (house.dweller.batchAskFilter(ask)) buf.addOne(envelope)
            else {
                if (buf.nonEmpty) handleBatchAsk(buf)
                house.inBarrier = house.dweller.isBarrier(ask)
                house.dweller.receiveAsk(envelope)
            }
        }
        if (buf.nonEmpty) handleBatchAsk(buf)
    }

    // =========================================================================
    // Notices — supports individual and batch modes
    // =========================================================================

    private def dispatchNotices(): Unit =
        if (house.dweller.batchable) dispatchBatchNotices() else dispatchIndividualNotices()

    private def dispatchIndividualNotices(): Unit = {
        if (noticeCursor == null) noticeCursor = house.noticeMailbox.getAll
        while (noticeCursor != null && !house.inBarrier) {
            val msg = noticeCursor
            noticeCursor = msg.next
            msg.unlink()
            val envelope = msg.asInstanceOf[Envelope[Notice]]
            house.inBarrier = house.dweller.isBarrier(envelope.message)
            house.dweller.receiveNotice(envelope)
        }
    }

    private def dispatchBatchNotices(): Unit = {
        if (noticeCursor == null) noticeCursor = house.noticeMailbox.getAll
        val buf = ActorThread.threadBuffer[Notice]
        while (noticeCursor != null && !house.inBarrier) {
            val envelope = noticeCursor.asInstanceOf[Envelope[Notice]]
            noticeCursor = envelope.next
            envelope.unlink()
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
            msg.unlink()
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
