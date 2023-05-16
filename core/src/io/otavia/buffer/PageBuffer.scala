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

import io.otavia.buffer.PageBuffer.{ST_PAGE_ALLOCATABLE, ST_PAGE_ALLOCATED}

trait PageBuffer extends Buffer {

    private var parent: BufferAllocator = _
    private var n: PageBuffer           = _
    private var status: Int             = ST_PAGE_ALLOCATABLE

    def setAllocator(allocator: BufferAllocator): Unit = parent = allocator

    def allocator: BufferAllocator = parent

    def next_=(pageBuffer: PageBuffer): Unit = n = pageBuffer

    private[buffer] def setAllocated(): Unit = status = ST_PAGE_ALLOCATED
    private[buffer] def allocated: Boolean   = status == ST_PAGE_ALLOCATED

    def next: PageBuffer = n

    override def close(): Unit = {
        parent.recycle(this)
        status = ST_PAGE_ALLOCATABLE
    }

}

object PageBuffer {

    val PAGE_SIZE: Int = 1024 * 8

    val ST_PAGE_ALLOCATABLE: Int = 0
    val ST_PAGE_ALLOCATED: Int   = 1

}
