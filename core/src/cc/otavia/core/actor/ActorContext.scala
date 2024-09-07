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

/** [[Actor]] content info, the content info is created by actor system when a [[Actor]] instance is creating by actor
 *  system, and inject to [[Actor]] instance by [[Actor.setCtx]]
 */
trait ActorContext {

    /** actor system */
    def system: ActorSystem

    /** physical address of the binding actor. */
    def address: Address[? <: Message]

    /** id distributed by actor system */
    def actorId: Long

    def isLoadBalance: Boolean

    def mountedThreadId: Int

}
