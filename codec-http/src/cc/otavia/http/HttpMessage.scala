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

sealed trait HttpMessage

case class HttpRequest[P, C](
    method: HttpMethod,
    path: String,
    version: HttpVersion = HttpVersion.HTTP_1_1,
    headers: Option[HttpHeaders] = None,
    contentType: Option[String] = None,
    params: Option[P] = None,
    content: Option[C] = None
) extends HttpMessage
