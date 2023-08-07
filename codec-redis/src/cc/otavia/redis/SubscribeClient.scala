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

package cc.otavia.redis

import cc.otavia.core.actor.ChannelsActor
import cc.otavia.core.channel.Channel
import cc.otavia.core.message.{Ask, IdAllocator, Notice, Reply}
import cc.otavia.core.stack.{ChannelFrame, StackState}

//class SubscribeClient extends ChannelsActor[SubscribeClient.MSG] {
//  override def continueChannelMessage(msg: AnyRef | ChannelFrame): Option[StackState] = ???
//
//  override def init(channel: Channel): Unit = ???
//
//  override def close(): Unit = ???
//}
//
//object SubscribeClient {
//  type MSG = Client.Connect | Subscribe
//
//  final case class Subscribe(channel: String) extends Ask[SubscribeOk]
//  final case class SubscribeOk()              extends Reply
//}
