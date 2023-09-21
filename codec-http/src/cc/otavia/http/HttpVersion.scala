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

/** The version of HTTP or its derived protocols, such as <a
 *  href="https://en.wikipedia.org/wiki/Real_Time_Streaming_Protocol">RTSP</a> and <a
 *  href="https://en.wikipedia.org/wiki/Internet_Content_Adaptation_Protocol">ICAP</a>.
 */
enum HttpVersion(val bytes: Array[Byte]) {

    /** HTTP/1.0 */
    case HTTP_1_0 extends HttpVersion("HTTP/1.0".getBytes(StandardCharsets.US_ASCII))

    /** HTTP/1.1 */
    case HTTP_1_1 extends HttpVersion("HTTP/1.1".getBytes(StandardCharsets.US_ASCII))

    /** HTTP/2.0 */
    case HTTP_2 extends HttpVersion("HTTP/2.0".getBytes(StandardCharsets.US_ASCII))

}
