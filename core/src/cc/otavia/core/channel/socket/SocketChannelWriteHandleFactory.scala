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

package cc.otavia.core.channel.socket

import cc.otavia.core.channel.estimator.MaxMessagesWriteHandleFactory
import cc.otavia.core.channel.estimator.MaxMessagesWriteHandleFactory
import cc.otavia.core.channel.socket.SocketChannelWriteHandleFactory.SndBufferWriteHandle
import cc.otavia.core.channel.socket.SocketChannelWriteHandleFactory.SndBufferWriteHandle.calculateMaxBytesPerGatheringWrite
import cc.otavia.core.channel.{Channel, ChannelOption}

class SocketChannelWriteHandleFactory(maxMessagesPerWrite: Int, private val maxBytesPerGatheringWrite: Long)
    extends MaxMessagesWriteHandleFactory(maxMessagesPerWrite) {

    def this(maxMessagesPerWrite: Int) = this(maxMessagesPerWrite, Long.MaxValue)

    override protected def newHandle(
        channel: Channel,
        maxMessagesPerWrite: Int
    ): MaxMessagesWriteHandleFactory.MaxMessagesWriteHandle =
        new SndBufferWriteHandle(channel, maxMessagesPerWrite, maxBytesPerGatheringWrite)

}

object SocketChannelWriteHandleFactory {

    private val MAX_BYTES_PER_GATHERING_WRITE_ATTEMPTED_LOW_THRESHOLD = 4096

    class SndBufferWriteHandle(channel: Channel, maxMessagesPerWrite: Int, val maxBytesPerGatheringWrite: Long)
        extends MaxMessagesWriteHandleFactory.MaxMessagesWriteHandle(maxMessagesPerWrite) {

        private var calculatedMaxBytesPerGatheringWrite: Long =
            calculateMaxBytesPerGatheringWrite(channel, maxBytesPerGatheringWrite)

        override def lastWrite(attemptedBytesWrite: Long, actualBytesWrite: Long, numMessagesWrite: Int): Boolean = {
            val continueWriting = super.lastWrite(attemptedBytesWrite, actualBytesWrite, numMessagesWrite)
            adjustMaxBytesPerGatheringWrite(attemptedBytesWrite, actualBytesWrite, maxBytesPerGatheringWrite)
            continueWriting
        }

        override def estimatedMaxBytesPerGatheringWrite: Long = calculatedMaxBytesPerGatheringWrite

        // By default we track the SO_SNDBUF when ever it is explicitly set. However some OSes may dynamically
        // change SO_SNDBUF (and other characteristics that determine how much data can be written at once) so we
        // should try make a best effort to adjust as OS behavior changes.
        private def adjustMaxBytesPerGatheringWrite(
            attempted: Long,
            written: Long,
            oldMaxBytesPerGatheringWrite: Long
        ): Unit = if (attempted == written) {
            if (attempted << 1 > oldMaxBytesPerGatheringWrite)
                calculatedMaxBytesPerGatheringWrite = math.min(maxBytesPerGatheringWrite, attempted << 1)
        } else if (attempted > MAX_BYTES_PER_GATHERING_WRITE_ATTEMPTED_LOW_THRESHOLD && written < (attempted >>> 1)) {
            calculatedMaxBytesPerGatheringWrite = math.min(maxBytesPerGatheringWrite, attempted >>> 1)
        }

    }

    object SndBufferWriteHandle {
        def calculateMaxBytesPerGatheringWrite(channel: Channel, maxBytesPerGatheringWrite: Long): Long = {
            // Multiply by 2 to give some extra space in case the OS can process write data faster than we can provide.
            val newSendBufferSize = channel.getOption(ChannelOption.SO_SNDBUF) << 1
            if (newSendBufferSize > 0) math.min(newSendBufferSize, maxBytesPerGatheringWrite)
            else maxBytesPerGatheringWrite
        }
    }

}
