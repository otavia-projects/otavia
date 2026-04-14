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
import cc.otavia.core.system.{ActorHouse, ActorSystem}
import cc.otavia.core.timer.Timer

import scala.concurrent.TimeoutException
import scala.language.unsafeNulls

/** The runtime kernel base class for all actors. It extends [[FutureDispatcher]] for O(1) promise lookup and implements
 *  the [[Actor]] trait.
 *
 *  This class contains the stack coroutine engine and kernel dispatch machinery. User-facing API (lifecycle hooks,
 *  scheduling configuration, timeout handler, self address) lives in the [[Actor]] trait. The only user-facing methods
 *  here are the resume methods ([[resumeAsk]], [[resumeNotice]], etc.) which cannot be moved to [[Actor]] due to the
 *  `+M` variance constraint.
 *
 *  Users never extend this class directly — use [[StateActor]] for pure business logic or [[ChannelsActor]] for
 *  IO-capable actors.
 *
 *  @tparam M
 *    the type of messages this actor can handle
 */
private[core] abstract class AbstractActor[M <: Call] extends FutureDispatcher with Actor[M] {

    // =========================================================================
    // Section 1: RUNTIME STATE
    // =========================================================================

    /** Logger for this actor instance, initialized during mount. */
    protected var logger: Logger = _

    /** The [[ActorHouse]] that manages this actor's lifecycle, mailboxes, and scheduling. */
    private var house: ActorHouse = _

    /** The message currently being processed. Set during dispatch, null otherwise.
     *  - [[Notice]] during receiveNotice dispatch
     *  - [[Ask]] during receiveAsk dispatch
     *  - [[Reply]] during receiveReply dispatch
     *  - [[Seq]] of [[Notice]] during receiveBatchNotice dispatch
     *  - [[Seq]] of [[Envelope]] of [[Ask]] during receiveBatchAsk dispatch
     *  - [[AnyRef]] during receiveChannelMessage (channel message)
     */
    private[core] var currentReceived: Call | Reply | Seq[Call] | AnyRef = _

    /** The [[Stack]] currently being executed by the stack coroutine engine. */
    private[core] var currentStack: Stack = _

    // =========================================================================
    // Section 2: USER PROGRAMMING INTERFACE
    // Override these protected methods to implement actor behavior.
    // =========================================================================

    /** Handle an [[Ask]] message received by this actor, or resume a suspended stack when the awaited reply arrives.     *
     *  Match on [[stack.state]] to determine the current execution point:
     *  {{{
     *  override protected def resumeAsk(stack: AskStack[MyAsk]): StackYield =
     *    stack.state match
     *      case _: StartState =>
     *        val state = FutureState[MyReply]()
     *        address.ask(MyRequest(), state.future)
     *        stack.suspend(state)
     *      case state: FutureState[MyReply] =>
     *        stack.return(state.future.getNow)
     *  }}}
     *
     *  @param stack
     *    the ask stack containing the received [[Ask]] message and current execution state
     *  @return
     *    [[StackYield]] — [[StackYield]] returned by [[Stack.suspend]] to yield with a state, or by
     *    [[AskStack.return]] to complete with a reply
     */
    protected def resumeAsk(stack: AskStack[M & Ask[? <: Reply]]): StackYield =
        throw new NotImplementedError(getClass.getName + ": an implementation is missing")

    /** Handle a [[Notice]] message received by this actor, or resume a suspended stack when the awaited reply arrives.
     *
     *  @param stack
     *    the notice stack containing the received [[Notice]] message and current execution state
     *  @return
     *    [[StackYield]] — [[StackYield]] returned by [[Stack.suspend]] to yield with a state, or by
     *    [[NoticeStack.return]] to complete
     */
    protected def resumeNotice(stack: NoticeStack[M & Notice]): StackYield =
        throw new NotImplementedError(getClass.getName + ": an implementation is missing")

    /** Handle a batch of [[Notice]] messages received by this actor.
     *
     *  @param stack
     *    the batch notice stack containing all received [[Notice]] messages
     *  @return
     *    [[StackYield]] — [[StackYield]] returned by [[Stack.suspend]] to yield with a state, or by
     *    [[BatchNoticeStack.return]] to complete
     */
    protected def resumeBatchNotice(stack: BatchNoticeStack[M & Notice]): StackYield =
        throw new NotImplementedError(getClass.getName + ": an implementation is missing")

    /** Handle a batch of [[Ask]] messages received by this actor.
     *
     *  @param stack
     *    the batch ask stack containing all received [[Ask]] messages
     *  @return
     *    [[StackYield]] — [[StackYield]] returned by [[Stack.suspend]] to yield with a state, or by
     *    [[BatchAskStack.return]] to complete
     */
    protected def resumeBatchAsk(stack: BatchAskStack[M & Ask[? <: Reply]]): StackYield =
        throw new NotImplementedError(getClass.getName + ": an implementation is missing")

    // =========================================================================
    // Section 3: USER UTILITIES
    // Protected helper methods available to subclasses.
    // =========================================================================

    /** Self address of this actor instance. Cannot be moved to [[Actor]] trait due to Address[-M] being
     *  contravariant, which conflicts with Actor's covariant +M.
     */
    def self: Address[M] = context.address.asInstanceOf[Address[M]]

    final override def context: ActorContext = house

    /** Send a [[Notice]] to self, placing it at the head of the notice mailbox (highest priority within notices).
     *  Useful for self-triggered processing that should be handled before other pending notices.
     */
    protected final def noticeSelfHead(call: Notice & M): Unit = {
        val envelope = Envelope[Notice & M]()
        envelope.setContent(call)
        house.address.asInstanceOf[PhysicalAddress[M]].house.putCallToHead(envelope)
    }

    // =========================================================================
    // Section 4: KERNEL DISPATCH ENGINE
    // All methods below are private[core] or private — internal to the framework.
    // =========================================================================

    // --- Kernel: Lifecycle wiring ---

    /** This method is called by [[ActorSystem]] when the actor is mounted. Injects the [[ActorHouse]] context and
     *  initializes the logger.
     *
     *  @param context
     *    the ActorHouse managing this actor
     */
    final private[core] def setCtx(context: ActorHouse): Unit = {
        house = context
        logger = Logger.getLogger(getClass, house.system)
    }

    final private[core] def mount(): Unit = try {
        this.afterMount()
    } catch {
        case t: Throwable => logger.error("afterMount error with", t)
    }

    private[core] def generateSendMessageId(): Long = house.generateSendMessageId()

    /** Check whether the given message should act as a barrier. Delegates to the user-overridable [[isBarrierCall]]. */
    final private[core] def isBarrier(call: Call): Boolean = isBarrierCall(call)

    // --- Kernel: Future/Promise wiring ---

    /** When this actor sends an ask message to another actor, this method binds the resulting [[Future]] to the
     *  current stack so the stack can be resumed when the reply arrives.
     *
     *  @param askId
     *    message id of the ask message sent to the other actor
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
                house.increaseSendCounter()
                this.push(promise)
            case _ =>
    }

    // --- Kernel: Entry points (called by ActorHouse) ---

    final private[core] def receiveNotice(envelope: Envelope[?]): Unit = {
        val notice = envelope.message.asInstanceOf[Notice]
        currentReceived = notice
        envelope.recycle()
        val stack = NoticeStack[M & Notice](this)
        stack.setNotice(notice)
        dispatchNoticeStack(stack)
        currentReceived = null
    }

    final private[core] def receiveBatchNotice(notices: Seq[Notice]): Unit = {
        currentReceived = notices
        val stack = BatchNoticeStack[M & Notice](this)
        stack.setNotices(notices)
        dispatchBatchNoticeStack(stack)
        currentReceived = null
    }

    final private[core] def receiveAsk(envelope: Envelope[?]): Unit = {
        val ask = envelope.message.asInstanceOf[Ask[?]]
        currentReceived = ask
        val stack = AskStack[M & Ask[? <: Reply]](this)
        stack.setAsk(envelope)
        envelope.recycle()
        dispatchAskStack(stack)
        currentReceived = null
    }

    final private[core] def receiveBatchAsk(asks: Seq[Envelope[Ask[?]]]): Unit = {
        currentReceived = asks
        val stack = BatchAskStack[M & Ask[?]](this)
        stack.setAsks(asks)
        dispatchBatchAskStack(stack)
        currentReceived = null
    }

    final private[core] def receiveReply(envelope: Envelope[?]): Unit = {
        if (!envelope.isBatchReply) {
            val reply   = envelope.message.asInstanceOf[Reply]
            val replyId = envelope.replyId
            envelope.recycle()
            // reply future maybe has been recycled cause by time-out, or it stack has been throw an error.
            if (this.contains(replyId)) {
                val promise = this.pop(replyId)
                if (promise.canTimeout) system.timer.cancelTimerTask(promise.timeoutId)
                receiveReply0(reply, promise)
            }    // drop reply otherwise
        } else { // reply is batch
            val reply    = envelope.message.asInstanceOf[Reply]
            val replyIds = envelope.replyIds
            envelope.recycle()
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

    private[core] def receiveExceptionReply(envelope: Envelope[?]): Unit =
        if (!envelope.isBatchReply) {
            val exceptionMessage = envelope.message.asInstanceOf[ExceptionMessage]
            val replyId          = envelope.replyId
            envelope.recycle()
            // reply future maybe has been recycled cause by time-out, or it stack has been throw an error.
            if (this.contains(replyId)) {
                val promise = this.pop(replyId)
                if (promise.canTimeout) system.timer.cancelTimerTask(promise.timeoutId)
                receiveReply0(exceptionMessage, promise, true)
            }    // drop reply otherwise
        } else { // reply is batch
            val exceptionMessage = envelope.message.asInstanceOf[ExceptionMessage]
            val replyIds         = envelope.replyIds
            envelope.recycle()
            for (rid <- replyIds if contains(rid)) {
                val promise = pop(rid)
                if (promise.canTimeout) system.timer.cancelTimerTask(promise.timeoutId)
                receiveReply0(exceptionMessage, promise, true)
            }
        }

    final private[core] def receiveEvent(event: Event): Unit = event match {
        case event: AskTimeoutEvent     => dispatchAskTimeoutEvent(event)
        case event: TimeoutEvent        => handleActorTimeout(event)
        case event: ChannelTimeoutEvent => receiveChannelTimeoutEvent(event)
        case event: ReactorEvent        => receiveReactorEvent(event)
        case _                          =>
    }

    final private[core] def receiveFuture(future: Future[?]): Unit = {
        val promise = future.promise.asInstanceOf[AbstractPromise[?]]
        val stack   = future.promise.actorStack
        handlePromiseCompleted(stack, promise)
    }

    // --- Kernel: Internal dispatch ---

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

    // --- Kernel: State machine & pooling ---

    final private[core] def switchState(stack: Stack, stackYield: StackYield): Unit =
        if (!stackYield.completed) {
            val oldState = stack.state
            val newState = stack.getNextState
            if (oldState != newState) {
                stack.setState(newState)         // change the stack to new state.
                this.recycleStackState(oldState) // recycle old state if enabled.
            }
            assert(stack.hasUncompletedPromise, s"has no future to wait for $stack")
        } else {
            this.recycleStackState(stack.state)
            assert(stack.isDone, "None but not call return method of Stack!")
            if (!stack.isInstanceOf[ChannelStack[?]]) recycleStack(stack) // recycle stack instance.
        }

    final private[core] def recycleStack(stack: Stack): Unit = {
        if (stack.hasUncompletedPromise) recycleUncompletedPromise(stack.uncompletedPromises())
        stack.recycle()
        if (house.isBarrier) house.cleanBarrier()
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

    inline private def recycleStackState(state: StackState): Unit = state match {
        case state: Poolable => state.recycle()
        case _               => // gc
    }

    // --- Kernel: Exception handling ---

    /** Exception handler when this actor encounters an unhandled exception while processing a notice or resuming a
     *  notice stack frame.
     *
     *  @param stack
     *    the stack that caused the exception
     *  @param e
     *    the exception
     */
    private[core] def handleNoticeException(stack: Stack, e: Throwable): Unit = {
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

    // --- Kernel: ChannelsActor stubs ---

    private[core] def dispatchChannelStack(stack: ChannelStack[?]): Unit

    /** Receive IO event from [[Reactor]] or timeout event from [[Timer]]
     *
     *  @param event
     *    IO/timeout event
     */
    private[core] def receiveReactorEvent(event: ReactorEvent): Unit = {}

    private[core] def receiveChannelTimeoutEvent(event: ChannelTimeoutEvent): Unit = {}

}
