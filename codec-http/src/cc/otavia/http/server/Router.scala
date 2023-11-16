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

import cc.otavia.core.address.Address
import cc.otavia.http.*
import cc.otavia.serde.Serde

import java.nio.charset.{Charset, StandardCharsets}
import java.nio.file.Path
import scala.language.unsafeNulls

enum Router {

    case ControllerRouter(
        method: HttpMethod,
        path: String,
        controller: Address[?],
        headers: Seq[String],
        paramsSerde: Option[ParameterSerde[?]],
        contentSerde: Option[ContentSerde[?]],
        responseSerde: Option[HttpSerde[?]]
    ) extends Router

    case StaticFilesRouter(path: String, root: Path) extends Router

    case NotFoundRouter(page: Option[Path] = None) extends Router

    case ConstantRouter[T](method: HttpMethod, path: String, value: T, serde: Serde[T], mediaType: MediaType)
        extends Router

}

object Router {

    private val EMPTY_STRING: Seq[String] = Seq.empty

    def static(path: String, root: Path): StaticFilesRouter = StaticFilesRouter(path, root)

    def controller(
        method: HttpMethod,
        path: String,
        controller: Address[?],
        headers: Seq[String] = EMPTY_STRING,
        paramsSerde: Option[ParameterSerde[?]] = None,
        contentSerde: Option[ContentSerde[?]] = None,
        responseSerde: Option[HttpSerde[?]] = None
    ): ControllerRouter = ControllerRouter(method, path, controller, headers, paramsSerde, contentSerde, responseSerde)

    def `404`(page: Option[Path]): NotFoundRouter = NotFoundRouter(page)

    def constant[T](method: HttpMethod, path: String, value: T, serde: Serde[T], media: MediaType): ConstantRouter[T] =
        ConstantRouter(method, path, value, serde, media)

}
