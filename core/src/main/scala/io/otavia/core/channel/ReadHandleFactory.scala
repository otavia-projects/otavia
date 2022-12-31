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

/** Implementations allow to influence how much data / messages are read per read loop invocation. */
object ReadHandleFactory {

    /** Handle which allows to customize how data / messages are read. */
    trait ReadHandle {

        /** Guess the capacity for the next receive buffer that is probably large enough to read all inbound data and
         *  small enough not to waste its space.
         */
        def estimatedBufferCapacity: Int

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
}

trait ReadHandleFactory {

    /** Creates a new handle for the given [[Channel]].
     *
     *  @param channel
     *    the [[Channel]] for which the [[ReadHandle]] is used.
     */
    def newHandle(channel: Channel): ReadHandleFactory.ReadHandle
}
