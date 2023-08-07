/*
 * Copyright 2022 Yan Kun <yan_kun_1992@foxmail.com>
 *
 * This file fork from netty.
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

package cc.otavia.core.channel.estimator

import cc.otavia.core.channel.Channel
import cc.otavia.core.channel.estimator.MaxMessagesWriteHandleFactory.MaxMessagesWriteHandle
import cc.otavia.core.channel.estimator.WriteHandleFactory

class MaxMessagesWriteHandleFactory(private val maxMessagesPerWrite: Int) extends WriteHandleFactory {

    assert(maxMessagesPerWrite > 0)

    override def newHandle(channel: Channel): WriteHandleFactory.WriteHandle = newHandle(channel, maxMessagesPerWrite)
    protected def newHandle(channel: Channel, maxMessagesPerWrite: Int): MaxMessagesWriteHandle =
        new MaxMessagesWriteHandle(maxMessagesPerWrite)

}

object MaxMessagesWriteHandleFactory {
    private[core] class MaxMessagesWriteHandle(private val maxMessagesPerWrite: Int)
        extends WriteHandleFactory.WriteHandle {

        assert(maxMessagesPerWrite > 0)

        private var totalNumMessages = 0

        override def lastWrite(attemptedBytesWrite: Long, actualBytesWrite: Long, numMessagesWrite: Int): Boolean = {
            if (numMessagesWrite > 0) this.totalNumMessages += numMessagesWrite
            totalNumMessages <= maxMessagesPerWrite
        }

        override def writeComplete(): Unit = this.totalNumMessages = 0

    }
}
