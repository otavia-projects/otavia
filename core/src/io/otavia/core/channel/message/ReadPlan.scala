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

package io.otavia.core.channel.message

import io.otavia.core.channel.ChannelOutboundInvoker

import java.nio.channels.FileChannel

/** [[ReadPlan]] is a trait usage by [[ChannelOutboundInvoker.read]] to describe how to read data read from a channel */
trait ReadPlan {

    /** Guess the capacity for the next receive buffer size that is probably large enough to read all inbound data and
     *  small enough not to waste its space.
     */
    def estimatedNextSize: Int

    /** Notify the [[ReadHandle]] of the last read operation and its result.
     *
     *  @param attemptedBytesRead
     *    The number of bytes the read operation did attempt to read.
     *  @param actualBytesRead
     *    The number of bytes from the previous read operation. This may be negative if a read error occurs.
     *  @param numMessagesRead
     *    The number of messages read.
     *  @return
     *    true if the read loop should continue reading, false otherwise.
     */
    def lastRead(attemptedBytesRead: Int, actualBytesRead: Int, numMessagesRead: Int): Boolean

    /** Method that must be called once the read loop was completed. */
    def readComplete(): Unit

}
