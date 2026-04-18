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

package cc.otavia.core.actor

import cc.otavia.core.address.ActorAddress
import cc.otavia.core.message.Call

/** Pure business logic actor with no IO capabilities. Scheduled with a time budget alongside other StateActors,
 *  ensuring fair scheduling among many business actor.
 *
 *  Override [[resumeAsk]] and/or [[resumeNotice]] to handle messages. Do NOT use this actor for direct network IO —
 *  use [[ChannelsActor]] or its subclasses instead.
 *
 *  @tparam M
 *    the type of messages this actor can handle
 */
abstract class StateActor[M <: Call] extends AbstractActor[M] {

    /** Typed self-address for this StateActor. */
    override def self: ActorAddress[M] = super.self.asInstanceOf[ActorAddress[M]]

}
