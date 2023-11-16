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

package cc.otavia.buffer

import cc.otavia.buffer.BytesUtil.bytes8Long
import org.scalatest.funsuite.AnyFunSuiteLike

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import scala.language.unsafeNulls

class BytesUtilSuite extends AnyFunSuiteLike {

    test("ignore case equal") {

        ('A' to 'Z').zip('a' to 'z').foreach { case (u, l) =>
            assert(BytesUtil.ignoreCaseEqual(u.toByte, u.toByte))
            assert(BytesUtil.ignoreCaseEqual(u.toByte, l.toByte))
        }

        assert(!BytesUtil.ignoreCaseEqual('@', '`'))

        assert('A'.toInt - '!' == 32)
        assert(!BytesUtil.ignoreCaseEqual('A', '!'))

    }

    test("four bytes to int") {
        val arr        = "otavia".getBytes(StandardCharsets.US_ASCII)
        val byteBuffer = ByteBuffer.wrap(arr)

        assert(BytesUtil.bytes4Int(arr(0), arr(1), arr(2), arr(3)) == byteBuffer.getInt(0))
    }

    test("eight bytes to long") {
        val arr        = "hello otavia-buffer".getBytes(StandardCharsets.US_ASCII)
        val byteBuffer = ByteBuffer.wrap(arr)

        val a1 = bytes8Long(arr(0), arr(1), arr(2), arr(3), arr(4), arr(5), arr(6), arr(7))
        val a2 = bytes8Long(arr(8), arr(9), arr(10), arr(11), arr(12), arr(13), arr(14), arr(15))

        assert(a1 == byteBuffer.getLong(0))
        assert(a2 == byteBuffer.getLong(8))
    }

}
