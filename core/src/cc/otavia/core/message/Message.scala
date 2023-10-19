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

import cc.otavia.core.actor.{AbstractActor, Actor}
import cc.otavia.core.address.Address
import cc.otavia.core.stack.Stack
import cc.otavia.core.util.Nextable

/** Message is base unit for actor community */
sealed trait Message extends Nextable with Serializable

/** Message which will generate [[Stack]] when a [[Actor]] received. */
sealed trait Call extends Message

/** message which do not need reply */
trait Notice extends Call

/** message which need reply */
trait Ask[R <: Reply] extends Call {

    private var address: Address[Call] = _
    private var sid: Long              = 0
    private var mid: Long              = 0

    def sender: Address[Call] = address

    def senderId: Long = sid

    def askId: Long = mid

    private[core] def setAskContext(sender: AbstractActor[?]): Unit = {
        // TODO: support AOP when sender is AopActor
        this.address = sender.self.asInstanceOf[Address[Call]]
        this.sid = sender.actorId
        this.mid = sender.generateSendMessageId()
    }

}

type ReplyOf[A <: Ask[? <: Reply]] <: Reply = A match
    case Ask[r] => r

/** reply message, it reply at least one ask message */
trait Reply extends Message {

    private var sid: Long = -1L
    private var rid: Long = -1L

    private var rids: Seq[(Long, Long)] = _
    private var batch: Boolean          = false
    private var bound: Boolean          = false

    def setReplyId(id: Long): Unit = { this.rid = id; batch = false; bound = true }

    def setReplyId(ids: Seq[(Long, Long)]): Unit = { this.rids = ids; batch = true; bound = true }

    def replyId: Long = if (batch) throw new RuntimeException("") else this.rid

    def replyIds: Seq[(Long, Long)] = { assert(batch, ""); this.rids }

    def isBatch: Boolean = batch

}