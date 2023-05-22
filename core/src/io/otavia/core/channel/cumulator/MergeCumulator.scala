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

package io.otavia.core.channel.cumulator

import io.netty5.util.internal.MathUtil
import io.otavia.buffer.{Buffer, BufferAllocator}
import io.otavia.core.channel.cumulator.MergeCumulator.expandAccumulationAndWrite

final class MergeCumulator extends Cumulator {

    override def cumulate(alloc: BufferAllocator, accumulation: Buffer, in: Buffer): Buffer =
        if (accumulation.readableBytes == 0) {
            accumulation.close()
            in
        } else {
            val required = in.readableBytes
            if (required > accumulation.writableBytes) expandAccumulationAndWrite(alloc, accumulation, in)
            else {
                accumulation.writeBytes(in)
                in.close()
                accumulation
            }
        }

    override def discardSomeReadBytes(accumulation: Buffer): Buffer = {
        if (accumulation.readerOffset > accumulation.writableBytes) accumulation.compact()
        accumulation
    }

    override def toString: String = "MergeCumulator"

}

object MergeCumulator {
    private def expandAccumulationAndWrite(allocator: BufferAllocator, oldAcc: Buffer, in: Buffer): Buffer = {
        val newSize = MathUtil.safeFindNextPositivePowerOfTwo(oldAcc.readableBytes + in.readableBytes)

        if (false /*oldAcc.readOnly()*/ ) {
            val newAcc = allocator.allocate()
            newAcc.writeBytes(oldAcc)
            oldAcc.close()
            newAcc.writeBytes(in)
            in.close()
            newAcc
        } else {
            oldAcc.ensureWritable(newSize)
            oldAcc.writeBytes(in)
            in.close()
            oldAcc
        }
    }
}
