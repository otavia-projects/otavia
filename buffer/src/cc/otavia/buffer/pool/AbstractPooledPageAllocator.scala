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

import cc.otavia.buffer.pool.{PooledPageAllocator, RecyclablePageBuffer}
import cc.otavia.buffer.{Buffer, FixedCapacityAllocator}

import java.nio.ByteBuffer
import scala.language.unsafeNulls

abstract class AbstractPooledPageAllocator(val fixedCapacity: Int) extends PooledPageAllocator {

    private var count: Int                 = 0
    private var head: RecyclablePageBuffer = _
    private var tail: RecyclablePageBuffer = _

    def this() = this(FixedCapacityAllocator.DEFAULT_PAGE_SIZE)

    protected def push(pageBuffer: RecyclablePageBuffer): Unit = this.synchronized {
        if (count == 0) {
            head = pageBuffer
            tail = pageBuffer
        } else {
            pageBuffer.next = head
            head = pageBuffer
        }
        count += 1
    }

    protected def pop(): RecyclablePageBuffer | Null = this.synchronized {
        if (count > 0) {
            val page = head
            if (count == 1) {
                head = null
                tail = null
            } else {
                head = page.next
                page.next = null
            }
            count -= 1
            page
        } else null
    }

    override def isPooling: Boolean = true

    override def recycle(buffer: Buffer): Unit = {
        buffer match
            case pageBuffer: RecyclablePageBuffer =>
                if (pageBuffer.allocator.eq(this)) push(pageBuffer)
            case _ =>
    }

    override def allocate(): RecyclablePageBuffer = {
        val buffer = pop() match
            case null =>
                val page = newBuffer()
                page.setAllocator(this)
                page
            case buffer: RecyclablePageBuffer => buffer

        buffer.setAllocated()
        buffer
    }

    def size: Int = count

}
