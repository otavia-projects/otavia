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

package cc.otavia.core.actor

import cc.otavia.core.actor.Actor.{ASK_TYPE, MessageType, NOTICE_TYPE, REPLY_TYPE}
import cc.otavia.core.address.{ActorAddress, Address}
import cc.otavia.core.cache.PerActorThreadObjectPool
import cc.otavia.core.message.*
import cc.otavia.core.reactor.*
import cc.otavia.core.slf4a.Logger
import cc.otavia.core.stack.*
import cc.otavia.core.system.ActorSystem
import cc.otavia.core.timer.Timer

import scala.collection.immutable.Seq
import scala.concurrent.TimeoutException
import scala.language.unsafeNulls
import scala.reflect.ClassTag

private[core] abstract class AbstractActor[M <: Call] extends FutureDispatcher with Actor[M] {

    protected var logger: Logger = _

    private var ctx: ActorContext = _

    // current received msg
    private[core] var currentReceived: Call | Reply | Seq[Call] | AnyRef = _

    private[core] var currentIsBatch: Boolean = false

    /** current stack frame for running ask or notice message */
    private[core] var currentStack: Stack = _

    private var revAsks: Long  = 0
    private var sendAsks: Long = 0

    private var currentSendMessageId: Long = Long.MinValue

    final private[core] def stackEndRate: Float =
        if (revAsks != 0) sendAsks.toFloat / revAsks.toFloat else Float.MaxValue

    private[core] def generateSendMessageId(): Long = {
        val id = currentSendMessageId
        currentSendMessageId += 1
        id
    }

    /** implement this method to handle ask message and resume when received reply message for this notice message
     *
     *  @param stack
     *    ask message received by this actor instance, or resume frame .
     *  @return
     *    an option value containing the resumable [[StackState]] waited for some reply message, or `None` if the stack
     *    frame has finished.
     */
    protected def continueAsk(stack: AskStack[M & Ask[? <: Reply]]): Option[StackState] =
        throw new NotImplementedError(getClass.getName + ": an implementation is missing")

    /** implement this method to handle notice message and resume when received reply message for this notice message
     *
     *  @param stack
     *    notice message receive by this actor instance, or resume frame .
     *  @return
     *    an option value containing the resumable [[StackState]] waited for some reply message, or `None` if the stack
     *    frame has finished.
     */
    protected def continueNotice(stack: NoticeStack[M & Notice]): Option[StackState] =
        throw new NotImplementedError(getClass.getName + ": an implementation is missing")

    protected def continueChannel(stack: ChannelStack[AnyRef]): Option[StackState] =
        throw new NotImplementedError(getClass.getName + ": an implementation is missing")

    protected def batchContinueNotice(stack: BatchNoticeStack[M & Notice]): Option[StackState] =
        throw new NotImplementedError(getClass.getName + ": an implementation is missing")

    protected def batchContinueAsk(stack: BatchAskStack[M & Ask[? <: Reply]]): Option[StackState] =
        throw new NotImplementedError(getClass.getName + ": an implementation is missing")

    /** handle user registered timeout event.
     *
     *  @param timeoutEvent
     *    event
     */
    protected def handleActorTimeout(timeoutEvent: TimeoutEvent): Unit = {}

    /** self address of this actor instance
     *
     *  @return
     *    self address
     */
    def self: Address[M] = context.address.asInstanceOf[Address[M]]

    def batchNoticeFilter: M & Notice => Boolean = _ => true

    def batchAskFilter: M & Ask[?] => Boolean = _ => true

    def noticeBarrier: M & Notice => Boolean = _ => false

    def askBarrier: M & Ask[?] => Boolean = _ => false

    /** This method will called by [[ActorSystem]] when actor mount to actor system, when a actor is creating, the
     *  [[ActorSystem]] will create a [[ActorContext]]. When mount actor instance to actor system, use this method to
     *  set system context information.
     *
     *  @param context
     *    the system context of this actor
     */
    final private[core] def setCtx(context: ActorContext): Unit = {
        ctx = context
        logger = Logger.getLogger(getClass, ctx.system)
    }

    private[core] final def mount(): Unit = try {
        this.afterMount()
    } catch {
        case t: Throwable => logger.error("afterMount error with", t)
    }

    final override def context: ActorContext = ctx

    /** When this actor send ask message to other actor, a [[Future]] will attach to current stack
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
            case promise: ChannelReplyPromise =>
                assert(promise.notInChain, "The ChannelReplyPromise has been used, can't be use again!")
                promise.setStack(currentStack)
                promise.setId(askId)
                currentStack.addUncompletedPromise(promise)
            case _ =>
    }

    def receiveFuture(future: Future[?]): Unit = {
        val promise = future.promise.asInstanceOf[AbstractPromise[?]]
        val stack   = future.promise.actorStack
        stack.addCompletedPromise(promise)
        if (stack.state.resumable() || !stack.hasUncompletedPromise) {
            currentStack = stack
            resume()
        }
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
            val uncompleted = stack.uncompletedPromises()
            val oldState    = stack.state
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
                recycleUncompletedPromise(stack.uncompletedPromises())
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
            val uncompleted = stack.uncompletedPromises()
            val oldState    = stack.state
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
                recycleUncompletedPromise(stack.uncompletedPromises())
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
        val stack = AskStack[M & Ask[? <: Reply]](this)
        stack.setCall(ask)
        currentStack = stack
        runAskStack()
    }

    private def runAskStack(): Unit = {
        val askStack = currentStack.asInstanceOf[AskStack[M & Ask[? <: Reply]]]
        try {
            val uncompleted = askStack.uncompletedPromises()
            val oldState    = askStack.state
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
                    recycleUncompletedPromise(askStack.uncompletedPromises())
                    assert(askStack.isDone, "continueAsk is return None but not call return method!")
                    askStack.recycle()
        } catch {
            case cause: Throwable =>
                cause.printStackTrace()
                askStack.`throw`(ExceptionMessage(cause)) // completed stack with Exception
                recycleUncompletedPromise(askStack.uncompletedPromises())
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
            val uncompleted = stack.uncompletedPromises()
            val oldState    = stack.state
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
                recycleUncompletedPromise(stack.uncompletedPromises())
                stack.recycle()
        } finally {
            currentStack = null
            currentReceived = null
        }
    }

    private[core] def recycleUncompletedPromise(uncompleted: PromiseIterator): Unit = { // TODO: ChannelPromise
        while (uncompleted.hasNext) {
            val promise = uncompleted.next()
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
        if (currentStack.state.resumable() || !currentStack.hasUncompletedPromise) resume()
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
        case _                                => receiveReactorEvent(event)

    private def handleAskTimeoutEvent(askTimeoutEvent: AskTimeoutEvent): Unit = {
        pop(askTimeoutEvent.askId) match
            case promise: TimeoutEventPromise =>
                promise.setSuccess(askTimeoutEvent)
                val stack = promise.actorStack
                stack.addCompletedPromise(promise)
                if (stack.state.resumable() || !stack.hasUncompletedPromise) {
                    currentStack = stack
                    resume()
                }
            case promise: ReplyPromise[?] =>
                promise.setFailure(new TimeoutException())
                val stack = promise.actorStack
                stack.addCompletedPromise(promise)
                if (stack.state.resumable() || !stack.hasUncompletedPromise) {
                    currentStack = stack
                    resume()
                }
            case _ =>
    }

    /** Receive IO event from [[Reactor]] or timeout event from [[Timer]]
     *
     *  @param event
     *    IO/timeout event
     */
    protected def receiveReactorEvent(event: Event): Unit = {}

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

}

private object AbstractActor {}
