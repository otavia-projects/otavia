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

/** Implementations allow to influence how much data / messages are written per write loop invocation. */
object WriteHandleFactory {

    /** Handle which allows to customize how data / messages are read. */
    trait WriteHandle {

        /** Estimate the maximum number of bytes that can be written with one gathering write operation. */
        def estimatedMaxBytesPerGatheringWrite: Long = Integer.MAX_VALUE

        /** Notify the [[WriteHandle]] of the last write operation and its result.
         *
         *  @param attemptedBytesWrite
         *    The number of bytes the write operation did attempt to write.
         *  @param actualBytesWrite
         *    The number of bytes from the previous write operation. This may be negative if a write error occurs.
         *  @param numMessagesWrite
         *    The number of messages written.
         *  @return
         *    true if the write loop should continue writing, false otherwise.
         */
        def lastWrite(attemptedBytesWrite: Long, actualBytesWrite: Long, numMessagesWrite: Int): Boolean

        /** Method that must be called once the write loop was completed. */
        def writeComplete(): Unit
    }
}

trait WriteHandleFactory {

    /** Creates a new handle for the given [[Channel]].
     *
     *  @param channel
     *    the [[Channel]] for which the [[WriteHandle]] is used.
     */
    def newHandle(channel: Channel): WriteHandleFactory.WriteHandle
}
