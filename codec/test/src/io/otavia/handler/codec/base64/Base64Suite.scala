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

package io.otavia.handler.codec.base64

import io.otavia.buffer.BufferAllocator
import io.netty5.handler.codec.base64.{Base64 as NettyBase64, Base64Dialect as NettyBase64Dialect}
import org.scalatest.funsuite.AnyFunSuite

import java.nio.charset.StandardCharsets
import scala.language.unsafeNulls

class Base64Suite extends AnyFunSuite {

    test("codec with STANDARD dialect") {
        val allocator = BufferAllocator.onHeapPooled()

        val dstNetty  = NettyBase64.encode(allocator.copyOf("hello world!", StandardCharsets.UTF_8))
        val dstOtavia = Base64.encode(allocator.copyOf("hello world!", StandardCharsets.UTF_8))

        assert(dstNetty == dstOtavia)

        assert(NettyBase64.decode(dstNetty) == Base64.decode(dstOtavia))
    }

    test("codec with URL_SAFE dialect") {
        val allocator = BufferAllocator.onHeapPooled()

        val dstNetty =
            NettyBase64.encode(allocator.copyOf("hello world!", StandardCharsets.UTF_8), NettyBase64Dialect.URL_SAFE)
        val dstOtavia = Base64.encode(allocator.copyOf("hello world!", StandardCharsets.UTF_8), Base64Dialect.URL_SAFE)

        assert(dstNetty == dstOtavia)

        assert(
          NettyBase64.decode(dstNetty, NettyBase64Dialect.URL_SAFE) == Base64.decode(dstOtavia, Base64Dialect.URL_SAFE)
        )
    }

    test("codec with ORDERED dialect") {
        val allocator = BufferAllocator.onHeapPooled()

        val dstNetty =
            NettyBase64.encode(allocator.copyOf("hello world!", StandardCharsets.UTF_8), NettyBase64Dialect.ORDERED)
        val dstOtavia = Base64.encode(allocator.copyOf("hello world!", StandardCharsets.UTF_8), Base64Dialect.ORDERED)

        assert(dstNetty == dstOtavia)

        assert(
          NettyBase64.decode(dstNetty, NettyBase64Dialect.ORDERED) == Base64.decode(dstOtavia, Base64Dialect.ORDERED)
        )
    }

}
