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

import io.otavia.core.channel.Channel

/** Event for [[io.otavia.core.actor.Actor]] */
sealed abstract class Event {
    private[core] var next: Event = _
}

/** Timeout event */
case class TimeoutEvent(registerId: Long, attach: AnyRef | Null = null) extends Event

sealed abstract class TimerEvent {
    def registerId: Long

}

case class ChannelTimeout(registerId: Long, channel: Channel) extends TimerEvent

private[core] case class CacheTimeout(registerId: Long) extends TimerEvent

/** channel event for [[io.otavia.core.actor.ChannelsActor]] */
enum ReactorEvent extends Event {

    // event for
    case RegisterReply(channel: Channel, cause: Option[Throwable] = None)
    case DeregisterReply(channel: Channel, cause: Option[Throwable] = None)

    case ChannelReadiness(channel: Channel, readyOps: Int)
    case ChannelClose(channel: Channel)

}
