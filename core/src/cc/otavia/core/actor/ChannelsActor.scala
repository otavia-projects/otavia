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
            val stackYield = resumeChannelStack(stack)
            this.switchState(stack, stackYield)
            if (stackYield.completed) stack.internalChannel.processCompletedChannelStacks()
        } catch {
            case cause: Throwable =>
                cause.printStackTrace()
                stack.`throw`(cause) // completed stack with Exception
                stack.internalChannel.processCompletedChannelStacks()
        } finally currentStack = null
    }

    final override private[core] def receiveReactorEvent(event: ReactorEvent): Unit = {
        event match {
            case e: ReactorEvent.RegisterReply =>
                e.channel.asInstanceOf[AbstractChannel].handleChannelRegisterReplyEvent(e)
                afterChannelRegistered(e)
            case e: ReactorEvent.DeregisterReply =>
                e.channel.asInstanceOf[AbstractChannel].handleChannelDeregisterReplyEvent(e)
            case e: ReactorEvent.ChannelClose =>
                e.channel.asInstanceOf[AbstractChannel].handleChannelCloseEvent(e)
                afterChannelClosed(e.channel, e.cause)
            case e: ReactorEvent.ChannelReadiness =>
                e.channel.asInstanceOf[AbstractChannel].handleChannelReadinessEvent(e)
            case e: ReactorEvent.AcceptedEvent => e.channel.asInstanceOf[AbstractChannel].handleChannelAcceptedEvent(e)
            case e: ReactorEvent.ReadCompletedEvent =>
                e.channel.asInstanceOf[AbstractChannel].handleChannelReadCompletedEvent(e)
            case e: ReactorEvent.BindReply => e.channel.asInstanceOf[AbstractChannel].handleChannelBindReplyEvent(e)
            case e: ReactorEvent.ConnectReply =>
                e.channel.asInstanceOf[AbstractChannel].handleChannelConnectReplyEvent(e)
            case e: ReactorEvent.ReadBuffer => e.channel.asInstanceOf[AbstractChannel].handleChannelReadBufferEvent(e)
            case e: ReactorEvent.OpenReply  => e.channel.asInstanceOf[AbstractChannel].handleChannelOpenReplyEvent(e)
            case e: ReactorEvent.ShutdownReply =>
                e.channel.asInstanceOf[AbstractNetworkChannel].handleShutdownReply(e)
        }
    }

    final override private[core] def receiveChannelTimeoutEvent(event: ChannelTimeoutEvent): Unit = {
        val channel = event.channel.asInstanceOf[AbstractChannel]
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

    final protected def openFile(path: Path, opts: Seq[OpenOption], attrs: Seq[FileAttribute[?]]): StackState = {
        val channel               = createFileChannelAndInit()
        val state                 = ChannelFutureState()
        val future: ChannelFuture = state.future
        channel.open(path, opts, attrs, future)
        state
    }

    final protected def openFile(file: File, opts: Seq[OpenOption], attrs: Seq[FileAttribute[?]]): StackState = {
        val state                 = ChannelFutureState()
        val future: ChannelFuture = state.future
        val channel               = createFileChannelAndInit()
        channel.open(file, opts, attrs, future)
        state
    }

    final def inExecutor(): Boolean = Thread.currentThread() match
        case thread: ActorThread => thread.currentRunningActor() == this
        case _                   => false

    //// =================== USER API ====================

    protected def resumeChannelStack(stack: ChannelStack[AnyRef]): StackYield =
        throw new NotImplementedError(getClass.getName + ": an implementation is missing")

    // Event from Reactor

    /** Handle channel close event */
    protected def afterChannelClosed(channel: Channel, cause: Option[Throwable]): Unit = {}

    /** Handle channel register result event */
    protected def afterChannelRegistered(event: ReactorEvent.RegisterReply): Unit = {}

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

    case class Bind(local: SocketAddress) extends Ask[ChannelEstablished]

    object Bind {

        def apply(port: Int): Bind = Bind(new InetSocketAddress(port))

        def apply(host: String, port: Int): Bind = Bind(new InetSocketAddress(host, port))

        def apply(host: InetAddress, port: Int): Bind = Bind(new InetSocketAddress(host, port))

    }

    case class ChannelEstablished(channelId: Int) extends Reply

}
