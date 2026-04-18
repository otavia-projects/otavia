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
import cc.otavia.core.slf4a.Logger
import cc.otavia.core.stack.*
import cc.otavia.core.system.{ActorHouse, ActorSystem}

import scala.concurrent.TimeoutException
import scala.language.unsafeNulls

/** Stack coroutine engine and kernel dispatch machinery for all actor.
 *
 *  Extends [[FutureDispatcher]] for O(1) promise lookup by reply ID. User-facing lifecycle hooks, scheduling
 *  configuration, and DI accessors live in the [[Actor]] trait. The resume methods ([[resumeAsk]],
 *  [[resumeNotice]], etc.) remain here because the `+M` variance on [[Actor]] prevents them from appearing in the
 *  trait.
 *
 *  Users never extend this class directly — use [[StateActor]] for pure business logic or [[ChannelsActor]] for
 *  IO-capable actor.
 *
 *  @tparam M
 *    the type of messages this actor can handle
 */
private[core] abstract class AbstractActor[M <: Call] extends FutureDispatcher with Actor[M] {

    // =========================================================================
    // Runtime state
    // =========================================================================

    protected var logger: Logger = _

    /** The [[ActorHouse]] managing this actor's mailboxes and scheduling. */
    private var house: ActorHouse = _

    /** The message currently being dispatched. Set during [[receiveAsk]]/[[receiveNotice]]/[[receiveReply]]/etc,
     *  cleared after dispatch completes.
     */
    private[core] var currentReceived: Call | Reply | Seq[Call] | AnyRef = _

    /** The [[Stack]] currently being executed by the coroutine engine, or null if idle. */
    private[core] var currentStack: Stack = _

    // =========================================================================
    // Resume methods — user overrides
    // =========================================================================

    /** Handle an [[Ask]] message. Called on initial receipt and on each resume after an awaited reply completes.
     *
     *  Match on [[stack.state]] to distinguish initial entry from resumed execution:
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
     */
    protected def resumeAsk(stack: AskStack[M & Ask[? <: Reply]]): StackYield =
        throw new NotImplementedError(getClass.getName + ": an implementation is missing")

    /** Handle a [[Notice]] message. Called on initial receipt and on each resume. */
    protected def resumeNotice(stack: NoticeStack[M & Notice]): StackYield =
        throw new NotImplementedError(getClass.getName + ": an implementation is missing")

    /** Handle a batch of [[Notice]] messages in a single stack. */
    protected def resumeBatchNotice(stack: BatchNoticeStack[M & Notice]): StackYield =
        throw new NotImplementedError(getClass.getName + ": an implementation is missing")

    /** Handle a batch of [[Ask]] messages in a single stack. */
    protected def resumeBatchAsk(stack: BatchAskStack[M & Ask[? <: Reply]]): StackYield =
        throw new NotImplementedError(getClass.getName + ": an implementation is missing")

    // =========================================================================
    // Utilities
    // =========================================================================

    /** Typed self-address. Separated from [[Actor]] by the `+M`/`Address[-M]` variance conflict. */
    def self: Address[M] = context.address.asInstanceOf[Address[M]]

    final override def context: ActorContext = house

    /** Send a [[Notice]] to self, inserted at the head of the notice mailbox before all other pending notices. */
    protected final def noticeSelfHead(call: Notice & M): Unit = {
        val envelope = Envelope[Notice & M]()
        envelope.setContent(call)
        house.address.asInstanceOf[PhysicalAddress[M]].house.putCallToHead(envelope)
    }

    /** Whether the calling thread is this actor's executor and this actor is currently running on it. */
    private[core] final def inExecutor(): Boolean =
        Thread.currentThread() == house.manager.thread && house.manager.currentRunningActor == this

    // =========================================================================
    // Kernel: lifecycle
    // =========================================================================

    /** Inject the [[ActorHouse]] context and initialize the logger. Called once during actor creation. */
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

    final private[core] def isBarrier(call: Call): Boolean = isBarrierCall(call)

    // =========================================================================
    // Kernel: promise ↔ stack binding
    // =========================================================================

    /** Bind a [[Future]] from an outgoing ask to the current stack, so the stack resumes when the reply arrives. */
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

    // =========================================================================
    // Kernel: mailbox entry points (called by ActorHouse)
    // =========================================================================

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
            // The promise may have been removed due to timeout or a prior error — drop the reply in that case.
            if (this.contains(replyId)) {
                val promise = this.pop(replyId)
                if (promise.canTimeout) system.timer.cancelTimerTask(promise.timeoutId)
                receiveReply0(reply, promise)
            }
        } else {
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
            if (this.contains(replyId)) {
                val promise = this.pop(replyId)
                if (promise.canTimeout) system.timer.cancelTimerTask(promise.timeoutId)
                receiveReply0(exceptionMessage, promise, true)
            }
        } else {
            val exceptionMessage = envelope.message.asInstanceOf[ExceptionMessage]
            val replyIds         = envelope.replyIds
            envelope.recycle()
            for (rid <- replyIds if contains(rid)) {
                val promise = pop(rid)
                if (promise.canTimeout) system.timer.cancelTimerTask(promise.timeoutId)
                receiveReply0(exceptionMessage, promise, true)
            }
        }

    /** Route an [[Event]] from the event mailbox to the appropriate handler. Channel-specific events
     *  ([[ReactorEvent]], [[ChannelTimeoutEvent]]) are forwarded to [[ChannelMessageSupport]] when the actor
     *  implements it.
     */
    final private[core] def receiveEvent(event: Event): Unit = event match {
        case event: AskTimeoutEvent     => dispatchAskTimeoutEvent(event)
        case event: TimeoutEvent        => handleActorTimeout(event)
        case event: ChannelTimeoutEvent =>
            this match
                case support: ChannelMessageSupport => support.receiveChannelTimeoutEvent(event)
                case _                              =>
        case event: ReactorEvent        =>
            this match
                case support: ChannelMessageSupport => support.receiveReactorEvent(event)
                case _                              =>
        case _                          =>
    }

    final private[core] def receiveFuture(future: Future[?]): Unit = {
        val promise = future.promise.asInstanceOf[AbstractPromise[?]]
        val stack   = future.promise.actorStack
        handlePromiseCompleted(stack, promise)
    }

    // =========================================================================
    // Kernel: stack dispatch
    // =========================================================================

    private def dispatchNoticeStack(stack: NoticeStack[M & Notice]): Unit = {
        currentStack = stack
        try {
            this.switchState(stack, resumeNotice(stack))
        } catch {
            case cause: Throwable =>
                logger.error(s"Unhandled exception in notice stack for actor [${this.getClass.getName}]", cause)
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
                logger.error(s"Unhandled exception in ask stack for actor [${this.getClass.getName}]", cause)
                stack.`throw`(ExceptionMessage(cause))
                recycleStack(stack)
        } finally currentStack = null
    }

    private def dispatchBatchAskStack(stack: BatchAskStack[M & Ask[? <: Reply]]): Unit = {
        currentStack = stack
        try {
            this.switchState(stack, resumeBatchAsk(stack))
        } catch {
            case cause: Throwable =>
                stack.`throw`(ExceptionMessage(cause))
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

    // =========================================================================
    // Kernel: promise completion → stack resume
    // =========================================================================

    /** Move a completed promise onto the stack's completed chain and re-dispatch the stack if it is resumable or
     *  has no remaining uncompleted promises. Channel stacks are dispatched through [[ChannelMessageSupport]].
     */
    private def handlePromiseCompleted(stack: Stack, promise: AbstractPromise[?]): Unit = {
        stack.moveCompletedPromise(promise)
        if (stack.state.resumable() || !stack.hasUncompletedPromise) {
            stack match
                case stack: AskStack[?]     => dispatchAskStack(stack.asInstanceOf[AskStack[M & Ask[? <: Reply]]])
                case stack: NoticeStack[?]  => dispatchNoticeStack(stack.asInstanceOf[NoticeStack[M & Notice]])
                case s: BatchAskStack[?]    => dispatchBatchAskStack(s.asInstanceOf[BatchAskStack[M & Ask[? <: Reply]]])
                case s: BatchNoticeStack[?] => dispatchBatchNoticeStack(s.asInstanceOf[BatchNoticeStack[M & Notice]])
                case stack: ChannelStack[?] =>
                    this match
                        case support: ChannelMessageSupport => support.dispatchChannelStack(stack)
                        case _                              =>
                case _                      =>
        }
    }

    // =========================================================================
    // Kernel: state machine transitions & object pooling
    // =========================================================================

    /** SUSPEND: transition to the next state and recycle the old one. RETURN: recycle the entire stack. */
    final private[core] def switchState(stack: Stack, stackYield: StackYield): Unit =
        if (!stackYield.completed) {
            val oldState = stack.state
            val newState = stack.getNextState
            if (oldState != newState) {
                stack.setState(newState)
                this.recycleStackState(oldState)
            }
            assert(stack.hasUncompletedPromise, s"has no future to wait for $stack")
        } else {
            this.recycleStackState(stack.state)
            assert(stack.isDone, "None but not call return method of Stack!")
            if (!stack.isInstanceOf[ChannelStack[?]]) recycleStack(stack)
        }

    /** Release a completed stack: drain any uncompleted promises and clear the barrier flag. */
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
        case _               =>
    }

    // =========================================================================
    // Kernel: exception handling
    // =========================================================================

    /** Apply the configured [[noticeExceptionStrategy]] when a notice-type stack throws. */
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

}
