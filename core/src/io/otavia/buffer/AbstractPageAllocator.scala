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

import java.nio.ByteBuffer
import scala.language.unsafeNulls

abstract class AbstractPageAllocator extends BufferAllocator {

    private var count: Int       = 0
    private var head: PageBuffer = _
    private var tail: PageBuffer = _

    protected def push(pageBuffer: PageBuffer): Unit = this.synchronized {
        if (count == 0) {
            head = pageBuffer
            tail = pageBuffer
        } else {
            pageBuffer.next = head
            head = pageBuffer
        }
        count += 1
    }

    protected def pop(): PageBuffer = this.synchronized {
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
    }

    protected def nonEmpty: Boolean = this.synchronized(count > 0)

    protected def isEmpty: Boolean = this.synchronized(count == 0)

    override def isPooling: Boolean = true

    override def recycle(buffer: Buffer): Unit = {
        buffer match
            case pageBuffer: PageBuffer =>
                if (pageBuffer.allocator.eq(this)) push(pageBuffer)
            case _ =>
    }

    override def allocate(): PageBuffer = {
        val buffer =
            if (nonEmpty) pop()
            else {
                val page = newBuffer()
                page.setAllocator(this)
                page
            }
        buffer.setAllocated()
        buffer
    }

    protected def newBuffer(): PageBuffer

}
