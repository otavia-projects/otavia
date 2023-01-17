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

package io.otavia.core.reactor

import io.otavia.core.address.ChannelsActorAddress
import io.otavia.core.channel.Channel

import java.nio.channels.SelectionKey
import java.util.Date
import java.util.concurrent.atomic.AtomicLong

/** Event for [[io.otavia.core.actor.Actor]] */
sealed abstract class Event {
    private[core] var next: Event = _
}

/** Timeout event */
case class TimeoutEvent(registerId: Long, attach: AnyRef | Null = null) extends Event

enum ReactorEvent extends Event {

    case RegisterReply(channel: Channel, cause: Option[Throwable])
    case DeregisterReply(channel: Channel, cause: Option[Throwable])

    case ChannelReadiness(channel: Channel, readyOps: Int)
    case ChannelClose(channel: Channel)

}

case class RegisterReplyEvent(channel: Channel, succeed: Boolean = true, cause: Throwable = null)   extends Event
case class DeregisterReplyEvent(channel: Channel, succeed: Boolean = true, cause: Throwable = null) extends Event

/** channel event for [[io.otavia.core.actor.ChannelsActor]]
 *
 *  @param channel
 *    event belong to this channel
 *  @param interest
 *    socket event type
 */
final case class ChannelEvent(channel: Channel, selectionKey: SelectionKey) extends Event

final case class ChannelClose(channel: Channel) extends Event
