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

package io.otavia.core.message

import io.otavia.core.cache.ResourceTimer
import io.otavia.core.channel.Channel
import io.otavia.core.message.{Event, TimerEvent}
import io.otavia.core.stack.BlockPromise
import io.otavia.core.util.Nextable

/** Event for [[io.otavia.core.actor.Actor]] */
sealed abstract class Event extends Nextable

sealed abstract class TimerEvent extends Event {
    def registerId: Long
}

/** Timeout event */
case class TimeoutEvent(registerId: Long, attach: Option[AnyRef] = None) extends TimerEvent

case class ChannelTimeoutEvent(registerId: Long, channel: Channel) extends TimerEvent

case class AskTimeoutEvent(registerId: Long, askId: Long) extends TimerEvent

case class ResourceTimeoutEvent(registerId: Long, cache: ResourceTimer) extends TimerEvent

/** channel event for [[io.otavia.core.actor.ChannelsActor]] */
enum ReactorEvent extends Event {

    // event for
    case RegisterReply(channel: Channel, cause: Option[Throwable] = None)
    case DeregisterReply(channel: Channel, firstInactive: Boolean = false, cause: Option[Throwable] = None)

    case BindReply(channel: Channel, firstActive: Boolean = false, cause: Option[Throwable] = None)

    case OpenReply(channel: Channel, cause: Option[Throwable] = None)

    case ConnectReply(channel: Channel, firstActive: Boolean = false, cause: Option[Throwable] = None)

    case DisconnectReply(channel: Channel, cause: Option[Throwable] = None)

    case ShutdownReply(channel: Channel, cause: Option[Throwable] = None)

    case ChannelReadiness(channel: Channel, readyOps: Int)
    case ChannelClose(channel: Channel, cause: Option[Throwable] = None)

    case EMPTY_EVENT

    case AcceptedEvent(channel: Channel, accepted: Channel)

    case ReadCompletedEvent(channel: Channel)

}

case class BlockFutureCompletedEvent(promise: BlockPromise[?]) extends Event
