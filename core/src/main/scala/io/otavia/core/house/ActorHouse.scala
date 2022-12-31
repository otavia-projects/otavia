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

package io.otavia.core.house

import io.otavia.core.actor.NormalActor
import io.otavia.core.message.{Ask, Message, Notice, Reply}

/** House is [[io.otavia.core.actor.NormalActor]] instance mount point. when a actor is creating by actor system, a
 *  house is creating at the same time, and mount the actor instance to the house instance.
 *
 *  @tparam M
 *    the message type of the mounted actor instance can handle
 */
private[core] class ActorHouse extends House {

    def setActor(actor: NormalActor[?]): Unit = dweller = actor

    def actor: NormalActor[?] = this.dweller.asInstanceOf[NormalActor[?]]

    override def putNotice(notice: Notice): Unit = {}

    override def putAsk(ask: Ask[?]): Unit = {}

    override def putReply(reply: Reply): Unit = {}

}
