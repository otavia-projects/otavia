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

package cc.otavia.buffer.pool

import cc.otavia.buffer.pool.RecyclablePageBuffer
import cc.otavia.buffer.{BufferAllocator, FixedCapacityAllocator}

trait PooledPageAllocator extends RecyclableAllocator with FixedCapacityAllocator {

    override def isPooling: Boolean = true

    override def allocate(size: Int): RecyclablePageBuffer = allocate()

    def allocate(): RecyclablePageBuffer

    protected def newBuffer(): RecyclablePageBuffer

    def minCache: Int

    def maxCache: Int

    def cacheSize: Int

    def totalAllocated: Int

    /** Release the allocated cache buffer to [[minCache]] size. */
    def release(): Unit

    /** Release all the allocated cache buffers. */
    def releaseAll(): Unit

}
