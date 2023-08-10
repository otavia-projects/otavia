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

package cc.otavia.core.channel.cumulator

import cc.otavia.buffer.{Buffer, BufferAllocator, CompositeBuffer}
import io.netty5.util.Send

import java.util

final class CompositeBufferCumulator extends Cumulator {

    override def cumulate(alloc: BufferAllocator, accumulation: Buffer, in: Buffer): Buffer =
        if (accumulation.readableBytes == 0) {
            accumulation.close()
            in
        } else if (in.readableBytes == 0) {
            in.close()
            accumulation
        } else {
            val buffer = if (false /* accumulation.readOnly() */ ) {
                val tmp = ??? // accumulation.copy()
                accumulation.close()
                tmp
            } else accumulation
            val composite = buffer match
                case composite: CompositeBuffer =>
//                    composite.extendWith(prepareInForCompose(in))
                    composite
                case _ =>
//                    alloc.compose(util.Arrays.asList( ???, prepareInForCompose(in)))
                    ???
            in.close()
            composite.nn
        }

    override def discardSomeReadBytes(accumulation: Buffer): Buffer = {
        // Compact is slow on composite buffers, and we also need to avoid leaving any writable space at the end.
        // Using readSplit(0), we grab zero readable bytes in the split-off buffer, but all the already-read
        // bytes get cut off from the accumulation buffer.

//        accumulation.readSplit(0).nn.close()
        accumulation

    }

    override def toString: String = "CompositeBufferCumulator"

}

object CompositeBufferCumulator {
//    private def prepareInForCompose(in: Buffer): Send[Buffer] = ???
//        if (in.readOnly()) in.copy().nn.send().nn else in.send().nn

}
