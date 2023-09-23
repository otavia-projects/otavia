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

object HttpUtils {

    final def parseMethod(buffer: Buffer): HttpMethod = if (buffer.skipIfNexts(HttpMethod.GET.bytes)) { // GET method
        HttpMethod.GET
    } else if (buffer.skipIfNexts(HttpMethod.POST.bytes)) { // POST method
        HttpMethod.POST
    } else if (buffer.skipIfNexts(HttpMethod.PUT.bytes)) { // PUT method
        HttpMethod.PUT
    } else if (buffer.skipIfNexts(HttpMethod.OPTIONS.bytes)) { // OPTIONS method
        HttpMethod.OPTIONS
    } else if (buffer.skipIfNexts(HttpMethod.HEAD.bytes)) { // HEAD method
        HttpMethod.HEAD
    } else if (buffer.skipIfNexts(HttpMethod.PATCH.bytes)) { // PATCH method
        HttpMethod.PATCH
    } else if (buffer.skipIfNexts(HttpMethod.DELETE.bytes)) { // DELETE method
        HttpMethod.DELETE
    } else if (buffer.skipIfNexts(HttpMethod.TRACE.bytes)) { // TRACE method
        HttpMethod.TRACE
    } else if (buffer.skipIfNexts(HttpMethod.CONNECT.bytes)) { // CONNECT method
        HttpMethod.CONNECT
    } else throw new RuntimeException()

}
