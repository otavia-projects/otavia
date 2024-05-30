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

import cc.otavia.buffer.Buffer
import cc.otavia.buffer.pool.{PooledPageAllocator, RecyclablePageBuffer}

import java.nio.ByteBuffer
import java.nio.channels.{FileChannel, ReadableByteChannel, WritableByteChannel}
import java.nio.charset.Charset
import javax.net.ssl.{SSLEngine, SSLEngineResult}
import scala.collection.mutable
import scala.language.unsafeNulls

/** A Adaptive allocate and release memory [[RecyclablePageBuffer]]. This type of [[Buffer]] */
trait AdaptiveBuffer extends Buffer {

    def allocator: PooledPageAllocator

    /** Split the [[RecyclablePageBuffer]] chain from this [[AdaptiveBuffer]]
     *
     *  @param offset
     *    split offset
     *  @return
     */
    private[otavia] def splitBefore(offset: Int): RecyclablePageBuffer

    /** Split the last [[RecyclablePageBuffer]] from this [[AdaptiveBuffer]]
     *
     *  @return
     */
    private[otavia] def splitLast(): RecyclablePageBuffer

    def allocatedWritableBytes: Int

    /** Append this [[RecyclablePageBuffer]] to the end of this [[AdaptiveBuffer]]
     *
     *  @param buffer
     *    [[RecyclablePageBuffer]] allocated by this [[allocator]]
     */
    private[otavia] def extend(buffer: RecyclablePageBuffer): Unit

    def sslunwarp(engine: SSLEngine, packetLength: Int, target: ByteBuffer): SSLEngineResult

    def sslwarp(engine: SSLEngine, target: AdaptiveBuffer): SSLEngineResult

    def sslHandshakeWarp(engine: SSLEngine, emptySource: Array[ByteBuffer]): SSLEngineResult

    def sslHandshakeUnwarp(engine: SSLEngine, emptyTarget: Array[ByteBuffer]): SSLEngineResult

}

object AdaptiveBuffer {

    val MAX_BUFFER_SIZE: Int = Int.MaxValue - 8

    def apply(allocator: PooledPageAllocator): AdaptiveBuffer = new AdaptiveBufferImpl(allocator)

    def apply(pageBuffer: RecyclablePageBuffer): AdaptiveBuffer = {
        val adaptiveBuffer = new AdaptiveBufferImpl(pageBuffer.allocator)
        var cursor         = pageBuffer
        while (cursor != null) {
            val buffer = cursor
            cursor = cursor.next
            buffer.next = null
            adaptiveBuffer.extend(buffer)
        }
        adaptiveBuffer
    }

}
