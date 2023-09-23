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

import cc.otavia.core.actor.Actor
import cc.otavia.core.actor.ChannelsActor.*
import cc.otavia.core.address.{ActorAddress, Address}
import cc.otavia.core.channel.*
import cc.otavia.core.message.*
import cc.otavia.core.reactor.*
import cc.otavia.core.slf4a.Logger
import cc.otavia.core.stack.*
import cc.otavia.core.system.ActorThread
import cc.otavia.core.timer.Timer
import cc.otavia.core.util.ThrowableUtil

import java.net.*
import java.nio.channels.SelectionKey
import java.nio.file.attribute.FileAttribute
import java.nio.file.{OpenOption, Path}
import java.util.concurrent.CancellationException
import scala.collection.mutable
import scala.language.unsafeNulls
import scala.reflect.ClassTag
import scala.util.*

abstract class ChannelsActor[M <: Call] extends AbstractActor[M] {

    private var channelCursor                  = 0
    private var currentChannelReceived: AnyRef = _

    protected val channels: mutable.HashMap[Int, ChannelAddress] = mutable.HashMap.empty

    override def self: ActorAddress[M] = super.self.asInstanceOf[ActorAddress[M]]

    def address: ActorAddress[M] = self

    final def reactor: Reactor = system.reactor

    protected def family: ProtocolFamily = StandardProtocolFamily.INET

    private[core] def generateChannelId(): Int = {
        val channelId = channelCursor
        channelCursor += 1
        channelId
    }

    def receiveChannelMessage(stack: ChannelStack[?]): Unit = {
        currentStack = stack
        currentChannelReceived = stack.message
        runChannelStack()
    }

    private def runChannelStack(): Unit = {
        val channelStack = currentStack.asInstanceOf[ChannelStack[?]]
        try {
            val uncompleted = channelStack.uncompletedIterator()
            val oldState    = channelStack.state
            continueChannel(channelStack) match // run stack and switch to next state
                case Some(state) =>
                    if (state != oldState) {
                        channelStack.setState(state) // this also recycled all completed promise
                        if (uncompleted.hasNext) recycleUncompletedPromise(uncompleted)
                    } else { // state == oldState, recover uncompleted promise
                        if (uncompleted.hasNext) channelStack.addUncompletedPromiseIterator(uncompleted)
                    }
                    assert(channelStack.hasUncompletedPromise, s"has no future to wait for $channelStack")
                case None =>
                    if (uncompleted.hasNext) recycleUncompletedPromise(uncompleted)
                    recycleUncompletedPromise(channelStack.uncompletedIterator())
                    assert(channelStack.isDone, "continueAsk is return None but not call return method!")
                    channelStack.recycle()
        } catch {
            case cause: Throwable =>
                cause.printStackTrace()
                channelStack.`return`(cause) // completed stack with Exception
                recycleUncompletedPromise(channelStack.uncompletedIterator())
                channelStack.recycle()
        } finally {
            currentStack = null
            currentReceived = null
        }
    }

    final override protected def receiveReactorEvent(event: Event): Unit = event match
        case e: ReactorEvent.RegisterReply =>
            e.channel.handleChannelRegisterReplyEvent(e)
            afterChannelRegisterReplyEvent(e)
        case e: ReactorEvent.DeregisterReply =>
            e.channel.handleChannelDeregisterReplyEvent(e)
            afterChannelDeregisterReplyEvent(e)
        case e: ReactorEvent.ChannelClose =>
            e.channel.handleChannelCloseEvent(e)
            afterChannelCloseEvent(e)
        case e: ReactorEvent.ChannelReadiness =>
            e.channel.handleChannelReadinessEvent(e)
            afterChannelReadinessEvent(e)
        case channelTimeoutEvent: ChannelTimeoutEvent =>
            channelTimeoutEvent.channel.handleChannelTimeoutEvent(channelTimeoutEvent.registerId)
            afterChannelTimeoutEvent(channelTimeoutEvent)
        case e: ReactorEvent.AcceptedEvent =>
            e.channel.handleChannelAcceptedEvent(e)
        case e: ReactorEvent.ReadCompletedEvent =>
            e.channel.handleChannelReadCompletedEvent(e)
        case e: ReactorEvent.BindReply =>
            e.channel.handleChannelBindReplyEvent(e)
        case e: ReactorEvent.ConnectReply =>
            e.channel.handleChannelConnectReplyEvent(e)
        case e: ReactorEvent.ReadBuffer => e.channel.handleChannelReadBufferEvent(e)
        case e: ReactorEvent.OpenReply  => e.channel.handleChannelOpenReplyEvent(e)

    // Event from Reactor

    /** Handle channel close event */
    protected def afterChannelCloseEvent(event: ReactorEvent.ChannelClose): Unit = {}

    /** Handle channel register result event */
    protected def afterChannelRegisterReplyEvent(event: ReactorEvent.RegisterReply): Unit = {}

    /** Handle channel deregister result event */
    private def afterChannelDeregisterReplyEvent(event: ReactorEvent.DeregisterReply): Unit = {}

