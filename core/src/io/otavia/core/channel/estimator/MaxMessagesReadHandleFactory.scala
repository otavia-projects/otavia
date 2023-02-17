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

package io.otavia.core.channel.estimator

import io.otavia.core.channel.Channel

/** Base implementation of [[ReadHandleFactory]] which allows to limit the number of messages read per read loop. */
abstract class MaxMessagesReadHandleFactory(private val maxMessagesPerRead: Int) extends ReadHandleFactory {

    def this() = this(1)

    assert(maxMessagesPerRead > 0)

    override def newHandle(channel: Channel): ReadHandleFactory.ReadHandle = newMaxMessageHandle(maxMessagesPerRead)

    /** Creates a new [[MaxMessageReadHandle]] to use.
     *
     *  @param maxMessagesPerRead
     *    the maximum number of messages to read per read loop.
     *  @return
     *    the handle.
     */
    protected def newMaxMessageHandle(maxMessagesPerRead: Int): MaxMessagesReadHandleFactory.MaxMessageReadHandle

}

object MaxMessagesReadHandleFactory {

    /** Focuses on enforcing the maximum messages per read condition for [[lastRead]] .
     *  @param maxMessagesPerRead
     *    the maximum number of messages to read per read loop.
     */
    private[channel] abstract class MaxMessageReadHandle(val maxMessagesPerRead: Int)
        extends ReadHandleFactory.ReadHandle {

        assert(maxMessagesPerRead > 0)
        private var totalMessages: Int = 0

        override def lastRead(attemptedBytesRead: Int, actualBytesRead: Int, numMessagesRead: Int): Boolean = {
            if (numMessagesRead > 0) totalMessages += numMessagesRead
            totalMessages < maxMessagesPerRead
        }

        override def readComplete(): Unit = totalMessages = 0

    }
}
