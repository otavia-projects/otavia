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

package cc.otavia.http

import cc.otavia.buffer.Buffer
import cc.otavia.serde.Serde

import java.nio.charset.StandardCharsets
import scala.language.unsafeNulls

class HttpRequestSerde[C] extends HttpSerde[HttpRequest[C]] {

    private var serde: Serde[C] = _

    def setContentSerde(serde: Serde[C]): this.type = {
        this.serde = serde
        this
    }

    def requireHeaders(keys: String*): this.type = {
        // TODO
        this
    }

    override def deserialize(in: Buffer): HttpRequest[C] = ???

    override def serialize(request: HttpRequest[C], out: Buffer): Unit = {
        serializeHeadLine(request, out)
        for ((k, v) <- request.headers) {
            serializeHeader(k, v, out)
        }
        serializeHeader("Content-Length", "          ", out) // set after serialize content
        val lengthOffset = out.writerOffset - 12
        out.writeByte('\r')
        out.writeByte('\n')

        val contentStartOffset = out.writerOffset
        serde.serialize(request.content, out)
        val contentLength = out.writerOffset - contentStartOffset

        out.setBytes(lengthOffset, contentLength.toString.getBytes(StandardCharsets.UTF_8)) // set Content-Length value

    }

    inline private def serializeHeadLine(request: HttpRequest[C], out: Buffer): Unit = {
        out.writeBytes(request.method.value)
        out.writeByte(' ')
        out.writeCharSequence(request.path, StandardCharsets.UTF_8)
        out.writeByte(' ')
        out.writeCharSequence(request.version.value, StandardCharsets.UTF_8)
        out.writeByte('\r')
        out.writeByte('\n')
    }

    inline private def serializeHeader(key: String, value: String, out: Buffer): Unit = {
        out.writeCharSequence(key)
        out.writeByte(':')
        out.writeByte(' ')
        out.writeCharSequence(value)
        out.writeByte('\r')
        out.writeByte('\n')
    }

}
