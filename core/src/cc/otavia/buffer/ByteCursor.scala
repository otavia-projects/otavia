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

/** The [[ByteCursor]] scans through a sequence of bytes. This is similar to [[ByteProcessor]], but for external
 *  iteration rather than internal iteration. The external iteration allows the callers to control the pace of the
 *  iteration.
 */
trait ByteCursor {

    /** Check if the iterator has at least one byte left, and if so, read that byte and move the cursor forward. The
     *  byte will then be available through the [[getByte]].
     *
     *  @return
     *    `true` if the cursor read a byte and moved forward, otherwise `false`.
     */
    def readByte: Boolean

    /** Return the last byte that was read by [[readByte]]. If [[readByte]] has not been called on this cursor before,
     *  then -1 is returned.
     *
     *  @return
     *    The next byte that was read by the most recent successful call to [[readByte]].
     */
    def getByte: Byte

    /** The current position of this iterator into the underlying sequence of bytes. For instance, if we are iterating a
     *  buffer, this would be the iterators current offset into the buffer.
     *
     *  @return
     *    The current iterator offset into the underlying sequence of bytes.
     */
    def currentOffset: Int

    /** Get the current number of bytes left in the iterator.
     *
     *  @return
     *    The number of bytes left in the iterator.
     */
    def bytesLeft: Int

    /** Process the remaining bytes in this iterator with the given [[ByteProcessor]]. This method consumes the
     *  iterator.
     *
     *  @param processor
     *    The processor to use for processing the bytes in the iterator.
     *  @return
     *    The number of bytes processed, if the [[ByteProcessor.process()]] method returned false, or -1 if the whole
     *    iterator was processed.
     */
    def process(processor: ByteProcessor): Int = {
        var requestMore = true
        var count       = 0
        while (readByte && requestMore) {
            requestMore = processor.process(getByte)
            count += 1
        }
        if (requestMore) -1 else count
    }

}
