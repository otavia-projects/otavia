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

package io.otavia.core.actor

import io.otavia.core.actor.Actor.{ASK_TYPE, MessageType, NOTICE_TYPE, REPLY_TYPE}
import io.otavia.core.address.{ActorAddress, Address}
import io.otavia.core.message.*
import io.otavia.core.stack.*
import io.otavia.core.util.Logging

import scala.reflect.ClassTag

abstract class NormalActor[M <: Ask[?] | Notice] extends Actor[M], Logging {

    /** self address of this actor instance
     *
     *  @return
     *    self address
     */
    override def self: ActorAddress[M] = super.self.asInstanceOf[ActorAddress[M]]

    /** reply message waiter */
    lazy private[core] val waiters: ReplyWaiters = new ReplyWaiters()

    // current received msg
    private[core] var currentReceived: Ask[?] | Notice | Reply | Seq[Ask[?]] | Seq[Notice] = _
    private[core] var currentReceivedType: MessageType                                     = ASK_TYPE
    private[core] var currentIsBatch: Boolean                                              = false

    /** current stack frame for running ask or notice message */
    private[core] var currentFrame: StackFrame = _

    /** user actor override this to control whether restart when occur exception */
    val noticeExceptionStrategy: ExceptionStrategy = ExceptionStrategy.Restart

    /** called by receiveXXX when schedule message running */
    private[core] def setCurrentAsk(ask: Ask[?]): Unit = {
        currentFrame = null
        currentReceived = ask
        currentReceivedType = ASK_TYPE
        currentIsBatch = false
    }

    /** called by receiveXXX when schedule message running */
    private[core] def setCurrentNotice(notice: Notice): Unit = {
        currentFrame = null
        currentReceived = notice
        currentReceivedType = NOTICE_TYPE
        currentIsBatch = false
    }

    /** called by receiveXXX when schedule message running */
    private[core] def setCurrentReply(reply: Reply): Unit = {
        currentFrame = null
        currentReceived = reply
        currentReceivedType = REPLY_TYPE
        currentIsBatch = false
    }

    inline private[core] def currentIsAsk: Boolean = currentReceivedType == ASK_TYPE

    inline private[core] def currentIsNotice: Boolean = currentReceivedType == NOTICE_TYPE

    inline private[core] def currentIsReply: Boolean = currentReceivedType == REPLY_TYPE

    /** get or create stack frame if it is not exists
     *
     *  @return
     *    current stack frame
     */
    private[core] def getOrCreateCurrentFrame(): StackFrame = if (currentIsReply) currentFrame
    else {
        currentFrame = currentReceived match
            case ask: Ask[?]                        => new AskFrame(ask)
            case notice: Notice                     => new NoticeFrame(notice)
            case asks: Seq[?] if currentIsAsk       => new AsksFrame(asks.asInstanceOf[Seq[Ask[?]]])
            case notices: Seq[?] if currentIsNotice => new NoticesFrame(notices.asInstanceOf[Seq[Notice]])
            case _                                  => throw new IllegalStateException("")
        currentFrame
    }

    /** when this actor send ask message to other actor, a [[ReplyWaiter]] will attach to current stack frame
     *
     *  @param ask
     *    ask message send to other actor
     *  @param waiter
     *    the reply message waiter for this ask message
     */
    private[core] def attachFrame(askId: Long, waiter: ReplyWaiter[_ <: Reply]): Unit = {
        waiter.setAskId(askId)
        waiter.setFrame(getOrCreateCurrentFrame())
        waiters.push(askId, waiter)
    }

    /** Exception handler when this actor received notice message or resume notice stack frame
     *
     *  @param e
     *    exception
     */
    private[core] def handleNoticeException(e: Throwable): Unit = {
        val log =
            if (currentFrame != null)
                s"StackFrame with call message ${currentFrame.call} failed at handle ${currentReceived} message"
            else s"failed at handle ${currentReceived} message"
        noticeExceptionStrategy match
            case ExceptionStrategy.Restart =>
                logError(log, e)
                try {
                    beforeRestart()
                    restart()
                    afterRestart()
                } catch {
                    case exception: Exception =>
                        logFatal("Fatal error on restart", exception)
                        system.shutdown()
                }
            case ExceptionStrategy.Ignore => logWarn(log, e)
            case ExceptionStrategy.ShutdownSystem =>
                logFatal(log, e)
                system.shutdown()
    }

    /** receive notice message by this method, the method will be call when this actor instance receive notice message
     *
     *  @param notice
     *    notice message receive by this actor instance
     */
    final private[core] def receiveNotice(notice: Notice): Unit = {
        setCurrentNotice(notice)
        try {
            continueNotice(notice.asInstanceOf[M & Notice]) match
                case Some(state) => currentFrame.nextState(state) // resume the frame by continueNotice
                case None        =>
        } catch {
            case e: Exception =>
                if (currentFrame != null) currentFrame.setError() // mark this frame is errored to ignore reply message
                handleNoticeException(e)
        }
    }

    /** receive ask message by this method, the method will be call when this actor instance receive ask message
     *
     *  @param ask
     *    ask message receive by this actor instance
     */
    final private[core] def receiveAsk(ask: Ask[?]): Unit = {
        setCurrentAsk(ask)
        try {
            continueAsk(ask.asInstanceOf[M & Ask[?]]) match
                case Some(state) => currentFrame.nextState(state) // resume the frame by continueAsk
                case None        =>                               // user code directly return reply message to sender
        } catch {
            case e: Exception =>
                if (currentFrame != null) currentFrame.setError() // mark this frame is errored to ignore reply message
                ask.throws(ExceptionMessage(e))
        }
    }

