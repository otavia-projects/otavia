/*
 * Copyright 2022 Yan Kun
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

package io.otavia.core.address

import io.otavia.core.actor.NormalActor
import io.otavia.core.house.ChannelsActorHouse
import io.otavia.core.message.{Ask, Message, Notice, Reply}
import io.otavia.core.reactor.Event
import io.otavia.core.stack.ReplyWaiter

/** channel group physical address
 *
 *  @param house
 *  @tparam M
 *    the message type that this channel group can receive.
 */
class ChannelsActorAddress[M <: Ask[?] | Notice](override private[core] val house: ChannelsActorHouse)
    extends PhysicalAddress[M, ChannelsActorHouse] {}
