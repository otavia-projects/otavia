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

package cc.otavia.http.client

import cc.otavia.core.message.Reply
import cc.otavia.http.{HttpHeaderKey, HttpHeaderValue, HttpStatus, HttpVersion}

class HttpClientResponse extends Reply {

    private[http] var version: HttpVersion                         = HttpVersion.HTTP_1_1
    private[http] var status: HttpStatus                           = HttpStatus.OK
    private[http] var headers: Map[HttpHeaderKey, HttpHeaderValue] = _
    private[http] var content: Any                                 = _

    def httpVersion: HttpVersion                         = version
    def httpStatus: HttpStatus                           = status
    def httpHeaders: Map[HttpHeaderKey, HttpHeaderValue] = headers
    def body[T]: T                                       = content.asInstanceOf[T]

}
