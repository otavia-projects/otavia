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
import cc.otavia.core.cache.Poolable
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

    import AbstractActor.*

    protected var logger: Logger = _

    private var ctx: ActorContext = _

    // current received msg
    private[core] var currentReceived: Call | Reply | Seq[Call] | AnyRef = _

    /** current stack frame for running ask or notice message */
    private[core] var currentStack: Stack = _

    private var revAsks: Long  = 0
    private var sendAsks: Long = 0

    private var currentSendMessageId: Long = Long.MinValue

    final private[core] def stackEndRate: Float =
        if (revAsks != 0) sendAsks.toFloat / revAsks.toFloat else Float.MaxValue

    /** Generate a unique id for send [[Ask]] message. */
    private[core] def generateSendMessageId(): Long = {
        val id = currentSendMessageId
        currentSendMessageId += 1
        id
    }

    /** self address of this actor instance */
    def self: Address[M] = context.address.asInstanceOf[Address[M]]

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

    final override def context: ActorContext = ctx

    final private[core] def mount(): Unit = try {
        this.afterMount()
    } catch {
        case t: Throwable => logger.error("afterMount error with", t)
    }

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
                promise.setId(askId)
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

    final private[core] def receiveFuture(future: Future[?]): Unit = {
        val promise = future.promise.asInstanceOf[AbstractPromise[?]]
        val stack   = future.promise.actorStack
        handlePromiseCompleted(stack, promise)
    }

    final override private[core] def receiveNotice(notice: Notice): Unit = {
        currentReceived = notice
        val stack = NoticeStack[M & Notice](this) // generate a NoticeStack instance from object pool.
        stack.setNotice(notice)
        dispatchNoticeStack(stack)
        currentReceived = null
    }

    final override private[core] def receiveBatchNotice(notices: Seq[Notice]): Unit = {
        currentReceived = notices
        val stack = BatchNoticeStack[M & Notice](this) // generate a BatchNoticeStack instance from object pool.
        stack.setNotices(notices)
        dispatchBatchNoticeStack(stack)
        currentReceived = null
    }

    final override private[core] def receiveAsk(ask: Ask[? <: Reply]): Unit = {
        revAsks += 1
        currentReceived = ask
        val stack = AskStack[M & Ask[? <: Reply]](this) // generate a AskStack instance from object pool.
        stack.setAsk(ask)
        dispatchAskStack(stack)
        currentReceived = null
    }

    final override private[core] def receiveBatchAsk(asks: Seq[Ask[?]]): Unit = {
        currentReceived = asks
        val stack = BatchAskStack[M & Ask[?]](this) // generate a BatchAskStack instance from object pool.
        stack.setAsks(asks)
        dispatchBatchAskStack(stack)
        currentReceived = null
    }

    final override private[core] def receiveReply(reply: Reply): Unit = {
        if (!reply.isBatch) {
            // reply future maybe has been recycled cause by time-out, or it stack has been throw an error.
            if (this.contains(reply.replyId)) {
                val promise = this.pop(reply.replyId).asInstanceOf[ReplyPromise[?]]
                if (promise.canTimeout) system.timer.cancelTimerTask(promise.timeoutId)
                receiveReply0(reply, promise)
            }    // drop reply otherwise
        } else { // reply is batch
            for ((senderId, rid) <- reply.replyIds if senderId == actorId && contains(rid)) {
                val promise = pop(rid).asInstanceOf[ReplyPromise[?]]
                if (promise.canTimeout) system.timer.cancelTimerTask(promise.timeoutId)
                receiveReply0(reply, promise)
            }
        }
    }

    private def receiveReply0(reply: Reply, promise: ReplyPromise[?]): Unit = {
        currentReceived = reply
        reply match
            case message: ExceptionMessage => promise.setFailure(message)
            case _                         => promise.setSuccess(reply)
        val stack = promise.actorStack
        handlePromiseCompleted(stack, promise)
        currentReceived = null
    }

    final override private[core] def receiveEvent(event: Event): Unit = event match {
        case event: AskTimeoutEvent => dispatchAskTimeoutEvent(event)
        case event: TimeoutEvent    => handleActorTimeout(event)
        case _                      => receiveReactorEvent(event)
    }

    private def dispatchNoticeStack(stack: NoticeStack[M & Notice]): Unit = {
        currentStack = stack
        try {
            val uncompleted = stack.uncompletedPromises()
            val oldState    = stack.state
            continueNotice(stack) match
                case Some(state) =>
                    if (state != oldState) {
                        stack.setState(state) // change the stack to next state.
                        if (uncompleted.hasNext) recycleUncompletedPromise(uncompleted)
                        this.recycleStackState(oldState)
                    } else { // state == oldState, recover uncompleted promise
                        if (uncompleted.hasNext) stack.addUncompletedPromiseIterator(uncompleted)
                    }
                    assert(stack.hasUncompletedPromise, s"has no future to wait for $stack")
                case None =>
                    if (uncompleted.hasNext) recycleUncompletedPromise(uncompleted)
                    this.recycleStackState(oldState)
                    assert(stack.isDone, "continueNotice is return None but not call return method of NoticeStack!")
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
        }
    }

    private def dispatchBatchNoticeStack(stack: BatchNoticeStack[M & Notice]): Unit = {
        currentStack = stack
        try {
            val uncompleted = stack.uncompletedPromises()
            val oldState    = stack.state
            batchContinueNotice(stack) match // change the stack to next state.
                case Some(state) =>
                    if (state != oldState) {
                        stack.setState(state) // change the stack to next state.
                        if (uncompleted.hasNext) recycleUncompletedPromise(uncompleted)
                        this.recycleStackState(oldState)
                    } else { // state == oldState, recover uncompleted promise
                        if (uncompleted.hasNext) stack.addUncompletedPromiseIterator(uncompleted)
                    }
                    assert(stack.hasUncompletedPromise, s"has no future to wait for $stack")
                case None =>
                    if (uncompleted.hasNext) recycleUncompletedPromise(uncompleted)
                    this.recycleStackState(oldState)
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
        }
    }

    private def dispatchAskStack(stack: AskStack[M & Ask[? <: Reply]]): Unit = {
        currentStack = stack
        try {
            val uncompleted = stack.uncompletedPromises()
            val oldState    = stack.state
            continueAsk(stack) match // run stack and switch to next state
                case Some(state) =>
                    if (state != oldState) {
                        stack.setState(state) // this also recycled all completed promise
                        if (uncompleted.hasNext) recycleUncompletedPromise(uncompleted)
                        this.recycleStackState(oldState)
                    } else { // state == oldState, recover uncompleted promise
                        if (uncompleted.hasNext) stack.addUncompletedPromiseIterator(uncompleted)
                    }
                    assert(stack.hasUncompletedPromise, s"has no future to wait for $stack")
                case None =>
                    if (uncompleted.hasNext) recycleUncompletedPromise(uncompleted)
                    this.recycleStackState(oldState)
                    recycleUncompletedPromise(stack.uncompletedPromises())
                    assert(stack.isDone, "continueAsk is return None but not call return method!")
                    stack.recycle()
        } catch {
            case cause: Throwable =>
                cause.printStackTrace()
                stack.`throw`(ExceptionMessage(cause)) // completed stack with Exception
                recycleUncompletedPromise(stack.uncompletedPromises())
                stack.recycle()
        } finally {
            currentStack = null
        }
    }

    final private def dispatchBatchAskStack(stack: BatchAskStack[M & Ask[? <: Reply]]): Unit = {
        currentStack = stack
        try {
            val uncompleted = stack.uncompletedPromises()
            val oldState    = stack.state
            batchContinueAsk(stack) match
                case Some(state) =>
                    if (state != oldState) {
                        stack.setState(state) // this also recycled all completed promise
                        if (uncompleted.hasNext) recycleUncompletedPromise(uncompleted)
                        this.recycleStackState(oldState)
                    } else { // state == oldState, recover uncompleted promise
                        if (uncompleted.hasNext) stack.addUncompletedPromiseIterator(uncompleted)
                    }
                    assert(stack.hasUncompletedPromise, s"has no future to wait for $stack")
                case None =>
                    if (uncompleted.hasNext) recycleUncompletedPromise(uncompleted)
                    this.recycleStackState(oldState)
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
        }
    }

    private def dispatchAskTimeoutEvent(timeoutEvent: AskTimeoutEvent): Unit = {
        val promise = this.pop(timeoutEvent.askId)
        if (promise != null) {
            promise match
                case promise: TimeoutEventPromise => promise.setSuccess(timeoutEvent)
                case promise: ReplyPromise[?]     => promise.setFailure(new TimeoutException())

            val stack = promise.actorStack
            handlePromiseCompleted(stack, promise)
        }
    }

    private def handlePromiseCompleted(stack: Stack, promise: AbstractPromise[?]): Unit = {
        stack.addCompletedPromise(promise)
        if (stack.state.resumable() || !stack.hasUncompletedPromise) {
            // resume running current stack frame to next state
            currentStack match
                case stack: AskStack[?]     => dispatchAskStack(stack.asInstanceOf[AskStack[M & Ask[? <: Reply]]])
                case stack: NoticeStack[?]  => dispatchNoticeStack(stack.asInstanceOf[NoticeStack[M & Notice]])
                case s: BatchAskStack[?]    => dispatchBatchAskStack(s.asInstanceOf[BatchAskStack[M & Ask[? <: Reply]]])
                case s: BatchNoticeStack[?] => dispatchBatchNoticeStack(s.asInstanceOf[BatchNoticeStack[M & Notice]])
                case stack: ChannelStack[?] => dispatchChannelStack(stack)
                case _                      =>
        }
    }

    private[core] def recycleUncompletedPromise(uncompleted: PromiseIterator): Unit = {
        while (uncompleted.hasNext) {
            val promise = uncompleted.next()
            this.pop(promise.id)
            promise.recycle()
        }
    }

    /** Exception handler when this actor received notice message or resume notice stack frame
     *
     *  @param e
     *    exception
     */
    private[core] def handleNoticeException(stack: Stack, e: Throwable): Unit = { // TODO: refactor

        val log = stack match
            case s: AskStack[?]      => s"Stack with call message ${s.ask} failed at handle $currentReceived message"
            case s: NoticeStack[?]   => s"Stack with call message ${s.notice} failed at handle $currentReceived message"
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

    inline private def recycleStackState(state: StackState): Unit = state match {
        case state: Poolable => state.recycle()
        case _               => // gc
    }

    //// ============= ChannelsActor API =================

    private[core] def dispatchChannelStack(stack: ChannelStack[?]): Unit

    /** Receive IO event from [[Reactor]] or timeout event from [[Timer]]
     *
     *  @param event
     *    IO/timeout event
     */
    private[core] def receiveReactorEvent(event: Event): Unit = {}

    //// ================= USER API =======================

    // ------------ continue interface ---------------

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

    /** whether this actor is a batch actor, if override it to true, actor system will dispatch seq message to
     *  receiveBatchXXX method
     */
    def batchable: Boolean = false

    /** max size message for each batch, usage for schedule system */
    def maxBatchSize: Int = system.defaultMaxBatchSize

    val batchNoticeFilter: Notice => Boolean = TURE_FUNC

    val batchAskFilter: Ask[?] => Boolean = TURE_FUNC

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

}

private object AbstractActor {

    private val TURE_FUNC: AnyRef => Boolean  = _ => true
    private val FALSE_FUNC: AnyRef => Boolean = _ => false

}