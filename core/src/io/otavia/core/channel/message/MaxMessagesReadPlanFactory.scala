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

package io.otavia.core.channel.message

import io.otavia.core.channel.{Channel, ServerChannel}

/** Base implementation of [[ReadPlanFactory]] which allows to limit the number of messages read per read loop. */
abstract class MaxMessagesReadPlanFactory(private val maxMessagesPerRead: Int) extends ReadPlanFactory {

    def this() = this(1)

    assert(maxMessagesPerRead > 0)

    override def newPlan(channel: Channel): ReadPlan = newMaxMessagePlan(maxMessagesPerRead)

    /** Creates a new [[MaxMessageReadHandle]] to use.
     *
     *  @param maxMessagesPerRead
     *    the maximum number of messages to read per read loop.
     *  @return
     *    the handle.
     */
    protected def newMaxMessagePlan(maxMessagesPerRead: Int): MaxMessagesReadPlanFactory.MaxMessageReadPlan

}

object MaxMessagesReadPlanFactory {

    /** Focuses on enforcing the maximum messages per read condition for [[lastRead]] .
     *
     *  @param maxMessagesPerRead
     *    the maximum number of messages to read per read loop.
     */
    abstract class MaxMessageReadPlan(val maxMessagesPerRead: Int) extends ReadPlan {

        assert(maxMessagesPerRead > 0)
        private var totalMessages: Int = 0

        override def lastRead(attemptedBytesRead: Int, actualBytesRead: Int, numMessagesRead: Int): Boolean = {
            if (numMessagesRead > 0) totalMessages += numMessagesRead
            totalMessages < maxMessagesPerRead
        }

        override def readComplete(): Unit = totalMessages = 0

    }

    /** [[MaxMessagesReadPlanFactory]] implementation which should be used for[[ServerChannel]]s. */
    class ServerChannelReadPlanFactory(maxMessagesPerRead: Int = 16)
        extends MaxMessagesReadPlanFactory(maxMessagesPerRead) {
        override protected def newMaxMessagePlan(maxMessagesPerRead: Int): MaxMessageReadPlan =
            new MaxMessageReadPlan(maxMessagesPerRead) {

                override def estimatedNextSize: Int = 128

            }
    }

}
