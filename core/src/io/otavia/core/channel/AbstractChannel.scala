/*
 * Copyright 2022 Yan Kun <yan_kun_1992@foxmail.com>
 *
 * This file fork from netty.
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

package io.otavia.core.channel

import io.netty5.util.DefaultAttributeMap
import io.otavia.core.actor.ChannelsActor
import io.otavia.core.buffer.AdaptiveBuffer
import io.otavia.core.log4a.ActorLogger
import io.otavia.core.system.ActorThread

/** Abstract class of file channel and network channel. */
abstract class AbstractChannel extends DefaultAttributeMap, Channel, ChannelState {

    protected var logger: ActorLogger = _

    private var channelId: Int = -1

    private var actor: ChannelsActor[?] | Null = _

    override def id: Int = channelId

    override def executor: ChannelsActor[?] = actor match
        case a: ChannelsActor[?] => a
        case null =>
            throw new IllegalStateException(s"The channel $this is not mounted, use mount to mount channel.")

    final private[core] def mount(channelsActor: ChannelsActor[?]): Unit = {
        assert(!mounted, s"The channel $this has been mounted already, you can't mount it twice!")
        actor = channelsActor
        logger = ActorLogger.getLogger(getClass)(using executor)
        channelId = executor.generateChannelId()
        //        resetInboundBuffer()
        mounted = true
    }

    override val pipeline: ChannelPipeline = newChannelPipeline()

    /** Returns a new [[ChannelPipeline]] instance. */
    private def newChannelPipeline(): ChannelPipeline = new OtaviaChannelPipeline(this)

    // read socket data to this buffer
    protected def channelInboundAdaptiveBuffer: AdaptiveBuffer = pipeline.channelInboundBuffer

    // write data to socket from this buffer
    protected def channelOutboundAdaptiveBuffer: AdaptiveBuffer = pipeline.channelOutboundBuffer

    protected def currentThread: ActorThread = Thread.currentThread().asInstanceOf[ActorThread]

    private def laterTasks = currentThread.laterTasks

    // This method is used by outbound operation implementations to trigger an inbound event later.
    // They do not trigger an inbound event immediately because an outbound operation might have been
    // triggered by another inbound event handler method.  If fired immediately, the call stack
    // will look like this for example:
    //
    //   handlerA.inboundBufferUpdated() - (1) an inbound handler method closes a connection.
    //   -> handlerA.ctx.close()
    //      -> channel.closeTransport()
    //         -> handlerA.channelInactive() - (2) another inbound handler method called while in (1) yet
    //
    // which means the execution of two inbound handler methods of the same handler overlap undesirably.
    protected def invokeLater(task: Runnable): Unit = laterTasks.append(task)

}
