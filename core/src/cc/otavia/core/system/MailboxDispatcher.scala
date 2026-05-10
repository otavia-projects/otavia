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

/** Message dispatch engine mixed into [[ActorHouse]]. Handles priority-ordered dispatch of all mailbox contents: replies
 *  (including exceptions) → asks → notices → events → channels → deferred tasks.
 *
 *  Supports both individual and batch dispatch modes, barrier semantics, and transient cursors that survive across
 *  multiple [[run]] calls when a dispatch doesn't fully drain.
 */
private[core] trait MailboxDispatcher { this: ActorHouse =>

    // Transient cursors for batch dispatch. Survive across multiple run() calls if a dispatch doesn't fully drain.
    private var askCursor: Nextable    = _
    private var noticeCursor: Nextable = _

    // Channel inflight tracking for ChannelsActor dispatch.
    private var pendingChannels: QueueMap[AbstractChannel] = _

    // =========================================================================
    // Inflight management
    // =========================================================================

    private[system] def initPendingChannels(): Unit =
        pendingChannels = new QueueMap[AbstractChannel]()

    private[system] def registerPendingChannel(channel: AbstractChannel): Unit =
        if (!pendingChannels.contains(channel.entityId)) pendingChannels.append(channel)

    /** Dispatch all pending messages in strict priority order.
     *
     *  Order: replies (including exceptions) → asks → notices → events → channels → deferred tasks. Barrier messages
     *  block subsequent asks/notices until all pending stacks complete.
     *
     *  Optimization: the [[hasMessages]] flag gates entry into the individual mailbox checks. This replaces volatile
     *  reads per dispatch with a single volatile read in the common case (actor is idle, no messages). The flag is a
     *  hint — false positives (flag true but all mailboxes empty) cause a few wasted volatile reads; false negatives
     *  (flag false but a mailbox is non-empty) are caught by [[completeRunning]] which will re-schedule the house.
     */
    def dispatch(): Unit = {
        if !hasMessages then return

        if (replyMailbox.nonEmpty) dispatchReplyMailbox()

        if (!inBarrier && askMailbox.nonEmpty) dispatchAsks()
        if (!inBarrier && noticeMailbox.nonEmpty) dispatchNotices()

        if (eventMailbox != null && eventMailbox.nonEmpty) dispatchEvents()

        if (actorType == CHANNELS_ACTOR) dispatchChannels()

        runLaterTasks()

        // Clear the hint after a full dispatch cycle. If messages arrived during dispatch (concurrent put), the flag
        // will be re-set by the producer, and completeRunning will detect nonEmpty and re-schedule this house.
        clearHasMessages()
    }

    // =========================================================================
    // Replies & exceptions — highest priority, no barrier
    // =========================================================================

    private def dispatchReplyMailbox(): Unit = {
        var cursor = replyMailbox.getAll
        while (cursor != null) {
            val msg = cursor
            cursor = msg.next
            msg.clearNext()
            val envelope = msg.asInstanceOf[Envelope]
            if (envelope.isExceptionReply) dweller.receiveExceptionReply(envelope)
            else dweller.receiveReply(envelope)
        }
    }

    // =========================================================================
    // Asks — supports individual and batch modes
    // =========================================================================

    private def dispatchAsks(): Unit =
        if (dweller.batchable) dispatchBatchAsks() else dispatchIndividualAsks()

    private def dispatchIndividualAsks(): Unit = {
        if (askCursor == null) askCursor = askMailbox.getAll
        while (askCursor != null && !inBarrier) {
            val msg = askCursor
            askCursor = msg.next
            msg.clearNext()
            val envelope = msg.asInstanceOf[Envelope]
            inBarrier = dweller.isBarrier(envelope.message.asInstanceOf[Call])
            dweller.receiveAsk(envelope)
        }
    }

    private def dispatchBatchAsks(): Unit = {
        if (askCursor == null) askCursor = askMailbox.getAll
        val buf = ActorThread.threadBuffer[Envelope]
        while (askCursor != null && !inBarrier) {
            val envelope = askCursor.asInstanceOf[Envelope]
            askCursor = envelope.next
            envelope.clearNext()
            val ask = envelope.message.asInstanceOf[Ask[?]]
            if (dweller.batchAskFilter(ask)) buf.addOne(envelope)
            else {
                if (buf.nonEmpty) handleBatchAsk(buf)
                inBarrier = dweller.isBarrier(ask)
                dweller.receiveAsk(envelope)
            }
        }
        if (buf.nonEmpty) handleBatchAsk(buf)
    }

    // =========================================================================
    // Notices — supports individual and batch modes
    // =========================================================================

    private def dispatchNotices(): Unit =
        if (dweller.batchable) dispatchBatchNotices() else dispatchIndividualNotices()

    private def dispatchIndividualNotices(): Unit = {
        if (noticeCursor == null) noticeCursor = noticeMailbox.getAll
        while (noticeCursor != null && !inBarrier) {
            val msg = noticeCursor
            noticeCursor = msg.next
            msg.clearNext()
            val envelope = msg.asInstanceOf[Envelope]
            inBarrier = dweller.isBarrier(envelope.message.asInstanceOf[Call])
            dweller.receiveNotice(envelope)
        }
    }

    private def dispatchBatchNotices(): Unit = {
        if (noticeCursor == null) noticeCursor = noticeMailbox.getAll
        val buf = ActorThread.threadBuffer[Notice]
        while (noticeCursor != null && !inBarrier) {
            val envelope = noticeCursor.asInstanceOf[Envelope]
            noticeCursor = envelope.next
            envelope.clearNext()
            val notice = envelope.message.asInstanceOf[Notice]
            if (dweller.batchNoticeFilter(notice)) {
                buf.addOne(notice)
                envelope.recycle()
            } else {
                if (buf.nonEmpty) handleBatchNotice(buf)
                inBarrier = dweller.isBarrier(envelope.message.asInstanceOf[Call])
                dweller.receiveNotice(envelope)
            }
        }
        if (buf.nonEmpty) handleBatchNotice(buf)
    }

    // =========================================================================
    // Events
    // =========================================================================

    private def dispatchEvents(): Unit = {
        var cursor = eventMailbox.getAll
        while (cursor != null) {
            val msg = cursor.asInstanceOf[Event]
            cursor = msg.next
            msg.clearNext()
            dweller.receiveEvent(msg)
        }
    }

    // =========================================================================
    // Channel inflight (ChannelsActor only)
    // =========================================================================

    private def dispatchChannels(): Unit = {
        pendingChannels.foreachEntity { channel =>
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
        dweller.receiveBatchNotice(notices)
    }

    private def handleBatchAsk(buf: mutable.ArrayBuffer[Envelope]): Unit = {
        val asks = buf.toSeq
        buf.clear()
        dweller.receiveBatchAsk(asks)
    }

    // =========================================================================
    // Deferred tasks
    // =========================================================================

    private def runLaterTasks(): Unit = {
        if (actorType == CHANNELS_ACTOR) {
            val tasks = manager.laterTasks
            while (tasks.nonEmpty) {
                val task = tasks.removeHead()
                task.run()
            }
        }
    }

}
