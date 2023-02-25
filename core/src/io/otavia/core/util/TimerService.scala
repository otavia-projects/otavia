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

package io.otavia.core.util

import io.otavia.core.actor.ChannelsActor
import io.otavia.core.channel.Channel
import io.otavia.core.message.{Ask, IdAllocator, Reply}
import io.otavia.core.stack.{AskFrame, ChannelFrame, StackState}

import java.util.Date

abstract class TimerService extends ChannelsActor[TimerService.MSG] {

//  def continueAsk(state: TimerService.MSG & Ask | AskFrame): Option[StackState] = None

//    override private[core] def receiveRegisterReply(registerReplyEvent: RegisterReplyEvent): Unit = ???

    override def continueChannelMessage(msg: AnyRef | ChannelFrame): Option[StackState] = ???

    override def init(channel: Channel): Unit = ???

    override def close(): Unit = ???

}

object TimerService {

    type MSG = FixTime | Delay | DelayPeriod | FirstTimePeriod

    final case class FixTime(date: Date)(using IdAllocator) extends Ask[TimeArrival]

    final case class Delay(delay: Long)(using IdAllocator) extends Ask[TimeArrival]

    final case class DelayPeriod(delay: Long, period: Long)(using IdAllocator) extends Ask[TimeArrival]

    final case class FirstTimePeriod(firstTime: Date, period: Long)(using IdAllocator) extends Ask[TimeArrival]

    final case class TimeArrival()(using IdAllocator) extends Reply

}
