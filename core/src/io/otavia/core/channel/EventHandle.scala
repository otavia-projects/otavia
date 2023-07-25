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

package io.otavia.core.channel

import io.otavia.core.message.{Event, ReactorEvent, TimeoutEvent}
import io.otavia.core.reactor.Reactor
import io.otavia.core.timer.Timer

/** A handle that will process [[Event]] from [[Reactor]] and [[Timer]]. */
trait EventHandle {
    this: Channel =>

    // Event from Reactor
    /** Handle channel close event */
    private[core] def handleChannelCloseEvent(event: ReactorEvent.ChannelClose): Unit

    /** Handle channel register result event */
    private[core] def handleChannelRegisterReplyEvent(event: ReactorEvent.RegisterReply): Unit

    /** Handle channel deregister result event */
    private[core] def handleChannelDeregisterReplyEvent(event: ReactorEvent.DeregisterReply): Unit

    /** Handle channel readiness event */
    private[core] def handleChannelReadinessEvent(event: ReactorEvent.ChannelReadiness): Unit

    private[core] def handleChannelBindReplyEvent(event: ReactorEvent.BindReply): Unit

    private[core] def handleChannelConnectReplyEvent(event: ReactorEvent.ConnectReply): Unit

    private[core] def handleChannelDisconnectReplyEvent(event: ReactorEvent.DisconnectReply): Unit

    private[core] def handleChannelOpenReplyEvent(event: ReactorEvent.OpenReply): Unit

    // Event from Timer

    /** Handle channel timeout event */
    private[core] def handleChannelTimeoutEvent(eventRegisterId: Long): Unit

    private[core] def handleChannelAcceptedEvent(event: ReactorEvent.AcceptedEvent): Unit

    private[core] def handleChannelReadCompletedEvent(event: ReactorEvent.ReadCompletedEvent): Unit

}
