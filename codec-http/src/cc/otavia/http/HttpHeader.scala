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

object HttpHeader {

    object Key {

        /** accept */
        val ACCEPT: Array[Byte] = "accept".getBytes(StandardCharsets.US_ASCII)

        /** accept-charset */
        val ACCEPT_CHARSET: Array[Byte] = "accept-charset".getBytes(StandardCharsets.US_ASCII)

        /** accept-encoding */
        val ACCEPT_ENCODING: Array[Byte] = "accept-encoding".getBytes(StandardCharsets.US_ASCII)

        /** accept-language */
        val ACCEPT_LANGUAGE: Array[Byte] = "accept-language".getBytes(StandardCharsets.US_ASCII)

        /** accept-ranges */
        val ACCEPT_RANGES: Array[Byte] = "accept-ranges".getBytes(StandardCharsets.US_ASCII)

        /** accept-patch */
        val ACCEPT_PATCH: Array[Byte] = "accept-patch".getBytes(StandardCharsets.US_ASCII)

        /** access-control-allow-credentials */
        val ACCESS_CONTROL_ALLOW_CREDENTIALS: Array[Byte] =
            "access-control-allow-credentials".getBytes(StandardCharsets.US_ASCII)

        /** access-control-allow-headers */
        val ACCESS_CONTROL_ALLOW_HEADERS: Array[Byte] =
            "access-control-allow-headers".getBytes(StandardCharsets.US_ASCII)

        /** access-control-allow-methods */
        val ACCESS_CONTROL_ALLOW_METHODS: Array[Byte] =
            "access-control-allow-methods".getBytes(StandardCharsets.US_ASCII)

        /** access-control-allow-origin */
        val ACCESS_CONTROL_ALLOW_ORIGIN: Array[Byte] = "access-control-allow-origin".getBytes(StandardCharsets.US_ASCII)

        /** access-control-allow-private-network */
        val ACCESS_CONTROL_ALLOW_PRIVATE_NETWORK: Array[Byte] =
            "access-control-allow-private-network".getBytes(StandardCharsets.US_ASCII)

        /** access-control-expose-headers */
        val ACCESS_CONTROL_EXPOSE_HEADERS: Array[Byte] =
            "access-control-expose-headers".getBytes(StandardCharsets.US_ASCII)

        /** access-control-max-age */
        val ACCESS_CONTROL_MAX_AGE: Array[Byte] = "access-control-max-age".getBytes(StandardCharsets.US_ASCII)

        /** access-control-request-headers */
        val ACCESS_CONTROL_REQUEST_HEADERS: Array[Byte] =
            "access-control-request-headers".getBytes(StandardCharsets.US_ASCII)

        /** access-control-request-method */
        val ACCESS_CONTROL_REQUEST_METHOD: Array[Byte] =
            "access-control-request-method".getBytes(StandardCharsets.US_ASCII)

        /** access-control-request-private-network */
        val ACCESS_CONTROL_REQUEST_PRIVATE_NETWORK: Array[Byte] =
            "access-control-request-private-network".getBytes(StandardCharsets.US_ASCII)

        /** age */
        val AGE: Array[Byte] = "age".getBytes(StandardCharsets.US_ASCII)

        /** allow */
        val ALLOW: Array[Byte] = "allow".getBytes(StandardCharsets.US_ASCII)

        /** authorization */
        val AUTHORIZATION: Array[Byte] = "authorization".getBytes(StandardCharsets.US_ASCII)

        /** cache-control */
        val CACHE_CONTROL: Array[Byte] = "cache-control".getBytes(StandardCharsets.US_ASCII)

        /** connection */
        val CONNECTION: Array[Byte] = "connection".getBytes(StandardCharsets.US_ASCII)

        /** content-base */
        val CONTENT_BASE: Array[Byte] = "content-base".getBytes(StandardCharsets.US_ASCII)

        /** content-encoding */
        val CONTENT_ENCODING: Array[Byte] = "content-encoding".getBytes(StandardCharsets.US_ASCII)

        /** content-language */
        val CONTENT_LANGUAGE: Array[Byte] = "content-language".getBytes(StandardCharsets.US_ASCII)

