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
import cc.otavia.core.address.Address
import cc.otavia.http.*
import cc.otavia.http.MediaType.*
import cc.otavia.serde.Serde

import java.nio.charset.{Charset, StandardCharsets}
import java.nio.file.Path
import scala.language.unsafeNulls

enum Router {

    case ControllerRouter(
        method: HttpMethod,
        path: String,
        controller: Address[?],
        requestFactory: HttpRequestFactory,
        responseSerde: HttpResponseSerde[?]
    ) extends Router

    case StaticFilesRouter(path: String, root: Path) extends Router

    case NotFoundRouter(page: Option[Path] = None) extends Router

    case ConstantRouter[T](method: HttpMethod, path: String, value: T, serde: Serde[T], mediaType: MediaType)
        extends Router

    case WebsocketRouter(path: String, controller: Address[?]) extends Router

}

object Router {

    private val EMPTY_STRING: Seq[String] = Seq.empty
    private val bytesSerde = new Serde[Array[Byte]] {

        override def deserialize(in: Buffer): Array[Byte]             = throw new UnsupportedOperationException()
        override def serialize(value: Array[Byte], out: Buffer): Unit = out.writeBytes(value)

    }

    def static(path: String, root: Path): StaticFilesRouter = StaticFilesRouter(path, root)

    def controller(
        method: HttpMethod,
        path: String,
        controller: Address[?],
        requestFactory: HttpRequestFactory,
        responseSerde: HttpResponseSerde[?]
    ): ControllerRouter =
        ControllerRouter(method, path, controller, requestFactory, responseSerde)

    def websocket(path: String, controller: Address[?]): WebsocketRouter = WebsocketRouter(path, controller)

    def get(
        path: String,
        controller: Address[?],
        requestFactory: HttpRequestFactory,
        responseSerde: HttpResponseSerde[?]
    ): ControllerRouter = ControllerRouter(HttpMethod.GET, path, controller, requestFactory, responseSerde)

    def `404`(page: Option[Path]): NotFoundRouter = NotFoundRouter(page)

    def constant[T](method: HttpMethod, path: String, value: T, serde: Serde[T], media: MediaType): ConstantRouter[T] =
        ConstantRouter(method, path, value, serde, media)

    def constant[T](path: String, value: T, serde: Serde[T], media: MediaType): ConstantRouter[T] =
        constant(HttpMethod.GET, path, value, serde, media)

    def plain(path: String, bytes: Array[Byte], mediaType: MediaType = TEXT_PLAIN_UTF8): ConstantRouter[Array[Byte]] =
        constant[Array[Byte]](path, bytes, bytesSerde, mediaType)

}
