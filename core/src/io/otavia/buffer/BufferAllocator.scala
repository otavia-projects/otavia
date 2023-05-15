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

package io.otavia.buffer

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
    def allocate(): Buffer

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

}
