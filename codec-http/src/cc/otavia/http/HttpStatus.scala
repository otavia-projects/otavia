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

import java.nio.charset.StandardCharsets
import scala.language.unsafeNulls

/** The status of http response. */
enum HttpStatus(code: Int, phrase: String) {

    case OK              extends HttpStatus(200, "OK")
    case NOT_CONTENT     extends HttpStatus(204, "Not Content")
    case PARTIAL_CONTENT extends HttpStatus(206, "Partial Content")

    case MOVED_PERMANENTLY extends HttpStatus(301, "Moved Permanently")
    case FOUND             extends HttpStatus(302, "Found")
    case SEE_OTHER         extends HttpStatus(303, "See Other")
    case NOT_MODIFIED      extends HttpStatus(304, "Not Modified")

    case BAD_REQUEST  extends HttpStatus(400, "Bad Request")
    case UNAUTHORIZED extends HttpStatus(401, "Unauthorized")
    case FORBIDDEN    extends HttpStatus(403, "Forbidden")
    case NOT_FOUND    extends HttpStatus(404, "Not Found")

    case INTERNAL_SERVER_ERROR extends HttpStatus(500, "Internal Server Error")
    case SERVICE_UNAVAILABLE   extends HttpStatus(503, "Service Unavailable")

    val bytes: Array[Byte]     = s"$code $phrase".getBytes(StandardCharsets.US_ASCII)
    val bytesCRCL: Array[Byte] = s"$code $phrase\r\n".getBytes(StandardCharsets.US_ASCII)

}
