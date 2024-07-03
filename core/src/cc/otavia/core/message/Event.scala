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

package cc.otavia.core.message

import cc.otavia.buffer.pool.RecyclablePageBuffer
import cc.otavia.core.cache.ResourceTimer
import cc.otavia.core.channel.{Channel, ChannelShutdownDirection}
import cc.otavia.core.util.Nextable

import java.net.SocketAddress

/** Event for [[cc.otavia.core.actor.Actor]] */
sealed abstract class Event extends Nextable

sealed abstract class TimerEvent extends Event {
    def registerId: Long
}

/** Timeout event */
case class TimeoutEvent(registerId: Long, attach: Option[AnyRef] = None) extends TimerEvent

case class ChannelTimeoutEvent(registerId: Long, channel: Channel) extends TimerEvent

case class AskTimeoutEvent(registerId: Long, askId: Long) extends TimerEvent

case class ResourceTimeoutEvent(registerId: Long, cache: ResourceTimer) extends TimerEvent

/** channel event for [[cc.otavia.core.actor.ChannelsActor]] */
sealed abstract class ReactorEvent extends Event

// event for
case class RegisterReply(channel: Channel, active: Boolean = false, cause: Option[Throwable] = None)
    extends ReactorEvent

case class DeregisterReply(
    channel: Channel,
    firstInactive: Boolean = false,
    isOpen: Boolean = false,
    cause: Option[Throwable] = None
) extends ReactorEvent

case class BindReply(channel: Channel, firstActive: Boolean = false, cause: Option[Throwable] = None)
    extends ReactorEvent

case class OpenReply(channel: Channel, cause: Option[Throwable] = None) extends ReactorEvent

case class ConnectReply(channel: Channel, firstActive: Boolean = false, cause: Option[Throwable] = None)
    extends ReactorEvent

case class DisconnectReply(channel: Channel, cause: Option[Throwable] = None) extends ReactorEvent

case class ShutdownReply(channel: Channel, direction: ChannelShutdownDirection, cause: Option[Throwable] = None)
    extends ReactorEvent

case class ChannelClose(channel: Channel, cause: Option[Throwable] = None) extends ReactorEvent

object EMPTY_EVENT extends ReactorEvent

case class ReadEvent(channel: Channel, cause: Option[Throwable] = None) extends ReactorEvent

case class ReadCompletedEvent(channel: Channel, cause: Option[Throwable] = None) extends ReactorEvent

case class AcceptedEvent(channel: Channel, accepted: Channel) extends ReactorEvent

case class ReadBuffer(
    channel: Channel,
    buffer: RecyclablePageBuffer,
    sender: Option[SocketAddress] = None,
    recipient: SocketAddress,
    cause: Option[Throwable] = None
) extends ReactorEvent
