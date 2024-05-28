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

enum HttpHeaderValue(val string: String) {

    private val bytes: Array[Byte] = string.getBytes(StandardCharsets.US_ASCII).nn

    def getBytes: Array[Byte] = bytes

    def length: Int = bytes.length

    override def toString: String = string

    /** application/json */
    case APPLICATION_JSON extends HttpHeaderValue("application/json")

    /** application/x-www-form-urlencoded */
    case APPLICATION_X_WWW_FORM_URLENCODED extends HttpHeaderValue("application/x-www-form-urlencoded")

    /** application/octet-stream */
    case APPLICATION_OCTET_STREAM extends HttpHeaderValue("application/octet-stream")

    /** application/xhtml+xml */
    case APPLICATION_XHTML extends HttpHeaderValue("application/xhtml+xml")

    /** application/xml */
    case APPLICATION_XML extends HttpHeaderValue("application/xml")

    /** application/zstd */
    case APPLICATION_ZSTD extends HttpHeaderValue("application/zstd")

    /** attachment */
    case ATTACHMENT extends HttpHeaderValue("attachment")

    /** base64 */
    case BASE64 extends HttpHeaderValue("base64")

    /** binary */
    case BINARY extends HttpHeaderValue("binary")

    /** boundary */
    case BOUNDARY extends HttpHeaderValue("boundary")

    /** bytes */
    case BYTES extends HttpHeaderValue("bytes")

    /** charset */
    case CHARSET extends HttpHeaderValue("charset")

    /** chunked */
    case CHUNKED extends HttpHeaderValue("chunked")

    /** close */
    case CLOSE extends HttpHeaderValue("close")

    /** compress */
    case COMPRESS extends HttpHeaderValue("compress")

    /** 100-continue */
    case CONTINUE extends HttpHeaderValue("100-continue")

    /** deflate */
    case DEFLATE extends HttpHeaderValue("deflate")

    /** x-deflate */
    case X_DEFLATE extends HttpHeaderValue("x-deflate")

    /** file */
    case FILE extends HttpHeaderValue("file")

    /** filename */
    case FILENAME extends HttpHeaderValue("filename")

    case FORM_DATA extends HttpHeaderValue("form-data")

    /** gzip */
    case GZIP extends HttpHeaderValue("gzip")

    /** br */
    case BR extends HttpHeaderValue("br")

    /** snappy */
    case SNAPPY extends HttpHeaderValue("snappy")

    /** zstd */
    case ZSTD extends HttpHeaderValue("zstd")

    /** gzip,deflate */
    case GZIP_DEFLATE extends HttpHeaderValue("gzip,deflate")

    /** x-gzip */
    case X_GZIP extends HttpHeaderValue("x-gzip")

    /** identity */
    case IDENTITY extends HttpHeaderValue("identity")

    /** keep-alive */
    case KEEP_ALIVE extends HttpHeaderValue("keep-alive")

    /** max-age */
    case MAX_AGE extends HttpHeaderValue("max-age")

    /** max-stale */
    case MAX_STALE extends HttpHeaderValue("max-stale")

    /** min-fresh */
    case MIN_FRESH extends HttpHeaderValue("min-fresh")

    /** multipart/form-data */
    case MULTIPART_FORM_DATA extends HttpHeaderValue("multipart/form-data")

    /** multipart/mixed */
    case MULTIPART_MIXED extends HttpHeaderValue("multipart/mixed")

    /** must-revalidate */
    case MUST_REVALIDATE extends HttpHeaderValue("must-revalidate")

    /** name */
    case NAME extends HttpHeaderValue("name")

    /** no-cache */
    case NO_CACHE extends HttpHeaderValue("no-cache")

    /** no-store */
    case NO_STORE extends HttpHeaderValue("no-store")

    /** no-transform */
    case NO_TRANSFORM extends HttpHeaderValue("no-transform")

    /** none */
    case NONE extends HttpHeaderValue("none")

    /** 0 */
    case ZERO extends HttpHeaderValue("0")

    /** only-if-cached */
    case ONLY_IF_CACHED extends HttpHeaderValue("only-if-cached")

    /** private */
    case PRIVATE extends HttpHeaderValue("private")

    /** proxy-revalidate */
    case PROXY_REVALIDATE extends HttpHeaderValue("proxy-revalidate")

    /** public */
    case PUBLIC extends HttpHeaderValue("public")

    /** quoted-printable */
    case QUOTED_PRINTABLE extends HttpHeaderValue("quoted-printable")

    /** s-maxage */
    case S_MAXAGE extends HttpHeaderValue("s-maxage")

    /** text/css */
    case TEXT_CSS extends HttpHeaderValue("text/css")

    /** text/html */
    case TEXT_HTML extends HttpHeaderValue("text/html")

    /** text/event-stream */
    case TEXT_EVENT_STREAM extends HttpHeaderValue("text/event-stream")

    /** text/plain */
    case TEXT_PLAIN extends HttpHeaderValue("text/plain")

    /** trailers */
    case TRAILERS extends HttpHeaderValue("trailers")

    /** upgrade */
    case UPGRADE extends HttpHeaderValue("upgrade")

    /** websocket */
    case WEBSOCKET extends HttpHeaderValue("websocket")

    /** XMLHttpRequest */
    case XML_HTTP_REQUEST extends HttpHeaderValue("XMLHttpRequest")

    /** User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:126.0) Gecko/20100101 Firefox/126.0 */
    case UA_FIREFOX
        extends HttpHeaderValue("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:126.0) Gecko/20100101 Firefox/126.0")

}
