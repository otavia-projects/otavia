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
import scala.collection.mutable
import scala.language.unsafeNulls

class PooledSinglePageAllocator(val direct: Boolean = false) extends BufferAllocator {

    private val cache: mutable.Queue[PageBuffer] = mutable.Queue.empty

    override def allocate(): PageBuffer = {
        if (cache.isEmpty) {
            val buf =
                if (direct) ByteBuffer.allocateDirect(PageBuffer.PAGE_SIZE)
                else
                    ByteBuffer.allocate(PageBuffer.PAGE_SIZE)

            val page = new PageBuffer(buf)
            page.setAllocator(this)
            page
        } else {
            cache.dequeue()
        }
    }

    override def isPooling: Boolean = true

    override def recycle(buffer: Buffer): Unit = {
        buffer match
            case singlePageBuffer: PageBuffer =>
                if (singlePageBuffer.allocator.eq(this)) cache.enqueue(singlePageBuffer)
            case _ =>

    }

}
