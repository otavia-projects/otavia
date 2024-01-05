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
import cc.otavia.core.message.Reply
import cc.otavia.http.ParameterSerde
import cc.otavia.http.server.Router.*
import cc.otavia.serde.Serde
import org.scalatest.funsuite.AnyFunSuite

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.charset.StandardCharsets.*
import java.nio.file.Path
import scala.collection.mutable
import scala.language.unsafeNulls

class RouterMatcherSuite extends AnyFunSuite {

    import RouterMatcherSuite.*

    test("build matcher") {
        val routers = Seq(
          plain("/plaintext", "你好, otavia!".getBytes(StandardCharsets.UTF_8)),
          static("/", Path.of("index.html")),
          static("/assets", Path.of("assets")),
          plain("/user/say/hello", "hello!".getBytes(StandardCharsets.UTF_8)),
          plain("/user/say/world", "world!".getBytes(StandardCharsets.UTF_8)),
          plain("/user/say", "hello world!".getBytes(StandardCharsets.UTF_8)),
          plain("/do/{what}", "hello".getBytes(StandardCharsets.UTF_8)),
          get("/do/{what}/{go_home}", null, doRequestSerde, null)
        )

        val matcher = new RouterMatcher(routers)

        val buf1 = Buffer.wrap("GET /plaintext HTTP/1.1".getBytes(StandardCharsets.US_ASCII))
        val buf2 = Buffer.wrap("GET / HTTP/1.1".getBytes(StandardCharsets.US_ASCII))
        val buf3 = Buffer.wrap("GET /assets HTTP/1.1".getBytes(StandardCharsets.US_ASCII))
        val buf4 = Buffer.wrap("GET /user/say/hello HTTP/1.1".getBytes(StandardCharsets.US_ASCII))
        val buf5 = Buffer.wrap("GET /user/say/world HTTP/1.1".getBytes(StandardCharsets.US_ASCII))
        val buf6 = Buffer.wrap("GET /user/say HTTP/1.1".getBytes(StandardCharsets.US_ASCII))

        val buf7 = Buffer.wrap("GET /admin HTTP/1.1".getBytes(StandardCharsets.US_ASCII))
        val buf8 = Buffer.wrap("GET https://user@pass/ HTTP/1.1".getBytes(US_ASCII))

        val buf9  = Buffer.wrap("GET /assets/js/bootstrap.min.js HTTP/1.1".getBytes(US_ASCII))
        val buf10 = Buffer.wrap("GET /do/biking HTTP/1.1".getBytes(US_ASCII))
        val buf11 = Buffer.wrap("GET /do/biking/true HTTP/1.1".getBytes(US_ASCII))
        val buf12 = Buffer.wrap("GET /do/biking/true?name=yk&age=30 HTTP/1.1".getBytes(US_ASCII))

        val r1  = matcher.choice(buf1)
        val r2  = matcher.choice(buf2)
        val r3  = matcher.choice(buf3)
        val r4  = matcher.choice(buf4)
        val r5  = matcher.choice(buf5)
        val r6  = matcher.choice(buf6)
        val r7  = matcher.choice(buf7)
        val r8  = matcher.choice(buf8)
        val r9  = matcher.choice(buf9)
        val r10 = matcher.choice(buf10)
        val r11 = matcher.choice(buf11)
        val r12 = matcher.choice(buf12)

        assert(true)
    }

}

object RouterMatcherSuite {

    private val bytesSerde = new Serde[Array[Byte]] {

        override def deserialize(in: Buffer): Array[Byte] = throw new UnsupportedOperationException()

        override def serialize(value: Array[Byte], out: Buffer): Unit = out.writeBytes(value)

    }

    case class Do(what: String, goHome: Boolean, name: Option[String], age: Option[Int])
    case class DoResult(success: Boolean) extends Reply

    class DoRequest extends HttpRequest[Nothing, DoResult]

    val doRequestSerde: HttpRequestFactory[Nothing, DoResult] =
        new HttpRequestFactory[Nothing, DoResult]() {
            override def createHttpRequest(): HttpRequest[Nothing, DoResult] = new DoRequest
        }

}
