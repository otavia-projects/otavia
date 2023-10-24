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

package cc.otavia.core.stack

import cc.otavia.core.channel.inflight.QueueMapEntity
import cc.otavia.core.channel.{AbstractChannel, Channel, ChannelAddress}

import scala.language.unsafeNulls

class ChannelStack[+T <: AnyRef] private () extends Stack with QueueMapEntity {

    private var msg: AnyRef             = _
    private var belong: AbstractChannel = _
    private var msgId: Long             = -1

    private var barrier: Boolean = false

    private var done: Boolean = false

    private var ret: AnyRef        = _
    private var noticeRet: Boolean = false

    def message: T                                         = msg.asInstanceOf[T]
    private[core] def setMessage(m: AnyRef): Unit          = msg = m
    def channel: ChannelAddress                            = belong
    private[core] def internalChannel: AbstractChannel     = belong
    private[core] def setChannel(c: AbstractChannel): Unit = belong = c

    def messageId: Long              = msgId
    def setMessageId(id: Long): Unit = msgId = id
    override def entityId: Long      = msgId

    override def isBarrier: Boolean  = barrier
    def setBarrier(b: Boolean): Unit = barrier = b

    override def recycle(): Unit = ChannelStack.stackPool.recycle(this)

    override protected def cleanInstance(): Unit = {
        msg = null
        belong = null
        msgId = -1
        barrier = false
        done = false
        ret = null
        noticeRet = false
        super.cleanInstance()
    }

    def isDone: Boolean = done

    def `return`(ret: AnyRef): None.type = {
        this.ret = ret
        done = true
        None
    }

    def `return`(): None.type = {
        this.noticeRet = true
        done = true
        None
    }

    private[core] def hasResult: Boolean = ret != null

    private[core] def result: AnyRef = ret

}

object ChannelStack {

    private val stackPool = new StackObjectPool[ChannelStack[AnyRef]] {
        override protected def newObject(): ChannelStack[AnyRef] = new ChannelStack()
    }

    def apply(channel: AbstractChannel, msg: AnyRef, msgId: Long): ChannelStack[AnyRef] = {
        val stack = stackPool.get()
        stack.setChannel(channel)
        stack.setMessage(msg)
        stack.setMessageId(msgId)
        stack
    }

}