        /** content-length */
        val CONTENT_LENGTH: Array[Byte] = "content-length".getBytes(StandardCharsets.US_ASCII)

        /** content-location */
        val CONTENT_LOCATION: Array[Byte] = "content-location".getBytes(StandardCharsets.US_ASCII)

        /** content-transfer-encoding */
        val CONTENT_TRANSFER_ENCODING: Array[Byte] = "content-transfer-encoding".getBytes(StandardCharsets.US_ASCII)

        /** content-disposition */
        val CONTENT_DISPOSITION: Array[Byte] = "content-disposition".getBytes(StandardCharsets.US_ASCII)

        /** content-md5 */
        val CONTENT_MD5: Array[Byte] = "content-md5".getBytes(StandardCharsets.US_ASCII)

        /** content-range */
        val CONTENT_RANGE: Array[Byte] = "content-range".getBytes(StandardCharsets.US_ASCII)

        /** content-security-policy */
        val CONTENT_SECURITY_POLICY: Array[Byte] = "content-security-policy".getBytes(StandardCharsets.US_ASCII)

        /** content-type */
        val CONTENT_TYPE: Array[Byte] = "content-type".getBytes(StandardCharsets.US_ASCII)

        /** cookie */
        val COOKIE: Array[Byte] = "cookie".getBytes(StandardCharsets.US_ASCII)

        /** date */
        val DATE: Array[Byte] = "date".getBytes(StandardCharsets.US_ASCII)

        /** dnt */
        val DNT: Array[Byte] = "dnt".getBytes(StandardCharsets.US_ASCII)

        /** etag */
        val ETAG: Array[Byte] = "etag".getBytes(StandardCharsets.US_ASCII)

        /** expect */
        val EXPECT: Array[Byte] = "expect".getBytes(StandardCharsets.US_ASCII)

        /** expires */
        val EXPIRES: Array[Byte] = "expires".getBytes(StandardCharsets.US_ASCII)

        /** from */
        val FROM: Array[Byte] = "from".getBytes(StandardCharsets.US_ASCII)

        /** host */
        val HOST: Array[Byte] = "host".getBytes(StandardCharsets.US_ASCII)

        /** if-match */
        val IF_MATCH: Array[Byte] = "if-match".getBytes(StandardCharsets.US_ASCII)

        /** if-modified-since */
        val IF_MODIFIED_SINCE: Array[Byte] = "if-modified-since".getBytes(StandardCharsets.US_ASCII)

        /** if-none-match */
        val IF_NONE_MATCH: Array[Byte] = "if-none-match".getBytes(StandardCharsets.US_ASCII)

        /** if-range */
        val IF_RANGE: Array[Byte] = "if-range".getBytes(StandardCharsets.US_ASCII)

        /** if-unmodified-since */
        val IF_UNMODIFIED_SINCE: Array[Byte] = "if-unmodified-since".getBytes(StandardCharsets.US_ASCII)

        /** keep-alive */
        val KEEP_ALIVE: Array[Byte] = "keep-alive".getBytes(StandardCharsets.US_ASCII)

        /** last-modified */
        val LAST_MODIFIED: Array[Byte] = "last-modified".getBytes(StandardCharsets.US_ASCII)

        /** location */
        val LOCATION: Array[Byte] = "location".getBytes(StandardCharsets.US_ASCII)

        /** max-forwards */
        val MAX_FORWARDS: Array[Byte] = "max-forwards".getBytes(StandardCharsets.US_ASCII)

        /** origin */
        val ORIGIN: Array[Byte] = "origin".getBytes(StandardCharsets.US_ASCII)

        /** pragma */
        val PRAGMA: Array[Byte] = "pragma".getBytes(StandardCharsets.US_ASCII)

        /** proxy-authenticate */
        val PROXY_AUTHENTICATE: Array[Byte] = "proxy-authenticate".getBytes(StandardCharsets.US_ASCII)

        /** proxy-authorization */
        val PROXY_AUTHORIZATION: Array[Byte] = "proxy-authorization".getBytes(StandardCharsets.US_ASCII)

