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

import cc.otavia.core.actor.Actor.{ASK_TYPE, MessageType, NOTICE_TYPE, REPLY_TYPE}
import cc.otavia.core.address.{ActorAddress, Address}
import cc.otavia.core.message.*
import cc.otavia.core.stack.*

import scala.reflect.ClassTag

abstract class StateActor[M <: Call] extends AbstractActor[M] {

    /** self address of this actor instance
     *
     *  @return
     *    self address
     */
    override def self: ActorAddress[M] = super.self.asInstanceOf[ActorAddress[M]]

    final override private[core] def dispatchChannelStack(stack: ChannelStack[?]): Unit =
        throw new UnsupportedOperationException("StateActor not support ChannelStack")

}
