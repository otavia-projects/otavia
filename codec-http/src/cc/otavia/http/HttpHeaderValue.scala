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

final class HttpHeaderValue(string: String) {

    private val bytes: Array[Byte] = string.getBytes(StandardCharsets.US_ASCII).nn

    def getBytes: Array[Byte] = bytes

    def length: Int = bytes.length

    override def toString: String = string

}

object HttpHeaderValue {

    /** application/json */
    val APPLICATION_JSON = new HttpHeaderValue("application/json")

    /** application/x-www-form-urlencoded */
    val APPLICATION_X_WWW_FORM_URLENCODED = new HttpHeaderValue("application/x-www-form-urlencoded")

    /** application/octet-stream */
    val APPLICATION_OCTET_STREAM = new HttpHeaderValue("application/octet-stream")

    /** application/xhtml+xml */
    val APPLICATION_XHTML = new HttpHeaderValue("application/xhtml+xml")

    /** application/xml */
    val APPLICATION_XML = new HttpHeaderValue("application/xml")

    /** application/zstd */
    val APPLICATION_ZSTD = new HttpHeaderValue("application/zstd")

    /** attachment */
    val ATTACHMENT = new HttpHeaderValue("attachment")

    /** base64 */
    val BASE64 = new HttpHeaderValue("base64")

    /** binary */
    val BINARY = new HttpHeaderValue("binary")

    /** boundary */
    val BOUNDARY = new HttpHeaderValue("boundary")

    /** bytes */
    val BYTES = new HttpHeaderValue("bytes")

    /** charset */
    val CHARSET = new HttpHeaderValue("charset")

    /** chunked */
    val CHUNKED = new HttpHeaderValue("chunked")

    /** close */
    val CLOSE = new HttpHeaderValue("close")

    /** compress */
    val COMPRESS = new HttpHeaderValue("compress")

    /** 100-continue */
    val CONTINUE = new HttpHeaderValue("100-continue")

    /** deflate */
    val DEFLATE = new HttpHeaderValue("deflate")

    /** x-deflate */
    val X_DEFLATE = new HttpHeaderValue("x-deflate")

    /** file */
    val FILE = new HttpHeaderValue("file")

    /** filename */
    val FILENAME = new HttpHeaderValue("filename")

    val FORM_DATA = new HttpHeaderValue("form-data")

    /** gzip */
    val GZIP = new HttpHeaderValue("gzip")

    /** br */
    val BR = new HttpHeaderValue("br")

    /** snappy */
    val SNAPPY = new HttpHeaderValue("snappy")

    /** zstd */
    val ZSTD = new HttpHeaderValue("zstd")

    /** gzip,deflate */
    val GZIP_DEFLATE = new HttpHeaderValue("gzip,deflate")

    /** x-gzip */
    val X_GZIP = new HttpHeaderValue("x-gzip")

    /** identity */
    val IDENTITY = new HttpHeaderValue("identity")

    /** keep-alive */
    val KEEP_ALIVE = new HttpHeaderValue("keep-alive")

    /** max-age */
    val MAX_AGE = new HttpHeaderValue("max-age")

    /** max-stale */
    val MAX_STALE = new HttpHeaderValue("max-stale")

    /** min-fresh */
    val MIN_FRESH = new HttpHeaderValue("min-fresh")

    /** multipart/form-data */
    val MULTIPART_FORM_DATA = new HttpHeaderValue("multipart/form-data")

    /** multipart/mixed */
    val MULTIPART_MIXED = new HttpHeaderValue("multipart/mixed")

    /** must-revalidate */
    val MUST_REVALIDATE = new HttpHeaderValue("must-revalidate")

    /** name */
    val NAME = new HttpHeaderValue("name")

    /** no-cache */
    val NO_CACHE = new HttpHeaderValue("no-cache")

    /** no-store */
    val NO_STORE = new HttpHeaderValue("no-store")

    /** no-transform */
    val NO_TRANSFORM = new HttpHeaderValue("no-transform")

    /** none */
    val NONE = new HttpHeaderValue("none")

    /** 0 */
    val ZERO = new HttpHeaderValue("0")

    /** only-if-cached */
    val ONLY_IF_CACHED = new HttpHeaderValue("only-if-cached")

    /** private */
    val PRIVATE = new HttpHeaderValue("private")

    /** proxy-revalidate */
    val PROXY_REVALIDATE = new HttpHeaderValue("proxy-revalidate")

    /** public */
    val PUBLIC = new HttpHeaderValue("public")

    /** quoted-printable */
    val QUOTED_PRINTABLE = new HttpHeaderValue("quoted-printable")

    /** s-maxage */
    val S_MAXAGE = new HttpHeaderValue("s-maxage")

    /** text/css */
    val TEXT_CSS = new HttpHeaderValue("text/css")

    /** text/html */
    val TEXT_HTML = new HttpHeaderValue("text/html")

    /** text/event-stream */
    val TEXT_EVENT_STREAM = new HttpHeaderValue("text/event-stream")

    /** text/plain */
    val TEXT_PLAIN = new HttpHeaderValue("text/plain")

    /** trailers */
    val TRAILERS = new HttpHeaderValue("trailers")

    /** upgrade */
    val UPGRADE = new HttpHeaderValue("upgrade")

    /** websocket */
    val WEBSOCKET = new HttpHeaderValue("websocket")

    /** XMLHttpRequest */
    val XML_HTTP_REQUEST = new HttpHeaderValue("XMLHttpRequest")

    /** User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:126.0) Gecko/20100101 Firefox/126.0 */
    val UA_FIREFOX = new HttpHeaderValue(
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:126.0) Gecko/20100101 Firefox/126.0"
    )

    val values: Array[HttpHeaderValue] = Array(
      APPLICATION_JSON,
      APPLICATION_X_WWW_FORM_URLENCODED,
      APPLICATION_OCTET_STREAM,
      APPLICATION_XHTML,
      APPLICATION_XML,
      APPLICATION_ZSTD,
      ATTACHMENT,
      BASE64,
      BINARY,
      BOUNDARY,
      BYTES,
      CHARSET,
      CHUNKED,
      CLOSE,
      COMPRESS,
      CONTINUE,
      DEFLATE,
      X_DEFLATE,
      FILE,
      FILENAME,
      FORM_DATA,
      GZIP,
      BR,
      SNAPPY,
      ZSTD,
      GZIP_DEFLATE,
      X_GZIP,
      IDENTITY,
      KEEP_ALIVE,
      MAX_AGE,
      MAX_STALE,
      MIN_FRESH,
      MULTIPART_FORM_DATA,
      MULTIPART_MIXED,
      MUST_REVALIDATE,
      NAME,
      NO_CACHE,
      NO_STORE,
      NO_TRANSFORM,
      NONE,
      ZERO,
      ONLY_IF_CACHED,
      PRIVATE,
      PROXY_REVALIDATE,
      PUBLIC,
      QUOTED_PRINTABLE,
      S_MAXAGE,
      TEXT_CSS,
      TEXT_HTML,
      TEXT_EVENT_STREAM,
      TEXT_PLAIN,
      TRAILERS,
      UPGRADE,
      WEBSOCKET,
      XML_HTTP_REQUEST,
      UA_FIREFOX
    )

}
