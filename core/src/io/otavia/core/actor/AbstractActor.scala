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
import io.otavia.core.cache.PerActorThreadObjectPool
import io.otavia.core.log4a.Logging
import io.otavia.core.message.*
import io.otavia.core.reactor.*
import io.otavia.core.stack.*
import io.otavia.core.timer.Timer
import io.otavia.core.util.TimerService

import scala.collection.immutable.Seq
import scala.concurrent.TimeoutException
import scala.language.unsafeNulls
import scala.reflect.ClassTag

private[core] abstract class AbstractActor[M <: Call] extends Actor[M] with ActorCoroutineRunner[M] with Logging {

    private var ctx: ActorContext = _

    /** reply message waiter */
    lazy private[core] val waiters: ReplyWaiters = new ReplyWaiters()

    /** [[Reply]] message waiter */
    private val replyFutures: ActorFutures = new ActorFutures()

    // current received msg
    private[core] var currentReceived: Call | Reply | Seq[Call] = _
    private[core] var currentReceivedType: MessageType          = ASK_TYPE
    private[core] var currentIsBatch: Boolean                   = false

    /** current stack frame for running ask or notice message */
    private[core] var currentFrame: StackFrame | Null = _

    private var currentStack: ActorStack = _

    /** user actor override this to control whether restart when occur exception */
    val noticeExceptionStrategy: ExceptionStrategy = ExceptionStrategy.Restart

    /** self address of this actor instance
     *
     *  @return
     *    self address
     */
    def self: Address[M] = context.address.asInstanceOf[Address[M]]

    override private[core] def setCtx(context: ActorContext): Unit = {
        ctx = context
        idAllocator.setActorId(context.actorId)
        idAllocator.setActorAddress(context.address)
    }

    /** When this actor send ask message to other actor, a [[ReplyFuture]] will attach to current stack
     *
     *  @param askId
     *    message id of ask message send to other actor
     *  @param future
     *    the reply message future for this ask message
     */
    private[core] def attachStack(askId: Long, future: ReplyFuture[? <: Reply]): Unit = {
        val promise = future.promise
        assert(promise.notInChain, "The ReplyFuture has been used, can't be use again!")
        promise.setStack(currentStack)
        promise.setAskId(askId)
        currentStack.addUncompletedPromise(promise)
        replyFutures.push(askId, promise)
    }

    /** Exception handler when this actor received notice message or resume notice stack frame
     *
     *  @param e
     *    exception
     */
    private[core] def handleNoticeException(e: Throwable): Unit = { // TODO: refactor
        val log =
            if (currentFrame != null)
                s"StackFrame with call message ${currentFrame.nn.call} failed at handle $currentReceived message"
            else s"failed at handle $currentReceived message"
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
        val stack = NoticeStack[M & Notice]() // generate a NoticeStack instance from object pool.
        stack.setCall(notice)
        currentStack = stack
        runNoticeStack()
    }

    private def runNoticeStack(): Unit = {
        val stack = currentStack.asInstanceOf[NoticeStack[M & Notice]]
        try {
            continueNotice(stack) match
                case Some(state) => stack.setState(state) // change the stack to next state.
                case None =>
                    assert(stack.isDone, "receiveNotice is return None but not call return method!")
                    stack.recycle() // NoticeStack not return value to other actor.
        } catch {
            case cause: Throwable =>
                recycleUncompletedPromise(stack)
                stack.setFailed()
                stack.recycle()
                handleNoticeException(cause)
        } finally {
            currentStack = null
        }
    }

    /** receive ask message by this method, the method will be call when this actor instance receive ask message
     *
     *  @param ask
     *    ask message receive by this actor instance
     */
    final private[core] def receiveAsk(ask: Ask[? <: Reply]): Unit = {
        val stack = AskStack[M & Ask[? <: Reply]]
        stack.setCall(ask)
        currentStack = stack
        runAskStack()
    }

    private def runAskStack(): Unit = {
        val askStack = currentStack.asInstanceOf[AskStack[M & Ask[? <: Reply]]]
        try {
            continueAsk(askStack) match
                case Some(state) => askStack.setState(state)
                case None =>
                    assert(askStack.isDone, "continueAsk is return None but not call return method!")
                    askStack.recycle()
        } catch {
            case cause: Throwable =>
                askStack.`throw`(ExceptionMessage(cause)) // completed stack with Exception
                recycleUncompletedPromise(askStack)
                askStack.recycle()
        } finally {
            currentStack = null
        }
    }

    private def recycleUncompletedPromise(stack: ActorStack): Unit = {
        val uncompleted = stack.uncompletedIterator()
        while (uncompleted.hasNext) {
            val promise = uncompleted.nextCast[ReplyPromise[?]]()
            replyFutures.remove(promise.askId)
            promise.recycle()
        }
    }

//    final private[core] def receiveReply(reply: Reply): Unit = {
//        setCurrentReply(reply)
//        if (!reply.isBatch) receiveReply0(reply, waiters.pop(reply.getReplyId))
//        else reply.getReplyIds.foreach { id => if (waiters.contains(id)) receiveReply0(reply, waiters.pop(id)) }
//    }

    /** receive reply message by this method, the method will be call when this actor instance receive reply message
     *
     *  @param reply
     *    reply message receive by this actor instance
     */
    final private[core] def receiveReply(reply: Reply): Unit = {
        if (!reply.isBatch) {
            // reply future maybe has been recycled cause by time-out, or it stack has been throw an error.
            if (replyFutures.contains(reply.replyId)) {
                val promise = replyFutures.pop(reply.replyId)
                if (promise.canTimeout) {
                    //
                    // TODO: system.timer.cancelTimerTask()
                }
                receiveReply0(reply, promise)
            } // drop reply otherwise
        }
    }

    private def receiveReply0(reply: Reply, promise: ReplyPromise[?]): Unit = {
        currentStack = promise.actorStack
        reply match
            case message: ExceptionMessage => promise.setFailure(message)
            case _                         => promise.setSuccess(reply)
        currentStack.addCompletedPromise(promise)
        if (currentStack.stackState.resumable()) resume()
    }

//    private def receiveReply0(reply: Reply, waiter: ReplyWaiter[?]): Unit = {
//        currentFrame = waiter.frame
//        reply match {
//            case message: ExceptionMessage =>
//                waiter match
//                    case exceptionWaiter: ExceptionWaiter[_] =>
//                        exceptionWaiter.receive(reply)
//                    case _ =>
//                        currentFrame.nn.setError()
//                        currentFrame match
//                            case askFrame: AskFrame => askFrame.ask.throws(ExceptionMessage(message))
//                            case _: NoticeFrame     => handleNoticeException(message)
//                            case _                  =>
//            case _ => waiter.receive(reply)
//        }
//        if (!currentFrame.nn.thrown && currentFrame.nn.state.resumable()) resume()
//    }

    /** resume running current stack frame to next state */
    private def resume(): Unit = {
        currentStack match
            case _: AskStack[?]    => runAskStack()
            case _: NoticeStack[?] => runNoticeStack()
            case _                 =>
    }

    final override private[core] def receiveEvent(event: Event): Unit = event match
        case askTimeoutEvent: AskTimeoutEvent => handleAskTimeoutEvent(askTimeoutEvent)
        case timeoutEvent: TimeoutEvent       => handleActorTimeout(timeoutEvent)
        case _                                => receiveIOEvent(event)

    private def handleAskTimeoutEvent(askTimeoutEvent: AskTimeoutEvent): Unit =
        if (replyFutures.contains(askTimeoutEvent.askId)) {
            val promise = replyFutures.pop(askTimeoutEvent.askId)
            promise.setFailure(new TimeoutException())
            val stack = promise.actorStack
            if (stack.stackState.resumable()) {
                currentStack = stack
                resume()
            }
        }

    /** handle user registered timeout event.
     *  @param timeoutEvent
     *    event
     */
    protected def handleActorTimeout(timeoutEvent: TimeoutEvent): Unit = {}

    /** Receive IO event from [[Reactor]] or timeout event from [[Timer]]
     *  @param event
     *    IO/timeout event
     */
    protected def receiveIOEvent(event: Event): Unit = {}

//    private def resume(): Unit = try {
//        val next = currentFrame match
//            case askFrame: AskFrame       => continueAsk(askFrame)
//            case noticeFrame: NoticeFrame => continueNotice(noticeFrame)
//            case _                        => None
//        next match
//            case Some(state) => currentFrame.nn.nextState(state)
//            case None =>
//                currentFrame match
//                    case askFrame: AskFrame => askFrame.ask.replyInternal(askFrame.reply)
//                    case _                  => ()
//    } catch {
//        case e: Exception =>
//            currentFrame.nn.setError()
//            currentFrame match
//                case askFrame: AskFrame => askFrame.ask.throws(ExceptionMessage(e))
//                case _: NoticeFrame     => handleNoticeException(e)
//                case _                  =>
//    }

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
                case Some(state) => currentFrame.nn.nextState(state)
                case None        =>
        } catch {
            case _: NotImplementedError => notices.foreach(notice => receiveNotice(notice.asInstanceOf[M & Notice]))
            case e: Exception =>
                if (currentFrame != null)
                    currentFrame.nn.setError() // mark this frame is errored to ignore reply message
                handleNoticeException(e)
        }
    }

    final private[core] def receiveBatchAsk(asks: Seq[Ask[?]]): Unit = {
        setCurrentAsks(asks)
        try {
            batchContinueAsk(asks.asInstanceOf[Seq[M & Ask[?]]]) match
                case Some(state) => currentFrame.nn.nextState(state)
                case None        =>
        } catch {
            case _: NotImplementedError => asks.foreach(notice => receiveAsk(notice.asInstanceOf[M & Ask[?]]))
            case e: Exception =>
                if (currentFrame != null)
                    currentFrame.nn.setError() // mark this frame is errored to ignore reply message
            //        ask.reply(ExceptionMessage(e))
        }
    }

    def resumeMerge(frames: Seq[StackFrame]): Seq[Option[StackState]] =
        throw new NotImplementedError(getClass.getName.nn + ": an implementation is missing")

}

private object AbstractActor {}
