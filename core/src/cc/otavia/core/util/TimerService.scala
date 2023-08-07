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

package cc.otavia.core.util

import cc.otavia.core.actor.ChannelsActor
import cc.otavia.core.channel.Channel
import cc.otavia.core.message.{Ask, IdAllocator, Reply}
import cc.otavia.core.stack.{AskFrame, ChannelFrame, StackState}

import java.util.Date

abstract class TimerService extends ChannelsActor[TimerService.MSG] {

//  def continueAsk(state: TimerService.MSG & Ask | AskFrame): Option[StackState] = None

//    override private[core] def receiveRegisterReply(registerReplyEvent: RegisterReplyEvent): Unit = ???

//    override def continueChannelMessage(msg: AnyRef | ChannelFrame): Option[StackState] = ???

    override def init(channel: Channel): Unit = ???


}

object TimerService {

    type MSG = FixTime | Delay | DelayPeriod | FirstTimePeriod

    final case class FixTime(date: Date) extends Ask[TimeArrival]

    final case class Delay(delay: Long) extends Ask[TimeArrival]

    final case class DelayPeriod(delay: Long, period: Long) extends Ask[TimeArrival]

    final case class FirstTimePeriod(firstTime: Date, period: Long) extends Ask[TimeArrival]

    final case class TimeArrival() extends Reply

}