        /** proxy-connection */
        val PROXY_CONNECTION: Array[Byte] = "proxy-connection".getBytes(StandardCharsets.US_ASCII)

        /** range */
        val RANGE: Array[Byte] = "range".getBytes(StandardCharsets.US_ASCII)

        /** referer */
        val REFERER: Array[Byte] = "referer".getBytes(StandardCharsets.US_ASCII)

        /** retry-after */
        val RETRY_AFTER: Array[Byte] = "retry-after".getBytes(StandardCharsets.US_ASCII)

        /** sec-websocket-protocol */
        val SEC_WEBSOCKET_PROTOCOL: Array[Byte] = "sec-websocket-protocol".getBytes(StandardCharsets.US_ASCII)

        /** sec-websocket-version */
        val SEC_WEBSOCKET_VERSION: Array[Byte] = "sec-websocket-version".getBytes(StandardCharsets.US_ASCII)

        /** sec-websocket-key */
        val SEC_WEBSOCKET_KEY: Array[Byte] = "sec-websocket-key".getBytes(StandardCharsets.US_ASCII)

        /** Sec-WebSocket-Origin */
        val SEC_WEBSOCKET_ORIGIN: Array[Byte] = "Sec-WebSocket-Origin".getBytes(StandardCharsets.US_ASCII)

        /** sec-websocket-accept */
        val SEC_WEBSOCKET_ACCEPT: Array[Byte] = "sec-websocket-accept".getBytes(StandardCharsets.US_ASCII)

        /** sec-websocket-extensions */
        val SEC_WEBSOCKET_EXTENSIONS: Array[Byte] = "sec-websocket-extensions".getBytes(StandardCharsets.US_ASCII)

        /** server */
        val SERVER: Array[Byte] = "server".getBytes(StandardCharsets.US_ASCII)

        /** set-cookie */
        val SET_COOKIE: Array[Byte] = "set-cookie".getBytes(StandardCharsets.US_ASCII)

        /** set-cookie2 */
        val SET_COOKIE2: Array[Byte] = "set-cookie2".getBytes(StandardCharsets.US_ASCII)

        /** te */
        val TE: Array[Byte] = "te".getBytes(StandardCharsets.US_ASCII)

        /** trailer */
        val TRAILER: Array[Byte] = "trailer".getBytes(StandardCharsets.US_ASCII)

        /** transfer-encoding */
        val TRANSFER_ENCODING: Array[Byte] = "transfer-encoding".getBytes(StandardCharsets.US_ASCII)

        /** upgrade */
        val UPGRADE: Array[Byte] = "upgrade".getBytes(StandardCharsets.US_ASCII)

        /** upgrade-insecure-requests */
        val UPGRADE_INSECURE_REQUESTS: Array[Byte] = "upgrade-insecure-requests".getBytes(StandardCharsets.US_ASCII)

        /** user-agent */
        val USER_AGENT: Array[Byte] = "user-agent".getBytes(StandardCharsets.US_ASCII)

        /** vary */
        val VARY: Array[Byte] = "vary".getBytes(StandardCharsets.US_ASCII)

        /** via */
        val VIA: Array[Byte] = "via".getBytes(StandardCharsets.US_ASCII)

        /** warning */
        val WARNING: Array[Byte] = "warning".getBytes(StandardCharsets.US_ASCII)

        /** www-authenticate */
        val WWW_AUTHENTICATE: Array[Byte] = "www-authenticate".getBytes(StandardCharsets.US_ASCII)

        /** x-frame-options */
        val X_FRAME_OPTIONS: Array[Byte] = "x-frame-options".getBytes(StandardCharsets.US_ASCII)

        /** x-requested-with */
        val X_REQUESTED_WITH: Array[Byte] = "x-requested-with".getBytes(StandardCharsets.US_ASCII)

        /** alt-svc */
        val ALT_SVC: Array[Byte] = "alt-svc".getBytes(StandardCharsets.US_ASCII)

    }

    object Value {

        /** application/json */
        val APPLICATION_JSON: String = "application/json"

        /** application/x-www-form-urlencoded */
        val APPLICATION_X_WWW_FORM_URLENCODED: String = "application/x-www-form-urlencoded"

