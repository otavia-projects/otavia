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

package cc.otavia.buffer

import java.nio.ByteBuffer
import scala.language.unsafeNulls

/** Interface for allocating [[Buffer]]s. */
trait BufferAllocator {

    /** Allocate a [[Buffer]] of the given size in bytes. This method may throw an [[OutOfMemoryError]] if there is not
     *  enough free memory available to allocate a [[Buffer]] of the requested size. <p> The buffer will use big endian
     *  byte order.
     *
     *  <p> <strong>Note:</strong> unlike the JDK [[ByteBuffer]]s, Netty [[Buffer]]s are not guaranteed to be zeroed
     *  when allocated. In other words, the memory of a newly allocated buffer may contain garbage data from prior
     *  allocations, and the memory is likewise not guaranteed to be erased when the buffer is closed. If the data is
     *  sensitive and needs to be overwritten when the buffer is closed, then the buffer should be allocated with the
     *  [[SensitiveBufferAllocator]].
     *
     *  @return
     *    The newly allocated [[Buffer]].
     *  @throws IllegalStateException
     *    if this allocator has been closed.
     */
    def allocate(size: Int): Buffer

    /** Determine if this allocator is pooling and reusing its allocated memory.
     *
     *  @return
     *    true if this allocator is pooling and reusing its memory, false otherwise.
     */
    def isPooling: Boolean

    /** Recycle the [[Buffer]]
     *  @param buffer
     */
    def recycle(buffer: Buffer): Unit

    /** Determine if this allocator is allocate direct memory or heap memory.
     *
     *  @return
     *    true if this allocator is direct, false otherwise.
     */
    def isDirect: Boolean

}

object BufferAllocator {

    private val DEFAULT_OFF_HEAP_ALLOCATOR = new BufferAllocator {

        override def allocate(size: Int): Buffer = new DirectBuffer(ByteBuffer.allocateDirect(size))

        override def isPooling: Boolean = false

        override def recycle(buffer: Buffer): Unit = buffer.close()

        override def isDirect: Boolean = true

    }

    private val DEFAULT_ON_HEAP_ALLOCATOR = new BufferAllocator {

        override def allocate(size: Int): Buffer = new HeapBuffer(ByteBuffer.allocate(size))

        override def isPooling: Boolean = false

        override def recycle(buffer: Buffer): Unit = buffer.close()

        override def isDirect: Boolean = false

    }

    def onHeapAllocator(): BufferAllocator = DEFAULT_ON_HEAP_ALLOCATOR

    def offHeapAllocator(): BufferAllocator = DEFAULT_OFF_HEAP_ALLOCATOR

}
