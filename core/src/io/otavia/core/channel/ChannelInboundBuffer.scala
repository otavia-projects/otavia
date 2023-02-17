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

package io.otavia.core.channel

import io.netty5.buffer.{Buffer, BufferAllocator, CompositeBuffer}
import io.otavia.core.channel.cumulator.{Cumulator, ReadSinkBufferCumulator}

trait ChannelInboundBuffer {
    this: Channel =>

    private var accumulation: CompositeBuffer | Null = null
    private var firstAcc: Boolean                    = true

    private[channel] def resetInboundBuffer(): Unit = {
        accumulation = directAllocator.compose()
        firstAcc = true
    }

    private[channel] def closeInboundBuffer(): Unit = accumulation = null

    private[channel] def channelInboundBufferAccumulation: CompositeBuffer = accumulation.asInstanceOf[CompositeBuffer]

    def channelInboundBuffer(ctx: ChannelHandlerContext, buffer: Buffer): Unit = {
        val acc = channelInboundBufferAccumulation
        acc.extendWith(buffer.send())
        firstAcc = false
    }

    def inboundAccumulationIsEmpty: Boolean = ???
    def inboundAccumulationIsNull: Boolean  = accumulation == null

}

object ChannelInboundBuffer {
    private val cumulator: Cumulator = new ReadSinkBufferCumulator()
}