    /** Handle channel readiness event */
    private def afterChannelReadinessEvent(event: ReactorEvent.ChannelReadiness): Unit = {}

    // Event from Timer

    private def afterChannelTimeoutEvent(channelTimeoutEvent: ChannelTimeoutEvent): Unit = {}

    // End handle event.

    def handler: Option[ChannelInitializer[? <: Channel]] = None

    /** Initial and register a channel for this [[ChannelsActor]]. It do the flowing things:
     *    1. Create the [[Channel]].
     *    1. Initial the [[Channel]] with [[init]].
     *    1. Register the [[Channel]] to [[Reactor]]. When register channel success, the [[Reactor]] will send a
     *       [[ReactorEvent.RegisterReply]] event to this actor, then the [[afterChannelRegisterReplyEvent]] will be
     *       called to handle the register result [[Event]].
     */
    final protected def initAndRegister(channelAddress: ChannelAddress, stack: AskStack[?]): Option[StackState] = {
        val channel = channelAddress.asInstanceOf[Channel]
        if (!channel.isMounted) channel.mount(this)
        Try {
            init(channel)
        } match {
            case Success(_) =>
                val state = new RegisterWaitState()
                channel.register(state.registerFuture)
                state.suspend()
            case Failure(cause) =>
                val closeFuture = channel.close(ChannelFuture()) // ignore close result.
                stack.`throw`(ExceptionMessage(cause))
        }
    }

    /** Create a new channel and set executor and init it. */
    @throws[Exception]
    final protected def newChannelAndInit(): ChannelAddress = {
        val channel = newChannel()
        channel.mount(this)
        try {
            init(channel)
        } catch {
            case cause: Throwable =>
                channel.closeAfterCreate()
                throw cause
        }
        channel
    }

    /** Create a new [[Channel]] */
    protected def newChannel(): Channel

    @throws[Exception]
    protected def init(channel: Channel): Unit

    final protected def newFileChannel(): Channel = system.channelFactory.openFileChannel()

    protected def openFileChannel(
        path: Path,
        options: Seq[OpenOption],
        attrs: Seq[FileAttribute[?]],
        future: ChannelFuture
    ): ChannelFuture = ???

    final protected def close(stack: AskStack[Close]): Option[StackState] = {
        stack.state match
            case StackState.start =>
                val removes = if (stack.ask.ids.isEmpty) channels.keys else stack.ask.ids
                if (removes.isEmpty) stack.`return`(CloseReply(Iterable.empty))
                else {
                    val futures = removes
                        .map { id =>
                            val ch = channels.remove(id)
                            ch.map(c => c.close(ChannelFuture()))
                        }
                        .filter(_.nonEmpty)
                        .map(_.get)
                    val state = new CloseState(futures)
                    state.suspend()
                }
            case closeState: CloseState =>
                if (closeState.futures.forall(_.isSuccess))
                    stack.`return`(CloseReply(closeState.futures.map(_.channel.id)))
                else {
                    val fails = closeState.futures.filter(_.isFailed)
                    val errors =
                        fails.map(f => ThrowableUtil.stackTraceToString(f.causeUnsafe)).mkString("[", "\n", "]")
                    stack.`throw`(ExceptionMessage(s"failed close at channel with ${errors}"))
                }
    }

    // 1. how to design tail handler, channel group self ?
    // 2. how to use message codec in handler
    // 3. channel chooser

    final def inExecutor(): Boolean = {
        Thread.currentThread() match
            case thread: cc.otavia.core.system.ActorThread => thread.currentRunningActor() == this
            case _                                         => false
    }

}

object ChannelsActor {

    final class RegisterWaitState extends StackState {
        val registerFuture: ChannelFuture = ChannelFuture()
    }

    final class CloseState(val futures: Iterable[ChannelFuture]) extends StackState

    final case class Connect(remote: SocketAddress, local: Option[SocketAddress] = None) extends Ask[ConnectReply]
    case class ConnectReply(channelId: Int)                                              extends Reply

    case class Disconnect(ids: Seq[Int] = Seq.empty) extends Ask[DisconnectReply]
    case class DisconnectReply(ids: Seq[Int])        extends Reply

    case class Close(ids: Seq[Int] = Seq.empty) extends Ask[CloseReply]
    case class CloseReply(ids: Iterable[Int])   extends Reply

    object Connect {

        def apply(host: String, port: Int): Connect = Connect(
          InetSocketAddress.createUnresolved(host, port)
        )

        def apply(host: InetAddress, port: Int): Connect = Connect(new InetSocketAddress(host, port))

    }

    case class Bind(local: SocketAddress) extends Ask[BindReply]

    object Bind {

        def apply(port: Int): Bind = Bind(new InetSocketAddress(port))

        def apply(host: String, port: Int): Bind = Bind(new InetSocketAddress(host, port))

        def apply(host: InetAddress, port: Int): Bind = Bind(new InetSocketAddress(host, port))

    }

    case class BindReply(channelId: Int) extends Reply

}
