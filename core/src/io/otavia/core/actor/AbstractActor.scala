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

import scala.collection.immutable.Seq
import scala.concurrent.TimeoutException
import scala.language.unsafeNulls
import scala.reflect.ClassTag

private[core] abstract class AbstractActor[M <: Call] extends Actor[M] with ActorCoroutineRunner[M] with Logging {

    private var ctx: ActorContext = _

    /** [[Reply]] message waiter */
    private val replyFutures: ActorFutures = new ActorFutures()

    // current received msg
    private var currentReceived: Call | Reply | Seq[Call] = _

    private[core] var currentIsBatch: Boolean = false

    /** current stack frame for running ask or notice message */
    private[core] var currentStack: Stack = _

    /** self address of this actor instance
     *
     *  @return
     *    self address
     */
    def self: Address[M] = context.address.asInstanceOf[Address[M]]

    final override private[core] def setCtx(context: ActorContext): Unit = {
        afterCreate()
        ctx = context
        idAllocator.setActorId(context.actorId)
        idAllocator.setActorAddress(context.address)
        afterMount()
    }

    final override def context: ActorContext = ctx

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
    private[core] def handleNoticeException(stack: Stack, e: Throwable): Unit = { // TODO: refactor

        val log = stack match
            case s: ActorStack       => s"Stack with call message ${s.call} failed at handle $currentReceived message"
            case s: BatchAskStack[_] => s"Stack with call message ${s.asks} failed at handle $currentReceived message"
            case s: BatchNoticeStack[_] =>
                s"Stack with call message ${s.notices} failed at handle $currentReceived message"
            case s: ChannelStack[_] => ""
            case _                  => ""
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
            case ExceptionStrategy.Ignore => logError(log, e)
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
        currentReceived = notice
        val stack = NoticeStack[M & Notice]() // generate a NoticeStack instance from object pool.
        stack.setCall(notice)
        currentStack = stack
        runNoticeStack()
    }

    private def runNoticeStack(): Unit = {
        val stack = currentStack.asInstanceOf[NoticeStack[M & Notice]]
        try {
            val uncompleted = stack.uncompletedIterator()
            val oldState    = stack.stackState
            continueNotice(stack) match
                case Some(state) =>
                    if (state != oldState) {
                        stack.setState(state) // change the stack to next state.
                        if (uncompleted.hasNext) recycleUncompletedPromise(uncompleted)
                    } else { // state == oldState, recover uncompleted promise
                        if (uncompleted.hasNext) stack.addUncompletedPromiseIterator(uncompleted)
                    }
                    assert(stack.hasUncompletedPromise, s"has no future to wait for $stack")
                case None =>
                    if (uncompleted.hasNext) recycleUncompletedPromise(uncompleted)
                    assert(stack.isDone, "receiveNotice is return None but not call return method!")
                    stack.recycle() // NoticeStack not return value to other actor.
        } catch {
            case cause: Throwable =>
                recycleUncompletedPromise(stack.uncompletedIterator())
                stack.setFailed()
                handleNoticeException(stack, cause)
                stack.recycle()
        } finally {
            currentStack = null
            currentReceived = null
        }
    }

    final override private[core] def receiveBatchNotice(notices: Seq[Notice]): Unit = {
        currentReceived = notices
        val stack = BatchNoticeStack[M & Notice]() // generate a BatchNoticeStack instance from object pool.
        currentStack = stack
        stack.setNotices(notices)
        runBatchNoticeStack()
    }

    private def runBatchNoticeStack(): Unit = {
        val stack = currentStack.asInstanceOf[BatchNoticeStack[M & Notice]]
        try {
            val uncompleted = stack.uncompletedIterator()
            val oldState    = stack.stackState
            batchContinueNotice(stack) match // change the stack to next state.
                case Some(state) =>
                    if (state != oldState) {
                        stack.setState(state) // change the stack to next state.
                        if (uncompleted.hasNext) recycleUncompletedPromise(uncompleted)
                    } else { // state == oldState, recover uncompleted promise
                        if (uncompleted.hasNext) stack.addUncompletedPromiseIterator(uncompleted)
                    }
                    assert(stack.hasUncompletedPromise, s"has no future to wait for $stack")
                case None =>
                    if (uncompleted.hasNext) recycleUncompletedPromise(uncompleted)
                    assert(stack.isDone, "receiveNotice is return None but not call return method!")
                    stack.recycle() // NoticeStack not return value to other actor.
        } catch {
            case _: NotImplementedError => // receive message one by one.
                val notices = stack.notices
                currentStack = null
                stack.recycle()
                notices.foreach { notice => receiveNotice(notice) }
            case cause: Throwable =>
                recycleUncompletedPromise(stack.uncompletedIterator())
                stack.setFailed()
                handleNoticeException(stack, cause)
                stack.recycle()
        } finally {
            currentStack = null
            currentReceived = null
        }
    }

    /** receive ask message by this method, the method will be call when this actor instance receive ask message
     *
     *  @param ask
     *    ask message received by this actor instance
     */
    final private[core] def receiveAsk(ask: Ask[? <: Reply]): Unit = {
        currentReceived = ask
        val stack = AskStack[M & Ask[? <: Reply]]
        stack.setCall(ask)
        currentStack = stack
        runAskStack()
    }

    private def runAskStack(): Unit = {
        val askStack = currentStack.asInstanceOf[AskStack[M & Ask[? <: Reply]]]
        try {
            val uncompleted = askStack.uncompletedIterator()
            val oldState    = askStack.stackState
            continueAsk(askStack) match // run stack and switch to next state
                case Some(state) =>
                    if (state != oldState) {
                        askStack.setState(state) // this also recycled all completed promise
                        if (uncompleted.hasNext) recycleUncompletedPromise(uncompleted)
                    } else { // state == oldState, recover uncompleted promise
                        if (uncompleted.hasNext) askStack.addUncompletedPromiseIterator(uncompleted)
                    }
                    assert(askStack.hasUncompletedPromise, s"has no future to wait for $askStack")
                case None =>
                    if (uncompleted.hasNext) recycleUncompletedPromise(uncompleted)
                    recycleUncompletedPromise(askStack.uncompletedIterator())
                    assert(askStack.isDone, "continueAsk is return None but not call return method!")
                    askStack.recycle()
        } catch {
            case cause: Throwable =>
                askStack.`throw`(ExceptionMessage(cause)) // completed stack with Exception
                recycleUncompletedPromise(askStack.uncompletedIterator())
                askStack.recycle()
        } finally {
            currentStack = null
            currentReceived = null
        }
    }

    final override private[core] def receiveBatchAsk(asks: Seq[Ask[_]]): Unit = {
        currentReceived = asks
        val stack = BatchAskStack[M & Ask[?]]()
        stack.setAsks(asks)
        currentStack = stack
        runBatchAskStack()
    }

    final private def runBatchAskStack(): Unit = {
        val stack = currentStack.asInstanceOf[BatchAskStack[M & Ask[?]]]
        try {
            val uncompleted = stack.uncompletedIterator()
            val oldState    = stack.stackState
            batchContinueAsk(stack) match
                case Some(state) =>
                    if (state != oldState) {
                        stack.setState(state) // this also recycled all completed promise
                        if (uncompleted.hasNext) recycleUncompletedPromise(uncompleted)
                    } else { // state == oldState, recover uncompleted promise
                        if (uncompleted.hasNext) stack.addUncompletedPromiseIterator(uncompleted)
                    }
                    assert(stack.hasUncompletedPromise, s"has no future to wait for $stack")
                case None =>
                    if (uncompleted.hasNext) recycleUncompletedPromise(uncompleted)
                    assert(stack.isDone, "continueAsk is return None but not call return method!")
                    stack.recycle()
        } catch {
            case _: NotImplementedError =>
                val asks = stack.asks
                currentStack = null
                stack.recycle()
                asks.foreach { ask => receiveAsk(ask) }
            case cause: Throwable =>
                stack.`throw`(ExceptionMessage(cause)) // completed stack with Exception
                recycleUncompletedPromise(stack.uncompletedIterator())
                stack.recycle()
        } finally {
            currentStack = null
            currentReceived = null
        }
    }

    private def recycleUncompletedPromise(uncompleted: Stack.UncompletedPromiseIterator): Unit = { // TODO: ChannelPromise
        while (uncompleted.hasNext) {
            val promise = uncompleted.nextCast[ReplyPromise[?]]()
            replyFutures.remove(promise.askId)
            promise.recycle()
        }
    }

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
                if (promise.canTimeout) system.timer.cancelTimerTask(promise.timeoutId)
                receiveReply0(reply, promise)
            }    // drop reply otherwise
        } else { // reply is batch
            reply.replyIds.foreach { (senderId, rid) =>
                if (replyFutures.contains(rid) && senderId == actorId) {
                    val promise = replyFutures.pop(rid)
                    if (promise.canTimeout) system.timer.cancelTimerTask(promise.timeoutId)
                    receiveReply0(reply, promise)
                }
            }
        }
    }

    private def receiveReply0(reply: Reply, promise: ReplyPromise[?]): Unit = {
        currentReceived = reply
        currentStack = promise.actorStack
        reply match
            case message: ExceptionMessage => promise.setFailure(message)
            case _                         => promise.setSuccess(reply)
        currentStack.addCompletedPromise(promise)
        if (currentStack.stackState.resumable() || !currentStack.hasUncompletedPromise) resume()
    }

    /** resume running current stack frame to next state */
    private[core] def resume(): Unit = {
        currentStack match
            case _: AskStack[?]         => runAskStack()
            case _: NoticeStack[?]      => runNoticeStack()
            case _: BatchAskStack[?]    => runBatchAskStack()
            case _: BatchNoticeStack[?] => runBatchNoticeStack()
            case _                      =>
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
            stack.addCompletedPromise(promise)
            if (stack.stackState.resumable() || !currentStack.hasUncompletedPromise) {
                currentStack = stack
                resume()
            }
        }

}

private object AbstractActor {}
