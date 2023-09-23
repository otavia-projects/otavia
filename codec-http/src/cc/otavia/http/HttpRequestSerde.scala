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

class HttpRequestSerde[P, C] extends HttpSerde[HttpRequest[P, C]] {

    private var serde: Serde[C] = _

    def setContentSerde(serde: Serde[C]): this.type = {
        this.serde = serde
        this
    }

    def requireHeaders(keys: String*): this.type = {
        // TODO
        this
    }

    override def deserialize(in: Buffer): HttpRequest[P, C] = ???

    override def serialize(request: HttpRequest[P, C], out: Buffer): Unit = {
        serializeHeadLine(request, out)
        request.headers match
            case Some(value) => for ((k, v) <- value) serializeHeader(k, v, out)
            case None        =>

        request.content match
            case Some(value) =>
                // TODO: Content-Type
                // set after serialize content
                val lengthOffset = out.writerOffset + HttpHeader.Key.CONTENT_LENGTH.length + 2
                serializeHeader(HttpHeader.Key.CONTENT_LENGTH, HttpHeader.Value.CONTENT_LENGTH_PLACEHOLDER, out)
                out.writeByte('\r')
                out.writeByte('\n')

                val contentStartOffset = out.writerOffset
                serde.serialize(value, out)
                val contentLength = out.writerOffset - contentStartOffset

                out.setBytes(
                  lengthOffset,
                  contentLength.toString.getBytes(StandardCharsets.UTF_8)
                ) // set Content-Length value
            case None =>
                serializeHeader(HttpHeader.Key.CONTENT_LENGTH, HttpHeader.Value.ZERO, out)
                out.writeByte('\r')
                out.writeByte('\n')

    }

    private def serializeHeadLine(request: HttpRequest[P, C], out: Buffer): Unit = {
        out.writeBytes(request.method.bytes)
        out.writeByte(' ')
        out.writeCharSequence(request.path, StandardCharsets.UTF_8)
        out.writeByte(' ')
        out.writeBytes(request.version.bytes)
        out.writeByte('\r')
        out.writeByte('\n')
    }

    private def serializeHeader(key: Array[Byte], value: Array[Byte], out: Buffer): Unit = {
        out.writeBytes(key)
        out.writeByte(':')
        out.writeByte(' ')
        out.writeBytes(value)
        out.writeByte('\r')
        out.writeByte('\n')
    }

    private def serializeHeader(key: String, value: String, out: Buffer): Unit = {
        out.writeCharSequence(key, StandardCharsets.US_ASCII)
        out.writeByte(':')
        out.writeByte(' ')
        out.writeCharSequence(value, StandardCharsets.US_ASCII)
        out.writeByte('\r')
        out.writeByte('\n')
    }

}
