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

import io.otavia.core.channel.AdaptiveReadHandleFactory.*

import java.util
import java.util.{ArrayList, List}

/** The [[ReadHandleFactory]] that automatically increases and decreases the predicted buffer size on feed back.
 *
 *  It gradually increases the expected number of readable bytes if the previous read fully filled the allocated buffer.
 *  It gradually decreases the expected number of readable bytes if the read operation was unable to fill a certain
 *  amount of the allocated buffer two times consecutively. Otherwise, it keeps returning the same prediction.
 *
 *  @param maxMessagesPerRead
 *    the maximum number of messages to read per read loop invocation.
 *  @param minimum
 *    the inclusive lower bound of the expected buffer size
 *  @param initial
 *    the initial buffer size when no feedback was received
 *  @param maximum
 *    the inclusive upper bound of the expected buffer size
 */
class AdaptiveReadHandleFactory(maxMessagesPerRead: Int, minimum: Int, val initial: Int, maximum: Int)
    extends MaxMessagesReadHandleFactory {

    /** Creates a new predictor with the default parameters. With the default parameters, the expected buffer size
     *  starts from 1024, does not go down below 64, and does not go up above 65536.
     *
     *  @param maxMessagesPerRead
     *    the maximum number of messages to read per read loop invocation.
     */
    def this(maxMessagesPerRead: Int) = this(maxMessagesPerRead, DEFAULT_MINIMUM, DEFAULT_INITIAL, DEFAULT_MAXIMUM)

    /** Creates a new predictor with the default parameters. With the default parameters, the expected buffer size
     *  starts from 1024, does not go down below 64, and does not go up above 65536.
     */
    def this() = this(1)

    private var minIndex: Int = 0
    private var maxIndex: Int = 0

    override protected def newMaxMessageHandle(
        maxMessagesPerRead: Int
    ): MaxMessagesReadHandleFactory.MaxMessageReadHandle =
        new ReadHandleImpl(maxMessagesPerRead, minIndex, maxIndex, initial)

}

object AdaptiveReadHandleFactory {
    private[channel] val DEFAULT_MINIMUM = 64
    // Use an initial value that is bigger than the common MTU of 1500
    private[channel] val DEFAULT_INITIAL = 2048
    private[channel] val DEFAULT_MAXIMUM = 65536

    private val INDEX_INCREMENT = 4
    private val INDEX_DECREMENT = 1

    private var SIZE_TABLE: Array[Int] = {
        val sizeTable: util.List[Integer] = new util.ArrayList[Integer]
        var i: Int                        = 16
        while (i < 512) {
            sizeTable.add(i)
            i += 16
        }

        // Suppress a warning since `i` becomes negative when an integer overflow happens// Suppress a warning since `i` becomes negative when an integer overflow happens
        var j: Int = 512
        while (j > 0) { // lgtm[java/constant-comparison]
            sizeTable.add(j)
            j <<= 1
        }

        val table = new Array[Int](sizeTable.size)
        for (i <- table.indices) {
            table(i) = sizeTable.get(i)
        }
        table
    }

    private def getSizeTableIndex(size: Int): Int = {
        // TODO
        ???
    }

    protected final class ReadHandleImpl(
        maxMessagesPerRead: Int,
        val minIndex: Int,
        val maxIndex: Int,
        val initial: Int
    ) extends MaxMessagesReadHandleFactory.MaxMessageReadHandle(maxMessagesPerRead) {
        private var index: Int                 = getSizeTableIndex(initial)
        private var nextReceiveBufferSize: Int = SIZE_TABLE(index)
        private var decreaseNow: Boolean       = _

        private var _totalBytesRead: Int = 0

        override def lastRead(attemptedBytesRead: Int, actualBytesRead: Int, numMessagesRead: Int): Boolean = {
            // If we read as much as we asked for we should check if we need to ramp up the size of our next guess.
            // This helps adjust more quickly when large amounts of data is pending and can avoid going back to
            // the selector to check for more data. Going back to the selector can add significant latency for large
            // data transfers.
            if (attemptedBytesRead == actualBytesRead) record(actualBytesRead)
            if (actualBytesRead > 0) _totalBytesRead += actualBytesRead
            super.lastRead(attemptedBytesRead, actualBytesRead, numMessagesRead)
        }

        override def estimatedBufferCapacity: Int = nextReceiveBufferSize

        private def record(actualReadBytes: Int): Unit = {
            // TODO
            ???
        }

        override def readComplete(): Unit = {
            record(totalBytesRead())
            _totalBytesRead = 0
            super.readComplete()
        }

        private def totalBytesRead(): Int = if (_totalBytesRead < 0) Int.MaxValue else _totalBytesRead
    }

}
