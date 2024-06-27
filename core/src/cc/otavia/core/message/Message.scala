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

package cc.otavia.core.message

import cc.otavia.core.actor.Actor
import cc.otavia.core.stack.Stack
import cc.otavia.core.util.Nextable

/** Message is base unit for actor community */
sealed trait Message extends Serializable

/** Message which will generate [[Stack]] when a [[Actor]] received. */
sealed trait Call extends Message

/** message which do not need reply */
trait Notice extends Call

/** message which need reply */
trait Ask[R <: Reply] extends Call

type ReplyOf[A <: Ask[? <: Reply]] <: Reply = A match
    case Ask[r] => r

/** reply message, it reply at least one ask message */
trait Reply extends Message

case class TimeoutReply() extends Reply