        /** application/octet-stream */
        val APPLICATION_OCTET_STREAM: String = "application/octet-stream"

        /** application/xhtml+xml */
        val APPLICATION_XHTML: String = "application/xhtml+xml"

        /** application/xml */
        val APPLICATION_XML: String = "application/xml"

        /** application/zstd */
        val APPLICATION_ZSTD: String = "application/zstd"

        /** attachment */
        val ATTACHMENT: String = "attachment"

        /** base64 */
        val BASE64: String = "base64"

        /** binary */
        val BINARY: String = "binary"

        /** boundary */
        val BOUNDARY: String = "boundary"

        /** bytes */
        val BYTES: String = "bytes"

        /** charset */
        val CHARSET: String = "charset"

        /** chunked */
        val CHUNKED: String = "chunked"

        /** close */
        val CLOSE: String = "close"

        /** compress */
        val COMPRESS: String = "compress"

        /** 100-continue */
        val CONTINUE: String = "100-continue"

        /** deflate */
        val DEFLATE: String = "deflate"

        /** x-deflate */
        val X_DEFLATE: String = "x-deflate"

        /** file */
        val FILE: String = "file"

        /** filename */
        val FILENAME: String = "filename"

        /** form-data */
        val FORM_DATA: String = "form-data"

        /** gzip */
        val GZIP: String = "gzip"

        /** br */
        val BR: String = "br"

        /** snappy */
        val SNAPPY: String = "snappy"

        /** zstd */
        val ZSTD: String = "zstd"

        /** gzip,deflate */
        val GZIP_DEFLATE: String = "gzip,deflate"

        /** x-gzip */
        val X_GZIP: String = "x-gzip"

        /** identity */
        val IDENTITY: String = "identity"

        /** keep-alive */
        val KEEP_ALIVE: String = "keep-alive"

        /** max-age */
        val MAX_AGE: String = "max-age"

        /** max-stale */
        val MAX_STALE: String = "max-stale"

        /** min-fresh */
        val MIN_FRESH: String = "min-fresh"

        /** multipart/form-data */
        val MULTIPART_FORM_DATA: String = "multipart/form-data"

        /** multipart/mixed */
        val MULTIPART_MIXED: String = "multipart/mixed"

        /** must-revalidate */
        val MUST_REVALIDATE: String = "must-revalidate"

        /** name */
        val NAME: String = "name"

        /** no-cache */
        val NO_CACHE: String = "no-cache"

        /** no-store */
        val NO_STORE: String = "no-store"

        /** no-transform */
        val NO_TRANSFORM: String = "no-transform"

        /** none */
        val NONE: String = "none"

        /** 0 */
        val ZERO: Array[Byte] = "0".getBytes(StandardCharsets.US_ASCII)

        /** only-if-cached */
        val ONLY_IF_CACHED: String = "only-if-cached"

        /** private */
        val PRIVATE: String = "private"

        /** proxy-revalidate */
        val PROXY_REVALIDATE: String = "proxy-revalidate"

        /** public */
        val PUBLIC: String = "public"

        /** quoted-printable */
        val QUOTED_PRINTABLE: String = "quoted-printable"

        /** s-maxage */
        val S_MAXAGE: String = "s-maxage"

        /** text/css */
        val TEXT_CSS: String = "text/css"

        /** text/html */
        val TEXT_HTML: String = "text/html"

        /** text/event-stream */
        val TEXT_EVENT_STREAM: String = "text/event-stream"

        /** text/plain */
        val TEXT_PLAIN: String = "text/plain"

        /** trailers */
        val TRAILERS: String = "trailers"

        /** upgrade */
        val UPGRADE: String = "upgrade"

        /** websocket */
        val WEBSOCKET: String = "websocket"

        /** XMLHttpRequest */
        val XML_HTTP_REQUEST: String = "XMLHttpRequest"

        val CONTENT_LENGTH_PLACEHOLDER_LENGTH: Int  = 19
        val CONTENT_LENGTH_PLACEHOLDER: Array[Byte] = new Array[Byte](CONTENT_LENGTH_PLACEHOLDER_LENGTH)

    }

}
