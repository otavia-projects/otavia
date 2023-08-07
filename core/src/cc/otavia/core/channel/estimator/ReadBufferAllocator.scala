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

import cc.otavia.buffer.{Buffer, BufferAllocator}
import cc.otavia.core.channel.ChannelOutboundInvoker
import cc.otavia.core.channel.message.ReadPlan

/** Implementations of this interface can influence how [[Buffer]]s are allocated that are used when reading from the
 *  transport.
 */
object ReadBufferAllocator {

    /** Return a [[ReadBufferAllocator]] that will return a [[Buffer]] that has exact `numBytes` of writable bytes.
     *
     *  @param numBytes
     *    the number of bytes that can be written.
     *  @return
     *    a [[ReadBufferAllocator]].
     */
//    def exact(numBytes: Int): ReadBufferAllocator = (allocator: BufferAllocator, estimatedCapacity: Int) => {
//        val buffer = allocator.allocate()
//        if (numBytes != buffer.writableBytes) ??? else buffer
//    }
}

trait ReadBufferAllocator extends ReadPlan {

    /** Allocates a [[Buffer]] that is used for the next actual read to store the data in.
     *
     *  @param allocator
     *    The [[BufferAllocator]] that might be used for the allocation. While its fine to not use the
     *    [[BufferAllocator]] it is important that the returned [[Buffer]] matches the
     *    [[BufferAllocator#getAllocationType()]] of the allocator.
     *  @param estimatedCapacity
     *    the estimated capacity for the buffer. This is just a best guess and users might decide to use their own
     *    capacity.
     *  @return
     *    the [[Buffer]] that will be used or [[null]] if the read should not happen until the next
     *    [[ChannelOutboundInvoker#read(ReadBufferAllocator)]] call is done.
     */
    def allocate(allocator: BufferAllocator, estimatedCapacity: Int): Buffer
}
