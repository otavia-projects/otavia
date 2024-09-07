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

import cc.otavia.core.address.*
import cc.otavia.core.cache.Poolable
import cc.otavia.core.message.*
import cc.otavia.core.reactor.*
import cc.otavia.core.slf4a.Logger
import cc.otavia.core.stack.*
import cc.otavia.core.system.ActorSystem
import cc.otavia.core.timer.Timer

import scala.concurrent.TimeoutException
import scala.language.unsafeNulls

private[core] abstract class AbstractActor[M <: Call] extends FutureDispatcher with Actor[M] {

    import AbstractActor.*

    protected var logger: Logger = _

    private var ctx: ActorContext = _

    // current received msg
    private[core] var currentReceived: Call | Reply | Seq[Call] | AnyRef = _

    private[core] var inBarrier: Boolean = false

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

    protected final def noticeSelfHead(call: Notice & M): Unit = {
        val envelope = Envelope[Notice & M]()
        envelope.setContent(call)
        ctx.address.asInstanceOf[PhysicalAddress[M]].house.putCallToHead(envelope)
    }

    /** When this actor send ask message to other actor, a [[Future]] will attach to current stack
     *
     *  @param askId
     *    message id of ask message send to other actor
     *  @param future
     *    the reply message future for this ask message
     */
    private[core] def attachStack(askId: Long, future: Future[?]): Unit = {
        val promise = future.promise.asInstanceOf[AbstractPromise[?]]
        assert(promise.notInChain, "The Future has been used, can't be use again!")
        promise.setStack(currentStack)
        promise.setId(askId)
        currentStack.addUncompletedPromise(promise)
        promise match
            case promise: MessagePromise[? <: Reply] =>
                sendAsks += 1
                this.push(promise)
            case _ =>
    }

    final private[core] def receiveFuture(future: Future[?]): Unit = {
        val promise = future.promise.asInstanceOf[AbstractPromise[?]]
        val stack   = future.promise.actorStack
        handlePromiseCompleted(stack, promise)
    }

    final override private[core] def receiveNotice(envelope: Envelope[?]): Unit = {
        val notice = envelope.message.asInstanceOf[Notice]
        currentReceived = notice
        // TODO: recycle envelope
        inBarrier = isBarrierCall(notice)
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

    final override private[core] def receiveAsk(envelope: Envelope[?]): Unit = {
        revAsks += 1
        val ask = envelope.message.asInstanceOf[Ask[?]]
        currentReceived = ask
        inBarrier = isBarrierCall(ask)
        val stack = AskStack[M & Ask[? <: Reply]](this) // generate a AskStack instance from object pool.
        stack.setAsk(envelope)
        // TODO: recycle envelope
        dispatchAskStack(stack)
        currentReceived = null
    }

    final override private[core] def receiveBatchAsk(asks: Seq[Envelope[Ask[?]]]): Unit = {
        currentReceived = asks
        val stack = BatchAskStack[M & Ask[?]](this) // generate a BatchAskStack instance from object pool.
        stack.setAsks(asks)
        dispatchBatchAskStack(stack)
        currentReceived = null
    }

    final override private[core] def receiveReply(envelope: Envelope[?]): Unit = {
        if (!envelope.isBatchReply) {
            val reply   = envelope.message.asInstanceOf[Reply]
            val replyId = envelope.replyId
            // TODO: recycle envelope
            // reply future maybe has been recycled cause by time-out, or it stack has been throw an error.
            if (this.contains(replyId)) {
                val promise = this.pop(replyId)
                if (promise.canTimeout) system.timer.cancelTimerTask(promise.timeoutId)
                receiveReply0(reply, promise)
            }    // drop reply otherwise
        } else { // reply is batch
            val reply    = envelope.message.asInstanceOf[Reply]
            val replyIds = envelope.replyIds
            // TODO: recycle envelope
            for (rid <- replyIds if contains(rid)) {
                val promise = pop(rid)
                if (promise.canTimeout) system.timer.cancelTimerTask(promise.timeoutId)
                receiveReply0(reply, promise)
            }
        }
    }

    private def receiveReply0(reply: Reply, promise: MessagePromise[?], exception: Boolean = false): Unit = {
        currentReceived = reply
        if (exception) promise.setFailure(reply.asInstanceOf[ExceptionMessage]) else promise.setSuccess(reply)
        val stack = promise.actorStack
        handlePromiseCompleted(stack, promise)
        currentReceived = null
    }

    override private[core] def receiveExceptionReply(envelope: Envelope[?]): Unit =
        if (!envelope.isBatchReply) {
            val exceptionMessage = envelope.message.asInstanceOf[ExceptionMessage]
            val replyId          = envelope.replyId
            // TODO: recycle envelope
            // reply future maybe has been recycled cause by time-out, or it stack has been throw an error.
            if (this.contains(replyId)) {
                val promise = this.pop(replyId)
                if (promise.canTimeout) system.timer.cancelTimerTask(promise.timeoutId)
                receiveReply0(exceptionMessage, promise, true)
            }    // drop reply otherwise
        } else { // reply is batch
            val exceptionMessage = envelope.message.asInstanceOf[ExceptionMessage]
            val replyIds         = envelope.replyIds
            // TODO: recycle envelope
            for (rid <- replyIds if contains(rid)) {
                val promise = pop(rid)
                if (promise.canTimeout) system.timer.cancelTimerTask(promise.timeoutId)
                receiveReply0(exceptionMessage, promise, true)
            }
        }

    final override private[core] def receiveEvent(event: Event): Unit = event match {
        case event: AskTimeoutEvent     => dispatchAskTimeoutEvent(event)
        case event: TimeoutEvent        => handleActorTimeout(event)
        case event: ChannelTimeoutEvent => receiveChannelTimeoutEvent(event)
        case event: ReactorEvent        => receiveReactorEvent(event)
        case _                          =>
    }

    private def dispatchNoticeStack(stack: NoticeStack[M & Notice]): Unit = {
        currentStack = stack
        try {
            this.switchState(stack, resumeNotice(stack))
        } catch {
            case cause: Throwable =>
                cause.printStackTrace()
                handleNoticeException(stack, cause)
                recycleStack(stack)
        } finally currentStack = null
    }

    private def dispatchBatchNoticeStack(stack: BatchNoticeStack[M & Notice]): Unit = {
        currentStack = stack
        try {
            this.switchState(stack, resumeBatchNotice(stack))
        } catch {
            case cause: Throwable =>
                handleNoticeException(stack, cause)
                recycleStack(stack)
        } finally currentStack = null
    }

    private def dispatchAskStack(stack: AskStack[M & Ask[? <: Reply]]): Unit = {
        currentStack = stack
        try {
            this.switchState(stack, resumeAsk(stack))
        } catch {
            case cause: Throwable =>
                cause.printStackTrace()
                stack.`throw`(ExceptionMessage(cause)) // completed stack with Exception
                recycleStack(stack)
        } finally currentStack = null
    }

    private def dispatchBatchAskStack(stack: BatchAskStack[M & Ask[? <: Reply]]): Unit = {
        currentStack = stack
        try {
            this.switchState(stack, resumeBatchAsk(stack))
        } catch {
            case cause: Throwable =>
                stack.`throw`(ExceptionMessage(cause)) // completed stack with Exception
                recycleStack(stack)
        } finally currentStack = null
    }

    private def dispatchAskTimeoutEvent(timeoutEvent: AskTimeoutEvent): Unit = {
        val promise = this.pop(timeoutEvent.askId)
        if (promise != null) {
            if (promise.canTimeout) promise.setFailure(new TimeoutException())
            else {
                val timeoutReply = TimeoutReply()
                promise.setSuccess(timeoutReply)
            }

            val stack = promise.actorStack
            handlePromiseCompleted(stack, promise)
        }
    }

    private def handlePromiseCompleted(stack: Stack, promise: AbstractPromise[?]): Unit = {
        stack.moveCompletedPromise(promise)
        if (stack.state.resumable() || !stack.hasUncompletedPromise) {
            // resume running current stack frame to next state
            stack match
                case stack: AskStack[?]     => dispatchAskStack(stack.asInstanceOf[AskStack[M & Ask[? <: Reply]]])
                case stack: NoticeStack[?]  => dispatchNoticeStack(stack.asInstanceOf[NoticeStack[M & Notice]])
                case s: BatchAskStack[?]    => dispatchBatchAskStack(s.asInstanceOf[BatchAskStack[M & Ask[? <: Reply]]])
                case s: BatchNoticeStack[?] => dispatchBatchNoticeStack(s.asInstanceOf[BatchNoticeStack[M & Notice]])
                case stack: ChannelStack[?] => dispatchChannelStack(stack)
                case _                      =>
        }
    }

    final private[core] def switchState(stack: Stack, stackYield: StackYield): Unit =
        if (!stackYield.completed) {
            val oldState = stack.state
            val newState = stack.getNextState()
            if (oldState != newState) {
                stack.setState(newState)         // change the stack to new state.
                this.recycleStackState(oldState) // recycle old state if enable.
            }
            assert(stack.hasUncompletedPromise, s"has no future to wait for $stack")
        } else {
            this.recycleStackState(stack.state)
            assert(stack.isDone, "None but not call return method of Stack!")
            if (!stack.isInstanceOf[ChannelStack[?]]) recycleStack(stack) // recycle stack instance.
        }

    private def recycleUncompletedPromise(uncompleted: PromiseIterator): Unit = while (uncompleted.hasNext) {
        val promise = uncompleted.next()

        promise match
            case promise: ChannelPromise =>
                promise.setStack(null)
                promise.deChain()
            case promise: MessagePromise[?] =>
                this.pop(promise.id)
                promise.recycle()
            case _ =>

    }

    final private[core] def recycleStack(stack: Stack): Unit = {
        if (stack.hasUncompletedPromise) recycleUncompletedPromise(stack.uncompletedPromises())
        stack.recycle()
        if (inBarrier) inBarrier = false
    }

    /** Exception handler when this actor received notice message or resume notice stack frame
     *
     *  @param e
     *    exception
     */
    private[core] def handleNoticeException(stack: Stack, e: Throwable): Unit = { // TODO: refactor

        val log = stack match
            case s: NoticeStack[?] => s"Stack with call message ${s.notice} failed at handle $currentReceived message"
            case s: BatchNoticeStack[?] =>
                s"Stack with call message ${s.notices} failed at handle $currentReceived message"
            case _ => ""
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
    private[core] def receiveReactorEvent(event: ReactorEvent): Unit = {}

    private[core] def receiveChannelTimeoutEvent(event: ChannelTimeoutEvent): Unit = {}

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
    protected def resumeAsk(stack: AskStack[M & Ask[? <: Reply]]): StackYield =
        throw new NotImplementedError(getClass.getName + ": an implementation is missing")

    /** implement this method to handle notice message and resume when received reply message for this notice message
     *
     *  @param stack
     *    notice message receive by this actor instance, or resume frame .
     *  @return
     *    an option value containing the resumable [[StackState]] waited for some reply message, or `None` if the stack
     *    frame has finished.
     */
    protected def resumeNotice(stack: NoticeStack[M & Notice]): StackYield =
        throw new NotImplementedError(getClass.getName + ": an implementation is missing")

    /** whether this actor is a batch actor, if override it to true, actor system will dispatch seq message to
     *  receiveBatchXXX method
     */
    def batchable: Boolean = false

    /** max size message for each batch, usage for schedule system */
    def maxBatchSize: Int = system.defaultMaxBatchSize

    val batchNoticeFilter: Notice => Boolean = TURE_FUNC

    val batchAskFilter: Ask[?] => Boolean = TURE_FUNC

    protected def resumeBatchNotice(stack: BatchNoticeStack[M & Notice]): StackYield =
        throw new NotImplementedError(getClass.getName + ": an implementation is missing")

    protected def resumeBatchAsk(stack: BatchAskStack[M & Ask[? <: Reply]]): StackYield =
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