    /** receive reply message by this method, the method will be call when this actor instance receive reply message
     *
     *  @param reply
     *    reply message receive by this actor instance
     */
    final private[core] def receiveReply(reply: Reply): Unit = {
        setCurrentReply(reply)
        if (!reply.isBatch) receiveReply0(reply, waiters.pop(reply.getReplyId))
        else reply.getReplyIds.foreach { id => if (waiters.contains(id)) receiveReply0(reply, waiters.pop(id)) }
    }

    private def receiveReply0(reply: Reply, waiter: ReplyWaiter[?]): Unit = {
        currentFrame = waiter.frame
        reply match {
            case message: ExceptionMessage =>
                waiter match
                    case exceptionWaiter: ExceptionWaiter[_] =>
                        exceptionWaiter.receive(reply)
                    case _ =>
                        currentFrame.setError()
                        currentFrame match
                            case askFrame: AskFrame => askFrame.ask.throws(ExceptionMessage(message.cause))
                            case _: NoticeFrame     => handleNoticeException(message.cause)
                            case _                  =>
            case _ => waiter.receive(reply)
        }
        if (!currentFrame.thrown && currentFrame.state.resumable()) continue()
    }

    /** continue running current stack frame to next state */
    final private[core] def continue(): Unit = try {
        val next = currentFrame match
            case askFrame: AskFrame       => continueAsk(askFrame)
            case noticeFrame: NoticeFrame => continueNotice(noticeFrame)
            case _                        => None
        next match
            case Some(state) => currentFrame.nextState(state)
            case None =>
                currentFrame match
                    case askFrame: AskFrame => askFrame.ask.replyInternal(askFrame.reply)
                    case _                  => ()
    } catch {
        case e: Exception =>
            currentFrame.setError()
            currentFrame match
                case askFrame: AskFrame => askFrame.ask.throws(ExceptionMessage(e))
                case _: NoticeFrame     => handleNoticeException(e)
                case _                  =>
    }

    /** max size message for each batch, usage for schedule system */
    def maxBatchSize: Int = system.defaultMaxBatchSize

    /** called by receiveBatchXXX when schedule message running */
    private[core] def setCurrentAsks(asks: Seq[Ask[?]]): Unit = {
        currentFrame = null
        currentReceived = asks
        currentReceivedType = ASK_TYPE
        currentIsBatch = true
    }

    /** called by receiveBatchXXX when schedule message running */
    private[core] def setCurrentNotices(notices: Seq[Notice]): Unit = {
        currentFrame = null
        currentReceived = notices
        currentReceivedType = NOTICE_TYPE
        currentIsBatch = true
    }

    final private[core] def receiveBatchNotice(notices: Seq[Notice]): Unit = {
        setCurrentNotices(notices)
        try {
            batchContinueNotice(notices.asInstanceOf[Seq[M & Notice]]) match
                case Some(state) => currentFrame.nextState(state)
                case None        =>
        } catch {
            case _: NotImplementedError => notices.foreach(notice => receiveNotice(notice.asInstanceOf[M & Notice]))
            case e: Exception =>
                if (currentFrame != null) currentFrame.setError() // mark this frame is errored to ignore reply message
                handleNoticeException(e)
        }
    }

    final private[core] def receiveBatchAsk(asks: Seq[Ask[?]]): Unit = {
        setCurrentAsks(asks)
        try {
            batchContinueAsk(asks.asInstanceOf[Seq[M & Ask[?]]]) match
                case Some(state) => currentFrame.nextState(state)
                case None        =>
        } catch {
            case _: NotImplementedError => asks.foreach(notice => receiveAsk(notice.asInstanceOf[M & Ask[?]]))
            case e: Exception =>
                if (currentFrame != null) currentFrame.setError() // mark this frame is errored to ignore reply message
            //        ask.reply(ExceptionMessage(e))
        }
    }

    def batchContinueNotice(notices: Seq[M & Notice]): Option[StackState] =
        throw new NotImplementedError(getClass.getName + ": an implementation is missing")

    def batchContinueAsk(asks: Seq[M & Ask[?]]): Option[StackState] =
        throw new NotImplementedError(getClass.getName + ": an implementation is missing")

    /** implement this method to handle ask message and resume when received reply message for this notice message
     *
     *  @param state
     *    ask message received by this actor instance, or resume frame .
     *  @return
     *    an option value containing the resumable [[StackState]] waited for some reply message, or `None` if the stack
     *    frame has finished.
     */
    def continueAsk(state: M & Ask[?] | AskFrame): Option[StackState] =
        throw new NotImplementedError(getClass.getName + ": an implementation is missing")

    /** implement this method to handle notice message and resume when received reply message for this notice message
     *
     *  @param state
     *    notice message receive by this actor instance, or resume frame .
     *  @return
     *    an option value containing the resumable [[StackState]] waited for some reply message, or `None` if the stack
     *    frame has finished.
     */
    def continueNotice(state: M & Notice | NoticeFrame): Option[StackState] =
        throw new NotImplementedError(getClass.getName + ": an implementation is missing")

    def resumeMerge(frames: Seq[StackFrame]): Seq[Option[StackState]] =
        throw new NotImplementedError(getClass.getName + ": an implementation is missing")

}
