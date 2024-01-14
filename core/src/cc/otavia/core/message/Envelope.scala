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

import cc.otavia.core.address.Address
import cc.otavia.core.util.Nextable

import scala.language.unsafeNulls

class Envelope[M <: Message] extends Nextable {

    // sender message
    private var address: Address[Call] = _

    // payload
    private var mid: Long = 0
    private var msg: M    = _

    // reply info
    private var rid: Long         = 0 // reply id if is
    private var rids: Array[Long] = _

    private[core] def setSender(address: Address[Call]): Unit = this.address = address

    private[core] def setMessageId(id: Long): Unit = this.mid = id

    private[core] def setContent(msg: M): Unit = this.msg = msg

    private[core] def setReplyId(id: Long): Unit = this.rid = id

    private[core] def setReplyIds(ids: Array[Long]): Unit = this.rids = ids

    def sender: Address[Call] = address

    def content: Message = msg

    def message: M = msg

    def messageId: Long = mid

    private[core] def replyId: Long = rid

    private[core] def replyIds: Array[Long] = rids

    private[core] def isBatchReply: Boolean = rids != null

}

object Envelope {
    def apply[M <: Message](): Envelope[M] = new Envelope()
}
