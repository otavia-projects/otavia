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

package io.otavia.core.channel.inflight

import io.otavia.core.channel.{AbstractChannel, Channel, ChannelInflight}
import io.otavia.core.reactor.{Event, ReactorEvent}
import io.otavia.core.stack.*

import java.nio.channels.{AlreadyBoundException, ClosedChannelException, ConnectionPendingException}
import scala.collection.mutable
import scala.language.unsafeNulls

trait ChannelInflightImpl extends ChannelInflight {
    this: AbstractChannel[?, ?] =>

    private var inboundMsgBarrier: AnyRef => Boolean  = _ => false
    private var outboundMsgBarrier: AnyRef => Boolean = _ => true

    // outbound futures which is write to channel and wait channel reply
    private val outboundInflightFutures: FutureQueue = new FutureQueue()

    // outbound futures which is waiting channel to send
    private val outboundPendingFutures: FutureQueue = new FutureQueue()

    // inbound stack which is running by actor
    private val inboundInflightStacks: StackQueue = new StackQueue()

    // inbound stack to wait actor running
    private val inboundPendingStacks: StackQueue = new StackQueue()

    private var registerPromise: DefaultPromise[ReactorEvent.RegisterReply]     = _
    private var deregisterPromise: DefaultPromise[ReactorEvent.DeregisterReply] = _
    private var connectPromise: DefaultPromise[Channel]                         = _

    final override def inboundMessageBarrier: AnyRef => Boolean = inboundMsgBarrier

    final override def setInboundMessageBarrier(barrier: AnyRef => Boolean): Unit = inboundMsgBarrier = barrier

    final override def outboundMessageBarrier: AnyRef => Boolean = outboundMsgBarrier

    final override def setOutboundMessageBarrier(barrier: AnyRef => Boolean): Unit = outboundMsgBarrier = barrier

    private def attachStack(promise: Promise[?]): Unit = {
        assert(promise.notInChain, "The Promise has been used, can't be use again!")
        promise.setStack(this.executor.currentStack)
        this.executor.currentStack.addUncompletedPromise(promise)
    }

    override def connect(future: DefaultFuture[Channel]): Future[Channel] = {
        val promise = future.promise
        attachStack(promise)
        if (connectPromise != null) {
            promise.setFailure(new ConnectionPendingException())
            this.executor.receiveFuture(promise)
        } else {
            connectPromise = promise
            try {
                this.pipeline.connect()
            } catch {
                case cause: Throwable => ???
            }
        }

        future
    }

    override def onConnectFinsh(error: Option[Throwable]): Unit = {
        error match
            case Some(cause) => connectPromise.setFailure(cause)
            case None        => connectPromise.setSuccess(this)
        val promise = connectPromise
        connectPromise = null
        this.executor.receiveFuture(promise)
    }

    override def disconnect(future: DefaultFuture[Channel]): Future[Channel] = ???

    override def close(future: DefaultFuture[Channel]): Future[Channel] = ???

    override def register(future: DefaultFuture[ReactorEvent.RegisterReply]): Unit = {
        val promise = future.promise
        attachStack(promise)

        if (registerPromise != null) {
            promise.setFailure(new IllegalStateException(s"The channel $this is registering to reactor!"))
            this.executor.receiveFuture(promise)
        } else {
            registerPromise = promise

            try {
                this.pipeline.register()
            } catch {
                case cause: Throwable =>
                    registerPromise.setFailure(cause)
                    this.executor.receiveFuture(registerPromise)
                    registerPromise = null
            }
        }
    }

    override def onRegisterReply(event: ReactorEvent.RegisterReply): Unit = {
        val actor = this.executor
        event.cause match
            case Some(cause) => registerPromise.setFailure(cause)
            case None        => registerPromise.setSuccess(event)
        val promise = registerPromise
        registerPromise = null
        actor.receiveFuture(promise)
    }

    override def deregister(future: DefaultFuture[ReactorEvent.DeregisterReply]): Unit = {
        val promise = future.promise
        attachStack(promise)

        if (deregisterPromise != null) {
            promise.setFailure(new IllegalStateException(s"The channel $this is unregistering from reactor!"))
            this.executor.receiveFuture(promise)
        } else {
            deregisterPromise = promise
            try {
                this.pipeline.deregister()
            } catch {
                case cause: Throwable =>
                    deregisterPromise.setFailure(cause)
                    this.executor.receiveFuture(deregisterPromise)
                    deregisterPromise = null
            }
        }
    }

    override def onDeregisterReply(event: ReactorEvent.DeregisterReply): Unit = {
        event.cause match
            case Some(cause) => deregisterPromise.setFailure(cause)
            case None        => deregisterPromise.setSuccess(event)
        this.executor.receiveFuture(deregisterPromise)
        deregisterPromise = null
    }

    override def ask(value: AnyRef, future: ChannelReplyFuture): ChannelReplyFuture = {
        val promise = future.promise
        promise.setMessageId(generateMessageId)
        promise.setBarrier(outboundMessageBarrier(value))
        if (outboundInflightFutures.size >= maxOutboundInflight) {
            outboundPendingFutures.append(promise)
        } else {
            if (promise.isBarrier) {
                if (outboundInflightFutures.size == 0) {
                    this.write(value, promise.messageId)
                    this.flush()
                    outboundInflightFutures.append(promise)
                } else outboundPendingFutures.append(promise)
            } else {
                if (outboundInflightFutures.headIsBarrier) outboundPendingFutures.append(promise)
                else {
                    this.write(value, promise.messageId)
                    this.flush()
                    outboundInflightFutures.append(promise)
                }
            }
        }
        future
    }

    override def batchAsk(asks: Seq[AnyRef], futures: Seq[ChannelReplyFuture]): Seq[ChannelReplyFuture] = ???

    override def notice(value: AnyRef): Unit = {
        this.write(value)
        this.flush()
    }

    override def batchNotice(notices: Seq[AnyRef]): Unit = {
        for (notice <- notices) this.write(notice)
        this.flush()
    }

    override def reply(value: AnyRef): Unit = ???

    override def generateMessageId: Long = ???

    override private[core] def onInboundMessage(msg: AnyRef): Unit = ???

    override private[core] def onInboundMessage(msg: AnyRef, id: Long): Unit = {
        val stack = ChannelStack(this, msg, id)
//        this.executor.continueChannelStack()

        this.executor.receiveChannelMessage(stack)
        ???
    }

    final override def outboundInflightSize: Int = outboundInflightFutures.size

    final override def outboundPendingSize: Int = outboundPendingFutures.size

    final override def inboundInflightSize: Int = inboundInflightStacks.size

    final override def inboundPendingSize: Int = inboundPendingStacks.size

}
