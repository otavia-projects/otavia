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

import cc.otavia.core.address.Address
import cc.otavia.core.message.Message
import cc.otavia.core.system.ActorSystem

/** Runtime context injected into each [[Actor]] during mounting. Accessed via [[Actor.context]].
  *
  * Provides access to system-level services and actor identity metadata.
  */
trait ActorContext {

    /** The [[ActorSystem]] this actor belongs to. */
    def system: ActorSystem

    /** The physical address of this actor. Use [[Actor.self]] for the typed variant. */
    def address: Address[? <: Message]

    /** System-assigned unique ID for this actor instance. */
    def actorId: Long

    /** Whether this actor uses round-robin load balancing across multiple threads.
     *  When true, messages sent from co-located actors are routed to the same-thread instance for data locality.
     */
    def isLoadBalance: Boolean

    /** Index of the [[cc.otavia.core.system.ActorThread]] this actor is pinned to. */
    def mountedThreadId: Int

}
