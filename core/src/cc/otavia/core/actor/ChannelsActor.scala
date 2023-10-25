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

import cc.otavia.common.ThrowableUtil
import cc.otavia.core.actor.Actor
import cc.otavia.core.actor.ChannelsActor.*
import cc.otavia.core.address.{ActorAddress, Address}
import cc.otavia.core.channel.*
import cc.otavia.core.message.*
import cc.otavia.core.reactor.*
import cc.otavia.core.slf4a.Logger
import cc.otavia.core.stack.*
import cc.otavia.core.stack.helper.ChannelFutureState
import cc.otavia.core.system.ActorThread
import cc.otavia.core.timer.Timer

import java.io.File
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

    override def self: ActorAddress[M] = super.self.asInstanceOf[ActorAddress[M]]

    def address: ActorAddress[M] = self

    final def reactor: Reactor = system.reactor

    protected def family: ProtocolFamily = StandardProtocolFamily.INET

    private[core] def generateChannelId(): Int = { val channelId = channelCursor; channelCursor += 1; channelId }

    private[core] def receiveChannelMessage(stack: ChannelStack[?]): Unit = {
        currentChannelReceived = stack.message
        dispatchChannelStack(stack)
        currentChannelReceived = null
    }

    override final private[core] def dispatchChannelStack(stack: ChannelStack[?]): Unit = {
        currentStack = stack
        try {
            val oldState = stack.state
            val newState = continueChannel(stack)
            this.switchState(stack, oldState, newState)
            if (newState.isEmpty) stack.internalChannel.processCompletedChannelStacks()
        } catch {
            case cause: Throwable =>
                cause.printStackTrace()
                stack.`return`(cause) // completed stack with Exception
                stack.internalChannel.processCompletedChannelStacks()
        } finally currentStack = null
    }

    final override private[core] def receiveReactorEvent(event: ReactorEvent): Unit = {
        event match {
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
        }
    }

    final override private[core] def receiveChannelTimeoutEvent(event: ChannelTimeoutEvent): Unit = {
        val channel = event.channel
        channel.handleChannelTimeoutEvent(event.registerId)
    }

    /** Create a new channel and set executor and init it. */
    @throws[Exception]
    final protected def createChannelAndInit(): ChannelAddress = {
        val channel = newChannel()
        channel.mount(this)
        try {
            initChannel(channel)
            channel
        } catch {
            case cause: Throwable =>
                channel.closeAfterCreate()
                throw cause
        }
    }

    @throws[Exception]
    final protected def createFileChannelAndInit(): ChannelAddress = {
        val channel = system.channelFactory.openFileChannel()
        channel.mount(this)
        try {
            initFileChannel(channel)
            channel
        } catch {
            case cause: Throwable =>
                channel.closeAfterCreate()
                throw cause
        }
    }

    // format: off
    final protected def openFileChannel(path: Path, opts: Seq[OpenOption], attrs: Seq[FileAttribute[?]]): Option[ChannelFutureState] = {
        // format: on
        val channel               = createFileChannelAndInit()
        val state                 = ChannelFutureState()
        val future: ChannelFuture = state.future
        channel.open(path, opts, attrs, future)
        state.suspend().asInstanceOf[Option[ChannelFutureState]]
    }

    // format: off
    final protected def openFileChannel(file: File, opts: Seq[OpenOption], attrs: Seq[FileAttribute[?]]): Option[ChannelFutureState] = {
        // format: on
        val channel               = createFileChannelAndInit()
        val state                 = ChannelFutureState()
        val future: ChannelFuture = state.future
        channel.open(file, opts, attrs, future)
        state.suspend().asInstanceOf[Option[ChannelFutureState]]
    }

    final def inExecutor(): Boolean = {
        Thread.currentThread() match
            case thread: ActorThread => thread.currentRunningActor() == this
            case _                   => false
    }

    //// =================== USER API ====================

    // Event from Reactor

    /** Handle channel close event */
    protected def afterChannelCloseEvent(event: ReactorEvent.ChannelClose): Unit = {}

    /** Handle channel register result event */
    protected def afterChannelRegisterReplyEvent(event: ReactorEvent.RegisterReply): Unit = {}

    /** Handle channel deregister result event */
    protected def afterChannelDeregisterReplyEvent(event: ReactorEvent.DeregisterReply): Unit = {}

    /** Handle channel readiness event */
    protected def afterChannelReadinessEvent(event: ReactorEvent.ChannelReadiness): Unit = {}

    // Event from Timer

    // End handle event.

    /** Create a new [[Channel]] */
    protected def newChannel(): Channel =
        throw new NotImplementedError(getClass.getName + ".newChannel: an implementation is missing")

    @throws[Exception]
    protected def initChannel(channel: Channel): Unit = handler match
        case Some(h) => channel.pipeline.addLast(h)
        case None    =>

    protected def initFileChannel(channel: Channel): Unit = {}

    def handler: Option[ChannelInitializer[? <: Channel]] = None

}

object ChannelsActor {

    case class Bind(local: SocketAddress) extends Ask[BindReply]

    object Bind {

        def apply(port: Int): Bind = Bind(new InetSocketAddress(port))

        def apply(host: String, port: Int): Bind = Bind(new InetSocketAddress(host, port))

        def apply(host: InetAddress, port: Int): Bind = Bind(new InetSocketAddress(host, port))

    }

    case class BindReply(channelId: Int) extends Reply

}
