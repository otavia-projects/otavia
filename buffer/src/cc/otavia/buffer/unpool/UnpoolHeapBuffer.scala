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

package cc.otavia.buffer.unpool

import cc.otavia.buffer.{AbstractBuffer, Buffer}

import java.nio.ByteBuffer
import java.nio.channels.{FileChannel, ReadableByteChannel, WritableByteChannel}
import java.nio.charset.Charset
import java.util
import scala.language.unsafeNulls

class UnpoolHeapBuffer(underlying: ByteBuffer, clean: Boolean = false) extends AbstractBuffer(underlying) {

    assert(underlying.hasArray)
    if (clean) {
        writerOffset(0)
        readerOffset(0)
    } else {
        writerOffset(underlying.limit())
        readerOffset(underlying.position())
    }

    private val array: Array[Byte] = underlying.array()

    override def fill(value: Byte): Buffer = {
        var i = 0
        while (i < capacity) {
            array(i) = value
            i += 1
        }
        this
    }

    override def isDirect: Boolean = false

    override def closed: Boolean = false

}
