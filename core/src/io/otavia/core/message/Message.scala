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

package io.otavia.core.message

import io.otavia.core.address.Address

sealed trait MessageEnvelope {

    private var s: Address[Ask[?] | Notice] | Null = null
    private var sid: Long                          = ???
    private var id: Long                           = ???

    private var next: MessageEnvelope | Null = null

    def sender: Address[Ask[?] | Notice] = s.nn
    def senderId: Long                   = sid
    def messageId: Long                  = id

}

/** Message is base unit for actor community */
sealed trait Message(using distributor: IdAllocator) extends Serializable { // TODO: use MessageEnvelope

    final val sender: Address[Ask[?] | Notice] = distributor.sender
    final val senderId: Long                   = distributor.actorId
    final val id: Long                         = distributor.generate

    @volatile private[core] var next: Message = _

}

/** message which do not need reply */
trait Notice extends Message

/** message which need reply */
trait Ask[R <: Reply] extends Message { // + for R ?

    // TODO: handle reply message which not create by current actor
    def reply(rep: R): None.type                             = { rep.setReplyId(id); sender.reply(rep); None }
    private[core] def replyInternal(reply: Reply): None.type = this.reply(reply.asInstanceOf[R])

    def throws(reply: ExceptionMessage): None.type = { reply.setReplyId(id); sender.reply(reply); None }

}

type ReplyOf[A <: Ask[_ <: Reply]] <: Reply = A match
    case Ask[r] => r

/** reply message, it reply at least one ask message */
trait Reply extends Message {

    private[core] var replyId: Long = -1L

    private[core] var replyIds: Seq[Long] = _
    private var isBatchReply: Boolean     = false

    def setReplyId(id: Long): Unit = { this.replyId = id; isBatchReply = false }

    def setReplyId(ids: Seq[Long]): Unit = { this.replyIds = ids; isBatchReply = true }

    def getReplyId: Long = if (isBatchReply) throw new RuntimeException("") else this.replyId

    def getReplyIds: Seq[Long] = { assert(isBatchReply, ""); this.replyIds }

    def isBatch: Boolean = isBatchReply

}

final case class UnitReply()(using IdAllocator) extends Reply
