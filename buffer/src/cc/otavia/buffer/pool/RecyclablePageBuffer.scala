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

package cc.otavia.buffer.pool

import cc.otavia.buffer.pool.PooledPageAllocator
import cc.otavia.buffer.{AbstractBuffer, Buffer}

import java.nio.ByteBuffer
import scala.language.unsafeNulls

abstract class RecyclablePageBuffer(underlying: ByteBuffer) extends AbstractBuffer(underlying) {

    import RecyclablePageBuffer.*

    private var parent: PooledPageAllocator = _
    private var nxt: RecyclablePageBuffer   = _
    private var status: Int                 = ST_PAGE_ALLOCATABLE

    def setAllocator(allocator: PooledPageAllocator): Unit = parent = allocator

    def allocator: PooledPageAllocator = parent

    def next_=(pageBuffer: RecyclablePageBuffer): Unit = nxt = pageBuffer

    private[buffer] def setAllocated(): Unit = status = ST_PAGE_ALLOCATED
    private[buffer] def allocated: Boolean   = status == ST_PAGE_ALLOCATED

    def next: RecyclablePageBuffer = nxt

    override def close(): Unit = {
        super.close()
        status = ST_PAGE_ALLOCATABLE
        parent.recycle(this)
    }

    override def closed: Boolean = status == ST_PAGE_ALLOCATABLE

    private[otavia] def byteBuffer: ByteBuffer

}

object RecyclablePageBuffer {

    val ST_PAGE_ALLOCATABLE: Int = 0
    val ST_PAGE_ALLOCATED: Int   = 1

}
