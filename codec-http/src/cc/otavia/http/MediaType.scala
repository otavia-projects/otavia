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

import java.nio.charset.{Charset, StandardCharsets}
import scala.language.unsafeNulls

/** An <a href="http://tools.ietf.org/html/rfc2045">RFC 2045</a> Media Type, appropriate to describe the content type of
 *  an HTTP request or response body.
 */
enum MediaType(val tp: String, val subType: String, val charset: Charset = StandardCharsets.US_ASCII) {

    case TEXT_HTML       extends MediaType("text", "html")
    case TEXT_HTML_UTF8  extends MediaType("text", "html", StandardCharsets.UTF_8)
    case TEXT_PLAIN      extends MediaType("text", "plain")
    case TEXT_PLAIN_UTF8 extends MediaType("text", "plain", StandardCharsets.UTF_8)
    case TEXT_XML        extends MediaType("text", "xml")
    case TEXT_XML_UTF8   extends MediaType("text", "xml", StandardCharsets.UTF_8)

    case APP_XHTML      extends MediaType("application", "xhtml+xml")
    case APP_XHTML_UTF8 extends MediaType("application", "xhtml+xml", StandardCharsets.UTF_8)
    case APP_XML        extends MediaType("application", "xml")
    case APP_XML_UTF8   extends MediaType("application", "xml", StandardCharsets.UTF_8)

    case APP_OCTET_STREAM          extends MediaType("application", "octet-stream")
    case APP_X_WWW_FORM_URLENCODED extends MediaType("application", "x-www-form-urlencoded")

    case APP_JSON extends MediaType("application", "json", StandardCharsets.UTF_8)

    case MULTIPART_FORM_DATA extends MediaType("multipart", "form-data")

    val fullType: Array[Byte] = s"$tp/$subType".getBytes(StandardCharsets.US_ASCII)

    val fullName: Array[Byte] = s"$tp/$subType; charset=${charset.name()}".getBytes(StandardCharsets.US_ASCII)

}
