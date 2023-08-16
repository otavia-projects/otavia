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

package cc.otavia.buffer.pool

import cc.otavia.buffer.pool.RecyclablePageBuffer

import java.nio.ByteBuffer
import scala.language.unsafeNulls

class DirectRecyclablePageBuffer(underlying: ByteBuffer) extends RecyclablePageBuffer(underlying) {

    assert(underlying.isDirect)

    override private[otavia] def byteBuffer = underlying

    override def isDirect: Boolean = true

}

object DirectRecyclablePageBuffer {
    def apply(underlying: ByteBuffer): DirectRecyclablePageBuffer = new DirectRecyclablePageBuffer(underlying)
}
