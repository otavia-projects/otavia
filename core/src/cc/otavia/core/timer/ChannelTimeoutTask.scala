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

package cc.otavia.core.timer

import cc.otavia.core.channel.Channel
import cc.otavia.core.message.TimerEvent
import cc.otavia.core.channel.Channel
import cc.otavia.core.message.{ChannelTimeoutEvent, TimerEvent}

import scala.language.unsafeNulls

private[timer] class ChannelTimeoutTask(manager: TimerTaskManager) extends TimeoutTask(manager) {

    private var channel: Channel = _

    def setChannel(ch: Channel): Unit = channel = ch

    override protected def newEvent(): TimerEvent = ChannelTimeoutEvent(registerId, channel)

}
