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

package cc.otavia.mysql

import cc.otavia.buffer.pool.AdaptiveBuffer
import cc.otavia.core.channel.ChannelHandlerContext
import cc.otavia.core.stack.ChannelFuture
import cc.otavia.handler.codec.ByteToMessageCodec

import java.net.SocketAddress

class MysqlDriver extends ByteToMessageCodec {

    override protected def decode(ctx: ChannelHandlerContext, input: AdaptiveBuffer): Unit = ???

    override protected def encode(ctx: ChannelHandlerContext, output: AdaptiveBuffer, msg: AnyRef, msgId: Long): Unit =
        ???

    override def connect(
        ctx: ChannelHandlerContext,
        remote: SocketAddress,
        local: Option[SocketAddress],
        future: ChannelFuture
    ): ChannelFuture = {
        super.connect(ctx, remote, local, future)
        
    }

}
