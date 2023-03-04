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

package io.otavia.core.stack

import io.otavia.core.channel.Channel

import scala.language.unsafeNulls

class ChannelStack[T <: AnyRef] private () extends Stack {

    private var msg: AnyRef     = _
    private var belong: Channel = _

    def message: T                                = msg.asInstanceOf[T]
    private[core] def setMessage(m: AnyRef): Unit = msg = m

    def channel: Channel                           = belong
    private[core] def setChannel(c: Channel): Unit = belong = c

    override def recycle(): Unit = ChannelStack.stackPool.recycle(this)

    override protected def cleanInstance(): Unit = {
        super.cleanInstance()
        msg = null
        belong = null
    }

}

object ChannelStack {

    private val stackPool = new StackObjectPool[ChannelStack[?]] {
        override protected def newObject(): ChannelStack[?] = new ChannelStack[Nothing]()
    }

    def apply[T <: AnyRef](): ChannelStack[T] = stackPool.get().asInstanceOf[ChannelStack[T]]

}
