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
import cc.otavia.http.{HttpMethod, HttpRequest, HttpSerde}

import java.nio.charset.{Charset, StandardCharsets}
import java.nio.file.Path
import scala.language.unsafeNulls

sealed trait Router

case class ControllerRouter(
    method: HttpMethod,
    path: String,
    controller: Address[?],
    requestSerde: Option[HttpSerde[HttpRequest[?]]],
    responseSerde: Option[HttpSerde[?]]
) extends Router

case class StaticFilesRouter(path: String, statics: Path) extends Router

case class NotFoundRouter(statics: Option[Path] = None) extends Router

case class PlainTextRouter(
    method: HttpMethod,
    path: String,
    text: Array[String],
    charset: Charset = StandardCharsets.UTF_8
) extends Router
