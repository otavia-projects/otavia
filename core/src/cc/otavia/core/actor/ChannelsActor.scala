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

import cc.otavia.core.actor.ChannelsActor.*
import cc.otavia.core.address.ActorAddress
import cc.otavia.core.channel.*
import cc.otavia.core.message.*
import cc.otavia.core.reactor.*
import cc.otavia.core.stack.*
import cc.otavia.core.stack.helper.ChannelFutureState

import java.io.File
import java.net.*
import java.nio.file.attribute.FileAttribute
import java.nio.file.{OpenOption, Path}
import scala.language.unsafeNulls

/** IO-capable actor that manages [[Channel]] instances. Always fully drained during the event loop's IO phase
 *  (no time budget), ensuring IO responsiveness is never starved.
 *
 *  In addition to the standard message handling via [[resumeAsk]] / [[resumeNotice]], ChannelsActor provides
 *  [[resumeChannelStack]] for processing channel-level IO stacks, and lifecycle hooks like [[afterChannelClosed]] and
 *  [[afterChannelRegistered]] for channel events.
 *
 *  @tparam M
 *    the type of messages this actor can handle
 */
abstract class ChannelsActor[M <: Call] extends AbstractActor[M] with ChannelMessageSupport {

    private var channelCursor                  = 0
    private var currentChannelReceived: AnyRef = _

    override def self: ActorAddress[M] = super.self.asInstanceOf[ActorAddress[M]]

    /** Alias for [[self]] — the typed address of this IO-capable actor. */
    def address: ActorAddress[M] = self

    final def reactor: Reactor = system.reactor

    protected def family: ProtocolFamily = StandardProtocolFamily.INET

    private[core] def generateChannelId(): Int = { val channelId = channelCursor; channelCursor += 1; channelId }

    // =========================================================================
    // ChannelMessageSupport implementation
    // =========================================================================

    final override private[core] def receiveChannelMessage(stack: ChannelStack[?]): Unit = {
        currentChannelReceived = stack.message
        dispatchChannelStack(stack)
        currentChannelReceived = null
    }

    final override private[core] def dispatchChannelStack(stack: ChannelStack[?]): Unit = {
        currentStack = stack
        try {
            val stackYield = resumeChannelStack(stack)
            this.switchState(stack, stackYield)
            if (stackYield.completed) stack.internalChannel.processCompletedChannelStacks()
        } catch {
            case cause: Throwable =>
                logger.error(s"Unhandled exception in channel stack for actor [${getClass.getName}]", cause)
                stack.`throw`(cause)
                stack.internalChannel.processCompletedChannelStacks()
        } finally currentStack = null
    }

    final override private[core] def receiveReactorEvent(event: ReactorEvent): Unit = {
        event match {
            case e: RegisterReply =>
                e.channel.asInstanceOf[AbstractChannel].handleChannelRegisterReply(e.active, e.cause)
                afterChannelRegistered(e)
            case e: DeregisterReply =>
                e.channel
                    .asInstanceOf[AbstractChannel]
                    .handleChannelDeregisterReply(e.firstInactive, e.isOpen, e.cause)
            case e: ChannelClose =>
                e.channel.asInstanceOf[AbstractChannel].handleChannelClose(e.cause)
                afterChannelClosed(e.channel, e.cause)
            case e: AcceptedEvent => e.channel.asInstanceOf[AbstractChannel].handleChannelAcceptedEvent(e)
            case e: ReadCompletedEvent =>
                e.channel.asInstanceOf[AbstractChannel].handleChannelReadCompleted(e.cause)
            case e: BindReply =>
                e.channel.asInstanceOf[AbstractChannel].handleChannelBindReply(e.firstActive, e.cause)
            case e: ConnectReply =>
                e.channel.asInstanceOf[AbstractChannel].handleChannelConnectReply(e.firstActive, e.cause)
            case e: ReadBuffer =>
                e.channel
                    .asInstanceOf[AbstractChannel]
                    .handleChannelReadBuffer(e.buffer, e.sender, e.recipient, e.cause)
            case e: OpenReply => e.channel.asInstanceOf[AbstractChannel].handleChannelOpenReply(e.cause)
            case e: ShutdownReply =>
                e.channel.asInstanceOf[AbstractNetworkChannel].handleShutdownReply(e)
            case _ => // DisconnectReply, ReadEvent, and EMPTY_EVENT have no handler at this level
        }
    }

    final override private[core] def receiveChannelTimeoutEvent(event: ChannelTimeoutEvent): Unit = {
        val channel = event.channel.asInstanceOf[AbstractChannel]
        channel.handleChannelTimeoutEvent(event.registerId)
    }

    // =========================================================================
    // Channel lifecycle
    // =========================================================================

    /** Create a new [[Channel]] and mount it to this actor, then initialize its pipeline via [[initChannel]]. */
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

    /** Create a new file [[Channel]] and mount it to this actor, then initialize via [[initFileChannel]]. */
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

    // =========================================================================
    // User-overridable hooks
    // =========================================================================

    /** Process an inbound channel message. Override this to handle decoded IO data from the channel pipeline. */
    protected def resumeChannelStack(stack: ChannelStack[AnyRef]): StackYield =
        throw new NotImplementedError(getClass.getName + ": an implementation is missing")

    /** Called when a channel managed by this actor has been closed. */
    protected def afterChannelClosed(channel: Channel, cause: Option[Throwable]): Unit = {}

    /** Called when a channel has completed registration with the IO reactor. */
    protected def afterChannelRegistered(event: RegisterReply): Unit = {}

    /** Factory method to create a new [[Channel]] instance. Override to specify the channel type. */
    protected def newChannel(): Channel =
        throw new NotImplementedError(getClass.getName + ".newChannel: an implementation is missing")

    /** Initialize the channel's pipeline with handlers. Default implementation adds the handler from
     *  [[handler]] if present.
     */
    @throws[Exception]
    protected def initChannel(channel: Channel): Unit = handler match
        case Some(h) => channel.pipeline.addLast(h)
        case None    =>

    /** Initialize a file channel's pipeline. Default is no-op. */
    protected def initFileChannel(channel: Channel): Unit = {}

    /** Optional [[ChannelInitializer]] for pipeline setup. Override to provide a default handler. */
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
