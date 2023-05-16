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

import sun.misc.Unsafe
import sun.nio.ch.DirectBuffer

import java.nio.ByteBuffer
import java.nio.channels.{FileChannel, ReadableByteChannel, WritableByteChannel}
import java.nio.charset.Charset

abstract class AbstractBuffer(protected val underlying: ByteBuffer) extends Buffer {

    protected var ridx: Int = 0
    protected var widx: Int = 0

    override def readerOffset: Int = ridx

    override def readerOffset(offset: Int): Buffer = {
        ridx = offset
        this
    }

    override def writerOffset: Int = widx

    override def writerOffset(offset: Int): Buffer = {
        widx = offset
        this
    }

    override def close(): Unit = {
        ridx = 0
        widx = 0
    }

    override def capacity: Int = underlying.capacity()

    override def copyInto(srcPos: Int, dest: Array[Byte], destPos: Int, length: Int): Unit =
        underlying.put(srcPos, dest, destPos, length)

    override def copyInto(srcPos: Int, dest: ByteBuffer, destPos: Int, length: Int): Unit =
        underlying.put(srcPos, dest, destPos, length)

    override def copyInto(srcPos: Int, dest: Buffer, destPos: Int, length: Int): Unit = {
        ???
    }

    override def transferTo(channel: WritableByteChannel, length: Int): Int = {
        if (length > 0) {
            underlying.position(ridx)
            if (length > readableBytes) underlying.limit(ridx + readableBytes) else underlying.limit(ridx + length)
            val write = channel.write(underlying)
            skipReadableBytes(write)
            write
        } else 0
    }

    override def transferFrom(channel: FileChannel, position: Long, length: Int): Int = ???

    override def transferFrom(channel: ReadableByteChannel, length: Int): Int = ???

}
