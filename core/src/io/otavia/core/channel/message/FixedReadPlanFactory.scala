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

import io.otavia.core.channel.message.FixedReadPlanFactory.FixedReadPlan

/** The [[ReadPlanFactory]] that always yields the same buffer size prediction. This handle ignores the feedback from
 *  the I/O thread.
 *
 *  @param bufferSize
 *    size for each read.
 *  @param maxMessagesPerRead
 *    max message per read.
 */
class FixedReadPlanFactory(val bufferSize: Int, maxMessagesPerRead: Int = Int.MaxValue)
    extends MaxMessagesReadPlanFactory(maxMessagesPerRead) {
    override protected def newMaxMessagePlan(maxMessagesPerRead: Int): FixedReadPlan =
        new FixedReadPlan(maxMessagesPerRead, bufferSize)
}

object FixedReadPlanFactory {
    class FixedReadPlan(maxMessagesPerRead: Int, bufferSize: Int)
        extends MaxMessagesReadPlanFactory.MaxMessageReadPlan(maxMessagesPerRead) {
        override def estimatedNextSize: Int = bufferSize
    }
}
