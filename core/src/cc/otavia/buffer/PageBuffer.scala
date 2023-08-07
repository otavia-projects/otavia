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

import cc.otavia.buffer.PageBuffer.{ST_PAGE_ALLOCATABLE, ST_PAGE_ALLOCATED}
import cc.otavia.core.util.{Report, SystemPropertyUtil}

import java.nio.ByteBuffer
import scala.language.unsafeNulls

abstract class PageBuffer(underlying: ByteBuffer) extends AbstractBuffer(underlying) {

    private var parent: PageBufferAllocator = _
    private var nxt: PageBuffer             = _
    private var status: Int                 = ST_PAGE_ALLOCATABLE

    def setAllocator(allocator: PageBufferAllocator): Unit = parent = allocator

    def allocator: PageBufferAllocator = parent

    def next_=(pageBuffer: PageBuffer): Unit = nxt = pageBuffer

    private[buffer] def setAllocated(): Unit = status = ST_PAGE_ALLOCATED
    private[buffer] def allocated: Boolean   = status == ST_PAGE_ALLOCATED

    def next: PageBuffer = nxt

    override def close(): Unit = {
        parent.recycle(this)
        status = ST_PAGE_ALLOCATABLE
        nxt = null
        super.close()
    }

    private[otavia] def byteBuffer: ByteBuffer

}

object PageBuffer {

    private val DEFAULT_PAGE_SIZE: Int      = 4
    private val ENABLE_PAGE_SIZES: Set[Int] = Set(1, 2, 4, 8, 16)
    private val K: Int                      = 1024

    val PAGE_SIZE: Int = {
        val size = SystemPropertyUtil.getInt("cc.otavia.buffer.page.size", DEFAULT_PAGE_SIZE)
        if (ENABLE_PAGE_SIZES.contains(size)) size * K
        else {
            Report.report(
              s"cc.otavia.buffer.page.size is set to $size, but only support ${ENABLE_PAGE_SIZES.mkString("[", ", ", "]")}",
              "Buffer"
            )
            DEFAULT_PAGE_SIZE * K
        }
    }

    val ST_PAGE_ALLOCATABLE: Int = 0
    val ST_PAGE_ALLOCATED: Int   = 1

}
