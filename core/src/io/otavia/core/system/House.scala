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

package io.otavia.core.system

import io.otavia.core.actor.Actor
import io.otavia.core.message.{Ask, Event, Message, Notice, Reply}

/** House is [[Actor]] instance mount point. when a actor is creating by actor system, a house is creating at the same
 *  time, and mount the actor instance to the house instance.
 *
 *  @tparam M
 *    the message type of the mounted actor instance can handle
 */
private[core] trait House {

    def putNotice(notice: Notice): Unit

    def putAsk(ask: Ask[?]): Unit

    def putReply(reply: Reply): Unit

    def putEvent(event: Event): Unit

}

object House {
    
}