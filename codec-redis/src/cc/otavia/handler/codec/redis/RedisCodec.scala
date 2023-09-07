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

package cc.otavia.handler.codec.redis

import cc.otavia.buffer.pool.AdaptiveBuffer
import cc.otavia.core.channel.ChannelHandlerContext
import cc.otavia.handler.codec.*
import cc.otavia.redis.cmd.*
import cc.otavia.redis.serde.RedisSerde
import cc.otavia.redis.serde.impl.{OKSerde, SelectSerde}

import scala.collection.mutable

class RedisCodec extends ByteToMessageCodec {

    private val responseSerdeQueue: mutable.Queue[(Long, RedisSerde[?])] = mutable.Queue.empty

    override protected def encode(ctx: ChannelHandlerContext, output: AdaptiveBuffer, msg: AnyRef, mid: Long): Unit = {
        msg match
            case select: Select =>
                SelectSerde.serialize(select, output)
                responseSerdeQueue.addOne((mid, OKSerde))
        ???
    }

    override protected def decode(ctx: ChannelHandlerContext, input: AdaptiveBuffer): Unit =
        if (responseSerdeQueue.head._2.checkDeserializable(input)) {
            val (msgId, serde) = responseSerdeQueue.removeHead()
            try {
                val response = serde.deserialize(input)
                ctx.fireChannelRead(response.asInstanceOf[AnyRef], msgId)
            } catch {
                case e: Throwable => ctx.fireChannelRead(e, msgId)
            }

        }

}
