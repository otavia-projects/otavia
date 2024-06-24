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
import cc.otavia.http.MediaType
import cc.otavia.json.JsonSerde
import cc.otavia.serde.Serde
import cc.otavia.serde.helper.StringSerde

case class HttpResponseSerde[C](contentSerde: Serde[C], mediaType: MediaType = MediaType.ANY)

object HttpResponseSerde {

    def json[C](serde: JsonSerde[C]): HttpResponseSerde[C] = HttpResponseSerde(serde, MediaType.APP_JSON)

    def stringHtml: HttpResponseSerde[String] = HttpResponseSerde[String](StringSerde.utf8, MediaType.TEXT_HTML_UTF8)

}
