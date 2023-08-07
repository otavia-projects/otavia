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

package cc.otavia.core.buffer

import cc.otavia.buffer.*
import cc.otavia.core.buffer.AdaptiveBuffer.AdaptiveStrategy
import cc.otavia.core.cache.{PerThreadObjectPool, Poolable}
import cc.otavia.core.util.Chainable

import java.nio.ByteBuffer
import java.nio.channels.{FileChannel, ReadableByteChannel, WritableByteChannel}
import java.nio.charset.Charset
import scala.collection.mutable
import scala.language.unsafeNulls

/** A Adaptive allocate and release memory [[PageBuffer]]. This type of [[Buffer]] */
trait AdaptiveBuffer extends Buffer {

    def allocator: PageBufferAllocator

    def setStrategy(adaptiveStrategy: AdaptiveStrategy): Unit

    /** Split the [[PageBuffer]] chain from this [[AdaptiveBuffer]]
     *
     *  @param offset
     *    split offset
     *  @return
     */
    private[otavia] def splitBefore(offset: Int): PageBuffer

    /** Append this [[PageBuffer]] to the end of this [[AdaptiveBuffer]]
     *
     *  @param buffer
     *    [[PageBuffer]] allocated by this [[allocator]]
     */
    private[otavia] def extend(buffer: PageBuffer): Unit

    /** Writes into this [[AdaptiveBuffer]] from the source [[AdaptiveBuffer]]. This updates the write offset of this
     *  buffer and also the position of the source [[AdaptiveBuffer]].
     *
     *  Note: the behaviour is undefined if the given [[AdaptiveBuffer]] is an alias for the memory in this buffer.
     *
     *  @param source
     *    The [[AdaptiveBuffer]] to read from.
     *  @param length
     *    length of data to write.
     *  @return
     *    This buffer.
     */
    def writeBytes(source: AdaptiveBuffer, length: Int): AdaptiveBuffer

    /** Read from this [[AdaptiveBuffer]], into the destination [[AdaptiveBuffer]] This updates the read offset of this
     *  buffer and also the position of the destination [[AdaptiveBuffer]].
     *
     *  Note: the behaviour is undefined if the given [[AdaptiveBuffer]] is an alias for the memory in this buffer.
     *
     *  @param destination
     *    The [[AdaptiveBuffer]] to write into.
     *  @param length
     *    length of data to read.
     *  @return
     *    This buffer.
     */
    def readBytes(destination: AdaptiveBuffer, length: Int): AdaptiveBuffer

}

object AdaptiveBuffer {

    val MAX_BUFFER_SIZE: Int = Int.MaxValue - 8

    def apply(allocator: PageBufferAllocator): AdaptiveBuffer = new AdaptiveBufferImpl(allocator)

    private object BufferEntry {

        private val recycler = new PerThreadObjectPool[BufferEntry] {
            override protected def newObject(): BufferEntry = new BufferEntry
        }

        def apply(buffer: Buffer): BufferEntry = {
            val bufferEntry = recycler.get()
            bufferEntry.setBuffer(buffer)
            bufferEntry
        }

    }

    class BufferEntry extends Poolable {

        private var buf: Buffer | Null = _

        private def setBuffer(buffer: Buffer): Unit = buf = buffer
        def buffer: Buffer                          = buf

        override def recycle(): Unit = BufferEntry.recycler.recycle(this)

        override protected def cleanInstance(): Unit = buf = null

        override def next: BufferEntry | Null = super.next.asInstanceOf[BufferEntry | Null]

    }

    trait AdaptiveStrategy {
        def nextSize: Int
    }

    class FixedIncreaseStrategy(override val nextSize: Int) extends AdaptiveStrategy

    object QuarterPageStrategy extends FixedIncreaseStrategy(1024)

    object FixedHalfPageStrategy extends FixedIncreaseStrategy(2048)

    object FullPageStrategy extends FixedIncreaseStrategy(4096)

}
