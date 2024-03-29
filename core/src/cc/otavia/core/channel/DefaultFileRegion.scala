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

package cc.otavia.core.channel

import cc.otavia.core.channel.DefaultFileRegion.validate
import cc.otavia.util.*

import java.io.{File, IOException, RandomAccessFile}
import java.nio.channels.{FileChannel, WritableByteChannel}
import java.nio.charset.StandardCharsets
import scala.language.unsafeNulls

/** Default [[FileRegion]] implementation which transfer data from a [[FileChannel]] or [[File]]. Be aware that the
 *  [[FileChannel]] will be automatically closed once [[FileRegion.refCnt]] returns 0.
 *
 *  @param position
 *    the position from which the transfer should start
 *  @param count
 *    the number of bytes to transfer
 */
class DefaultFileRegion(override val position: Long, override val count: Long)
    extends AbstractReferenceCounted
    with FileRegion {

    private var _transferred: Long       = 0L
    private var fileChannel: FileChannel = _
    private var file: File               = _

    private val cBytes: Array[Byte] = count.toString.getBytes(StandardCharsets.US_ASCII)

    /** Create a new instance
     *
     *  @param fileChannel
     *    the [[FileChannel]] which should be transferred
     *  @param position
     *    the position from which the transfer should start
     *  @param count
     *    the number of bytes to transfer
     */
    def this(fileChannel: FileChannel, position: Long, count: Long) = {
        this(position, count)
        this.fileChannel = fileChannel
    }

    /** Create a new instance using the given [[File]]. The [[File]] will be opened lazily or explicitly via [[open()]].
     *
     *  @param file
     *    the [[File]] which should be transferred
     *  @param position
     *    the position from which the transfer should start
     *  @param count
     *    the number of bytes to transfer
     */
    def this(file: File, position: Long, count: Long) = {
        this(position, count)
        this.file = file
    }

    /** Create a new instance using the given [[File]]. The [[File]] will be opened lazily or explicitly via [[open()]].
     *  The [[position]] is 0 and [[count]] is the length of the file.
     *
     *  @param file
     *    the [[File]] which should be transferred
     */
    def this(file: File) = {
        this(0, file.length())
        this.file = file
    }

    /** Create a new instance using the given [[File]]. The [[File]] will be opened lazily or explicitly via [[open()]].
     *  The [[position]] is 0 and [[count]] is the size of the [[fileChannel]].
     *
     *  @param file
     *    the [[File]] which should be transferred
     */
    def this(fileChannel: FileChannel) = {
        this(0, fileChannel.size())
        this.fileChannel = fileChannel
    }

    /** Returns true if the [[FileRegion]] has a open file-descriptor */
    override def isOpen: Boolean = this.synchronized(fileChannel != null)

    override def countBytes: Array[Byte] = cBytes

    /** Explicitly open the underlying file-descriptor if not done yet.
     *  @throws IOException
     */
    @throws[IOException]
    def open(): Unit =
        if (!isOpen && refCnt > 0) this.synchronized { this.fileChannel = new RandomAccessFile(file, "r").getChannel }

    override protected def deallocate(): Unit = if (fileChannel != null) {
        val fc = fileChannel
        fileChannel = null
        try { fc.close() }
        catch { case e: IOException => }
    }

    /** Returns the bytes which was transferred already. */
    override def transferred: Long = _transferred

    /** Transfers the content of this file region to the specified channel.
     *
     *  @param target
     *    the destination of the transfer
     *  @param position
     *    the relative offset of the file where the transfer begins from. For example, <tt>0</tt> will make the transfer
     *    start from [[position]]th byte and <tt>[[count]] - 1</tt> will make the last byte of the region transferred.
     */
    override def transferTo(target: WritableByteChannel, position: Long): Long = {
        val count = this.count - position
        if (count < 0 || position < 0)
            throw new IllegalArgumentException(s"$position (expected: 0 - ${this.count - 1})")
        if (count == 0) 0
        else if (refCnt == 0) throw new IllegalReferenceCountException(0)
        else {
            // Call open to make sure fc is initialized. This is a no-oop if we called it before.
            open()
            val written = fileChannel.transferTo(this.position + position, count, target)
            if (written > 0) _transferred += written else if (written == 0) validate(this, position)
            written
        }
    }

}

private object DefaultFileRegion {
    @throws[IOException]
    private[core] def validate(region: DefaultFileRegion, position: Long): Unit = {
        // If the amount of written data is 0 we need to check if the requested count is bigger then the
        // actual file itself as it may have been truncated on disk.
        //
        // See https://github.com/netty/netty/issues/8868
        val size  = region.fileChannel.size()
        val count = region.count - position
        if (region.position + count + position > size)
            throw new IOException(s"Underlying file size $size smaller then requested count ${region.count}")
    }
}
