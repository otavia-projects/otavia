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

import cc.otavia.core.channel.ChannelHandlerMaskSuite.*
import cc.otavia.core.channel.internal.ChannelHandlerMask
import cc.otavia.core.channel.{ChannelHandlerContext, Skip}
import cc.otavia.core.stack.ChannelFuture
import org.scalatest.funsuite.AnyFunSuiteLike

import java.net.SocketAddress
import java.nio.file.attribute.FileAttribute
import java.nio.file.{OpenOption, Path}
import scala.language.unsafeNulls

class ChannelHandlerMaskSuite extends AnyFunSuiteLike {

    import ChannelHandlerMaskSuite.*

    test("Skip annotation") {
        val channelActive = classOf[TestHandler].getMethod("channelActive", classOf[ChannelHandlerContext])
        val skip          = channelActive.isAnnotationPresent(classOf[Skip])
        val not = classOf[TestHandler]
            .getMethod("channelRead", classOf[ChannelHandlerContext], classOf[AnyRef])
            .isAnnotationPresent(classOf[Skip])
        assert(skip)
        assert(!not)

        assert(
          classOf[TestHandler].getMethod("flush", classOf[ChannelHandlerContext]).isAnnotationPresent(classOf[Skip])
        )
        assert(
          classOf[TestHandler]
              .getMethod("write", classOf[ChannelHandlerContext], classOf[AnyRef], classOf[Long])
              .isAnnotationPresent(classOf[Skip])
        )

        assert(
          classOf[TestHandler]
              .getMethod(
                "connect",
                classOf[ChannelHandlerContext],
                classOf[SocketAddress],
                classOf[Option[SocketAddress]],
                classOf[ChannelFuture]
              )
              .isAnnotationPresent(classOf[Skip])
        )

        assert(
          classOf[TestHandler]
              .getMethod(
                "open",
                classOf[ChannelHandlerContext],
                classOf[Path],
                classOf[Seq[OpenOption]],
                classOf[Seq[FileAttribute[?]]],
                classOf[ChannelFuture]
              )
              .isAnnotationPresent(classOf[Skip])
        )
    }

    test("default handler mask has no inbound/outbound bits set") {
        // All methods on ChannelHandler trait have @Skip, so mask should have all bits cleared
        val m = ChannelHandlerMask.mask(classOf[ChannelHandler])
        assert((m & ChannelHandlerMask.MASK_CHANNEL_READ) == 0)
        assert((m & ChannelHandlerMask.MASK_CHANNEL_ACTIVE) == 0)
        assert((m & ChannelHandlerMask.MASK_WRITE) == 0)
        assert((m & ChannelHandlerMask.MASK_FLUSH) == 0)
        assert((m & ChannelHandlerMask.MASK_BIND) == 0)
        assert((m & ChannelHandlerMask.MASK_CONNECT) == 0)
    }

    test("handler overriding channelRead has MASK_CHANNEL_READ set") {
        val m = ChannelHandlerMask.mask(classOf[TestHandler])
        assert((m & ChannelHandlerMask.MASK_CHANNEL_READ) != 0)
        // channelActive has @Skip in TestHandler
        assert((m & ChannelHandlerMask.MASK_CHANNEL_ACTIVE) == 0)
    }

    test("isInbound and isOutbound correct") {
        // Default handler: all @Skip → not inbound, not outbound
        assert(!ChannelHandlerMask.isInbound(classOf[ChannelHandler]))
        assert(!ChannelHandlerMask.isOutbound(classOf[ChannelHandler]))
        // TestHandler overrides channelRead → is inbound
        assert(ChannelHandlerMask.isInbound(classOf[TestHandler]))
    }

    test("mask is cached (same class same value)") {
        val m1 = ChannelHandlerMask.mask(classOf[TestHandler])
        val m2 = ChannelHandlerMask.mask(classOf[TestHandler])
        assert(m1 == m2)
    }

}

object ChannelHandlerMaskSuite {
    class TestHandler extends ChannelHandler {

        @Skip
        override def channelActive(ctx: ChannelHandlerContext): Unit = {
            super.channelActive(ctx)
        }

        override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef): Unit = {
            super.channelRead(ctx, msg)
        }

    }

}
