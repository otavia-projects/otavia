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
import io.otavia.core.message.*
import io.otavia.core.reactor.*
import io.otavia.core.slf4a.Logger
import io.otavia.core.stack.*
import io.otavia.core.timer.Timer

import scala.collection.immutable.Seq
import scala.concurrent.TimeoutException
import scala.language.unsafeNulls
import scala.reflect.ClassTag

private[core] abstract class AbstractActor[M <: Call]
    extends FutureDispatcher
    with Actor[M]
    with ActorCoroutineRunner[M] {

    protected var logger: Logger = _

    private var ctx: ActorContext = _

    // current received msg
    private var currentReceived: Call | Reply | Seq[Call] = _

    private[core] var currentIsBatch: Boolean = false

    /** current stack frame for running ask or notice message */
    private[core] var currentStack: Stack = _

    private var revAsks: Long  = 0
    private var sendAsks: Long = 0

    final override private[core] def stackEndRate: Float =
        if (revAsks != 0) sendAsks.toFloat / revAsks.toFloat else Float.MaxValue

    /** self address of this actor instance
     *
     *  @return
     *    self address
     */
    def self: Address[M] = context.address.asInstanceOf[Address[M]]

    final override private[core] def setCtx(context: ActorContext): Unit = {
        ctx = context
        idAllocator.setActorId(context.actorId)
        idAllocator.setActorAddress(context.address)
        logger = Logger.getLogger(getClass, ctx.system)
    }

    private[core] final def mount(): Unit = try {
        this.afterMount()
    } catch {
        case t: Throwable => logger.error("afterMount error with", t)
    }

    final override def context: ActorContext = ctx

    /** When this actor send ask message to other actor, a [[ReplyFuture]] will attach to current stack
     *
     *  @param askId
     *    message id of ask message send to other actor
     *  @param future
     *    the reply message future for this ask message
     */
    private[core] def attachStack(askId: Long, future: Future[?]): Unit = {
        future.promise match
            case promise: ReplyPromise[? <: Reply] =>
                sendAsks += 1
                assert(promise.notInChain, "The ReplyFuture has been used, can't be use again!")
                promise.setStack(currentStack)
                promise.setId(askId)
                currentStack.addUncompletedPromise(promise)
                this.push(promise)
            case promise: TimeoutEventPromise =>
                assert(promise.notInChain, "The TimeoutEventPromise has been used, can't be use again!")
                promise.setStack(currentStack)
                currentStack.addUncompletedPromise(promise)
                this.push(promise)
            case promise: ChannelPromise =>
                assert(promise.notInChain, "The ChannelPromise has been used, can't be use again!")
                promise.setStack(currentStack)
                promise.setId(askId)
                currentStack.addUncompletedPromise(promise)
                this.push(promise)
            case promise: AioPromise[?]       =>
            case promise: BlockPromise[?]     =>
            case promise: ChannelReplyPromise =>
            case promise: DefaultPromise[?]   =>
            case _                            =>
    }

    def receiveFuture(future: Future[?]): Unit = {
        future match
            case promise: DefaultPromise[?] =>
//                val stack = promise.actorStack
//                stack.addCompletedPromise(promise)
//                currentStack = stack
//                if (stack.stackState.resumable() || !stack.hasUncompletedPromise) resume()
            case promise: ChannelPromise =>
                ???
            case promise: TimeoutEventPromise =>
                ???
    }

    /** Exception handler when this actor received notice message or resume notice stack frame
     *
     *  @param e
     *    exception
     */
    private[core] def handleNoticeException(stack: Stack, e: Throwable): Unit = { // TODO: refactor

        val log = stack match
            case s: ActorStack       => s"Stack with call message ${s.call} failed at handle $currentReceived message"
            case s: BatchAskStack[?] => s"Stack with call message ${s.asks} failed at handle $currentReceived message"
            case s: BatchNoticeStack[?] =>
                s"Stack with call message ${s.notices} failed at handle $currentReceived message"
            case s: ChannelStack[?] => ""
            case _                  => ""
        noticeExceptionStrategy match
            case ExceptionStrategy.Restart =>
                logger.error(log, e)
                try {
                    beforeRestart()
                    restart()
                    afterRestart()
                } catch {
                    case exception: Exception =>
                        logger.error("Fatal error on restart", exception)
                        system.shutdown()
                }
            case ExceptionStrategy.Ignore => logger.error(log, e)
            case ExceptionStrategy.ShutdownSystem =>
                logger.error(log, e)
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
                cause.printStackTrace()
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
        revAsks += 1
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
                cause.printStackTrace()
                askStack.`throw`(ExceptionMessage(cause)) // completed stack with Exception
                recycleUncompletedPromise(askStack.uncompletedIterator())
                askStack.recycle()
        } finally {
            currentStack = null
            currentReceived = null
        }
    }

    final override private[core] def receiveBatchAsk(asks: Seq[Ask[?]]): Unit = {
        currentReceived = asks
        val stack = BatchAskStack[M & Ask[?]]()
        stack.setAsks(asks)
        currentStack = stack
        runBatchAskStack()
    }

    final private def runBatchAskStack(): Unit = {
        val stack = currentStack.asInstanceOf[BatchAskStack[M & Ask[? <: Reply]]]
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
            this.pop(promise.id)
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
            if (this.contains(reply.replyId)) {
                val promise = this.pop(reply.replyId).asInstanceOf[ReplyPromise[?]]
                if (promise.canTimeout) system.timer.cancelTimerTask(promise.timeoutId)
                receiveReply0(reply, promise)
            }    // drop reply otherwise
        } else { // reply is batch
            reply.replyIds.foreach { (senderId, rid) =>
                if (contains(rid) && senderId == actorId) {
                    val promise = pop(rid).asInstanceOf[ReplyPromise[?]]
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

    private def handleAskTimeoutEvent(askTimeoutEvent: AskTimeoutEvent): Unit = {
        pop(askTimeoutEvent.askId) match
            case promise: TimeoutEventPromise =>
                promise.setSuccess(askTimeoutEvent)
                val stack = promise.actorStack
                stack.addCompletedPromise(promise)
                if (stack.stackState.resumable() || !stack.hasUncompletedPromise) {
                    currentStack = stack
                    resume()
                }
            case promise: ReplyPromise[?] =>
                promise.setFailure(new TimeoutException())
                val stack = promise.actorStack
                stack.addCompletedPromise(promise)
                if (stack.stackState.resumable() || !stack.hasUncompletedPromise) {
                    currentStack = stack
                    resume()
                }
            case _ =>
    }

    /** Call by [[io.otavia.core.system.ActorHousePhantomRef]] to release [[Actor]] resource. */
    private[core] def stop(): Unit = {
        this match
            case beforeStop: BeforeStop =>
                try {
                    beforeStop.beforeStop()
                } catch {
                    case t: Throwable => logger.error("Error at beforeStop with ", t)
                }
            case _ =>
    }

}

private object AbstractActor {}
