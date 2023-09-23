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

package cc.otavia.http.server

import cc.otavia.buffer.Buffer
import cc.otavia.http.server.Router.*
import org.scalatest.funsuite.AnyFunSuite

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import scala.language.unsafeNulls

class RouterMatcherSuite extends AnyFunSuite {

    test("build matcher") {
        val routers = Seq(
          plain("/plaintext", "你好, otavia!".getBytes(StandardCharsets.UTF_8)),
          static("/", Path.of("index.html")),
          static("/assets", Path.of("assets")),
          plain("/user/say/hello", "hello!".getBytes(StandardCharsets.UTF_8)),
          plain("/user/say/world", "world!".getBytes(StandardCharsets.UTF_8)),
          plain("/user/say", "hello world!".getBytes(StandardCharsets.UTF_8))
        )

        val matcher = new RouterMatcher(routers)

        val buf1 = Buffer.wrap("GET /plaintext HTTP/1.1".getBytes(StandardCharsets.US_ASCII))
        val buf2 = Buffer.wrap("GET / HTTP/1.1".getBytes(StandardCharsets.US_ASCII))
        val buf3 = Buffer.wrap("GET /assets HTTP/1.1".getBytes(StandardCharsets.US_ASCII))
        val buf4 = Buffer.wrap("GET /user/say/hello HTTP/1.1".getBytes(StandardCharsets.US_ASCII))
        val buf5 = Buffer.wrap("GET /user/say/world HTTP/1.1".getBytes(StandardCharsets.US_ASCII))
        val buf6 = Buffer.wrap("GET /user/say HTTP/1.1".getBytes(StandardCharsets.US_ASCII))

        val buf7 = Buffer.wrap("GET /admin HTTP/1.1".getBytes(StandardCharsets.US_ASCII))

        val r1 = matcher.choice(buf1)
        val r2 = matcher.choice(buf2)
        val r3 = matcher.choice(buf3)
        val r4 = matcher.choice(buf4)
        val r5 = matcher.choice(buf5)
        val r6 = matcher.choice(buf6)
        val r7 = matcher.choice(buf7)

        assert(true)
    }

}
