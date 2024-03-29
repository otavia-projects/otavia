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

import cc.otavia.buffer.AbstractBuffer

import java.nio.ByteBuffer
import java.nio.channels.{FileChannel, ReadableByteChannel, WritableByteChannel}
import java.nio.charset.Charset
import scala.language.unsafeNulls

class UnpoolDirectBuffer(underlying: ByteBuffer) extends AbstractBuffer(underlying) {

    assert(underlying.isDirect)

    writerOffset(underlying.limit())
    readerOffset(underlying.position())

    override def isDirect: Boolean = true

    override def closed: Boolean = false

}

object UnpoolDirectBuffer {
    def apply(underlying: ByteBuffer): UnpoolDirectBuffer = new UnpoolDirectBuffer(underlying)
}
