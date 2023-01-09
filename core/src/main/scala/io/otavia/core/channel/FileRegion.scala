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

package io.otavia.core.channel

import io.netty5.util.ReferenceCounted

import java.io.IOException
import java.nio.channels.WritableByteChannel

/** A region of a file that is sent via a {@link Channel} which supports <a
 *  href="https://en.wikipedia.org/wiki/Zero-copy">zero-copy file transfer</a>.
 *
 *  <h3>Upgrade your JDK / JRE</h3>
 *
 *  [[java.nio.channels.FileChannel.transferTo]] has at least four known bugs in the old versions of Sun JDK and perhaps
 *  its derived ones. Please upgrade your JDK to 1.6.0_18 or later version if you are going to use zero-copy file
 *  transfer. <ul> <li><a href="https://bugs.java.com/bugdatabase/view_bug.do?bug_id=5103988">5103988</a>
 *    - FileChannel.transferTo() should return -1 for EAGAIN instead throws IOException</li> <li><a
 *      href="https://bugs.java.com/bugdatabase/view_bug.do?bug_id=6253145">6253145</a>
 *    - FileChannel.transferTo() on Linux fails when going beyond 2GB boundary</li> <li><a
 *      href="https://bugs.java.com/bugdatabase/view_bug.do?bug_id=6427312">6427312</a>
 *    - FileChannel.transferTo() throws IOException "system call interrupted"</li> <li><a
 *      href="https://bugs.java.com/bugdatabase/view_bug.do?bug_id=6524172">6470086</a>
 *    - FileChannel.transferTo(2147483647, 1, channel) causes "Value too large" exception</li> </ul>
 *
 *  <h3>Check your operating system and JDK / JRE</h3>
 *
 *  If your operating system (or JDK / JRE) does not support zero-copy file transfer, sending a file with [[FileRegion]]
 *  might fail or yield worse performance. For example, sending a large file doesn't work well in Windows.
 *
 *  <h3>Not all transports support it</h3>
 */
trait FileRegion extends ReferenceCounted {

    /** Returns true if the [[FileRegion]] has a open file-descriptor */
    def isOpen: Boolean

    /** Returns the offset in the file where the transfer began. */
    def position: Long

    /** Returns the bytes which was transferred already. */
    def transferred: Long

    /** Returns the number of bytes to transfer. */
    def count: Long

    /** Transfers the content of this file region to the specified channel.
     *
     *  @param target
     *    the destination of the transfer
     *  @param position
     *    the relative offset of the file where the transfer begins from. For example, <tt>0</tt> will make the transfer
     *    start from [[position]]th byte and <tt>[[count]] - 1</tt> will make the last byte of the region transferred.
     */
    @throws[IOException]
    def transferTo(target: WritableByteChannel, position: Long): Long

    override def retain: FileRegion

    override def retain(increment: Int): FileRegion

    override def touch: FileRegion

    override def touch(hint: AnyRef): FileRegion
}
