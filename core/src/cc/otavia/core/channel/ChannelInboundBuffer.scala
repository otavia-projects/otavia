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

package cc.otavia.core.channel

import cc.otavia.buffer.{Buffer, BufferAllocator, CompositeBuffer}
import cc.otavia.core.channel.cumulator.{Cumulator, ReadSinkBufferCumulator}

trait ChannelInboundBuffer {
    this: Channel =>

    private var accumulation: CompositeBuffer | Null = null
    private var firstAcc: Boolean                    = true

    private[core] def resetInboundBuffer(): Unit = {
        accumulation = ??? // directAllocator.compose()
        firstAcc = true
    }

    private[core] def closeInboundBuffer(): Unit = accumulation = null

    private[core] def channelInboundBufferAccumulation: CompositeBuffer = accumulation.asInstanceOf[CompositeBuffer]

    def channelInboundBuffer(ctx: ChannelHandlerContext, buffer: Buffer): Unit = {
        val acc = channelInboundBufferAccumulation
//        acc.extendWith(buffer.send())
        firstAcc = false
    }

    def inboundAccumulationIsEmpty: Boolean = ???
    def inboundAccumulationIsNull: Boolean  = accumulation == null

}

object ChannelInboundBuffer {
    private val cumulator: Cumulator = new ReadSinkBufferCumulator()
}