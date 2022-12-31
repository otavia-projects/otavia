/*
 * Copyright 2022 Yan Kun
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

package io.otavia.core.channel

import io.otavia.core.channel.FixedReadHandleFactory.ReadHandleImpl

/** The [[ReadHandleFactory]] that always yields the same buffer size prediction. This handle ignores the feedback from
 *  the I/O thread.
 */
class FixedReadHandleFactory(val bufferSize: Int, maxMessagesPerRead: Int = 1)
    extends MaxMessagesReadHandleFactory(maxMessagesPerRead) {
    override protected def newMaxMessageHandle(
        maxMessagesPerRead: Int
    ): MaxMessagesReadHandleFactory.MaxMessageReadHandle = new ReadHandleImpl(maxMessagesPerRead, bufferSize)
}

object FixedReadHandleFactory {
    private class ReadHandleImpl(maxMessagesPerRead: Int, bufferSize: Int)
        extends MaxMessagesReadHandleFactory.MaxMessageReadHandle(maxMessagesPerRead) {
        override def estimatedBufferCapacity: Int = bufferSize
    }
}
