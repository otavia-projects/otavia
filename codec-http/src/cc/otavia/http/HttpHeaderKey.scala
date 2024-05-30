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

/** http header key */
final class HttpHeaderKey(string: String) {

    private val bytes: Array[Byte] = string.getBytes(StandardCharsets.US_ASCII).nn

    def getBytes: Array[Byte] = bytes

    def length: Int = bytes.length

    override def toString: String = string

}

object HttpHeaderKey {

    /** accept */
    val ACCEPT = new HttpHeaderKey("accept")

    /** accept-charset */
    val ACCEPT_CHARSET = new HttpHeaderKey("accept-charset")

    /** accept-encoding */
    val ACCEPT_ENCODING = new HttpHeaderKey("accept-encoding")

    /** accept-language */
    val ACCEPT_LANGUAGE = new HttpHeaderKey("accept-language")

    /** accept-ranges */
    val ACCEPT_RANGES = new HttpHeaderKey("accept-ranges")

    /** accept-patch */
    val ACCEPT_PATCH = new HttpHeaderKey("accept-patch")

    /** access-control-allow-credentials */
    val ACCESS_CONTROL_ALLOW_CREDENTIALS = new HttpHeaderKey("access-control-allow-credentials")

    /** access-control-allow-headers */
    val ACCESS_CONTROL_ALLOW_HEADERS = new HttpHeaderKey("access-control-allow-headers")

    /** access-control-allow-methods */
    val ACCESS_CONTROL_ALLOW_METHODS = new HttpHeaderKey("access-control-allow-methods")

    /** access-control-allow-origin */
    val ACCESS_CONTROL_ALLOW_ORIGIN = new HttpHeaderKey("access-control-allow-origin")

    /** access-control-allow-private-network */
    val ACCESS_CONTROL_ALLOW_PRIVATE_NETWORK = new HttpHeaderKey("access-control-allow-private-network")

    /** access-control-expose-headers */
    val ACCESS_CONTROL_EXPOSE_HEADERS = new HttpHeaderKey("access-control-expose-headers")

    /** access-control-max-age */
    val ACCESS_CONTROL_MAX_AGE = new HttpHeaderKey("access-control-max-age")

    /** access-control-request-headers */
    val ACCESS_CONTROL_REQUEST_HEADERS = new HttpHeaderKey("access-control-request-headers")

    /** access-control-request-method */
    val ACCESS_CONTROL_REQUEST_METHOD = new HttpHeaderKey("access-control-request-method")

    /** access-control-request-private-network */
    val ACCESS_CONTROL_REQUEST_PRIVATE_NETWORK = new HttpHeaderKey("access-control-request-private-network")

    /** age */
    val AGE = new HttpHeaderKey("age")

    /** allow */
    val ALLOW = new HttpHeaderKey("allow")

    /** authorization */
    val AUTHORIZATION = new HttpHeaderKey("authorization")

    /** cache-control */
    val CACHE_CONTROL = new HttpHeaderKey("cache-control")

    /** connection */
    val CONNECTION = new HttpHeaderKey("connection")

    /** content-base */
    val CONTENT_BASE = new HttpHeaderKey("content-base")

    /** content-encoding */
    val CONTENT_ENCODING = new HttpHeaderKey("content-encoding")

    /** content-language */
    val CONTENT_LANGUAGE = new HttpHeaderKey("content-language")

    /** content-length */
    val CONTENT_LENGTH = new HttpHeaderKey("Content-Length")

    /** content-location */
    val CONTENT_LOCATION = new HttpHeaderKey("content-location")

    /** content-transfer-encoding */
    val CONTENT_TRANSFER_ENCODING = new HttpHeaderKey("content-transfer-encoding")

    /** content-disposition */
    val CONTENT_DISPOSITION = new HttpHeaderKey("content-disposition")

    /** content-md5 */
    val CONTENT_MD5 = new HttpHeaderKey("content-md5")

    /** content-range */
    val CONTENT_RANGE = new HttpHeaderKey("content-range")

    /** content-security-policy */
    val CONTENT_SECURITY_POLICY = new HttpHeaderKey("content-security-policy")

    /** content-type */
    val CONTENT_TYPE = new HttpHeaderKey("Content-Type")

    /** cookie */
    val COOKIE = new HttpHeaderKey("Cookie")

    /** date */
    val DATE = new HttpHeaderKey("Date")

    /** dnt */
    val DNT = new HttpHeaderKey("dnt")

    /** etag */
    val ETAG = new HttpHeaderKey("etag")

    /** expect */
    val EXPECT = new HttpHeaderKey("expect")

    /** expires */
    val EXPIRES = new HttpHeaderKey("expires")

    /** from */
    val FROM = new HttpHeaderKey("from")

    /** host */
    val HOST = new HttpHeaderKey("host")

    /** if-match */
    val IF_MATCH = new HttpHeaderKey("if-match")

    /** if-modified-since */
    val IF_MODIFIED_SINCE = new HttpHeaderKey("if-modified-since")

    /** if-none-match */
    val IF_NONE_MATCH = new HttpHeaderKey("if-none-match")

    /** if-range */
    val IF_RANGE = new HttpHeaderKey("if-range")

    /** if-unmodified-since */
    val IF_UNMODIFIED_SINCE = new HttpHeaderKey("if-unmodified-since")

    /** keep-alive */
    val KEEP_ALIVE = new HttpHeaderKey("keep-alive")

    /** last-modified */
    val LAST_MODIFIED = new HttpHeaderKey("last-modified")

    /** location */
    val LOCATION = new HttpHeaderKey("location")

    /** max-forwards */
    val MAX_FORWARDS = new HttpHeaderKey("max-forwards")

    /** origin */
    val ORIGIN = new HttpHeaderKey("origin")

    /** pragma */
    val PRAGMA = new HttpHeaderKey("pragma")

    /** proxy-authenticate */
    val PROXY_AUTHENTICATE = new HttpHeaderKey("proxy-authenticate")

    /** proxy-authorization */
    val PROXY_AUTHORIZATION = new HttpHeaderKey("proxy-authorization")

    /** proxy-connection */
    val PROXY_CONNECTION = new HttpHeaderKey("proxy-connection")

    /** range */
    val RANGE = new HttpHeaderKey("range")

    /** referer */
    val REFERER = new HttpHeaderKey("referer")

    /** retry-after */
    val RETRY_AFTER = new HttpHeaderKey("retry-after")

    /** sec-websocket-protocol */
    val SEC_WEBSOCKET_PROTOCOL = new HttpHeaderKey("sec-websocket-protocol")

    /** sec-websocket-version */
    val SEC_WEBSOCKET_VERSION = new HttpHeaderKey("sec-websocket-version")

    /** sec-websocket-key */
    val SEC_WEBSOCKET_KEY = new HttpHeaderKey("sec-websocket-key")

    /** Sec-WebSocket-Origin */
    val SEC_WEBSOCKET_ORIGIN = new HttpHeaderKey("Sec-WebSocket-Origin")

    /** sec-websocket-accept */
    val SEC_WEBSOCKET_ACCEPT = new HttpHeaderKey("sec-websocket-accept")

    /** sec-websocket-extensions */
    val SEC_WEBSOCKET_EXTENSIONS = new HttpHeaderKey("sec-websocket-extensions")

    /** server */
    val SERVER = new HttpHeaderKey("server")

    /** set-cookie */
    val SET_COOKIE = new HttpHeaderKey("set-cookie")

    /** set-cookie2 */
    val SET_COOKIE2 = new HttpHeaderKey("set-cookie2")

    /** te */
    val TE = new HttpHeaderKey("te")

    /** trailer */
    val TRAILER = new HttpHeaderKey("trailer")

    /** transfer-encoding */
    val TRANSFER_ENCODING = new HttpHeaderKey("transfer-encoding")

    /** upgrade */
    val UPGRADE = new HttpHeaderKey("upgrade")

    /** upgrade-insecure-requests */
    val UPGRADE_INSECURE_REQUESTS = new HttpHeaderKey("upgrade-insecure-requests")

    /** user-agent */
    val USER_AGENT = new HttpHeaderKey("user-agent")

    /** vary */
    val VARY = new HttpHeaderKey("vary")

    /** via */
    val VIA = new HttpHeaderKey("via")

    /** warning */
    val WARNING = new HttpHeaderKey("warning")

    /** www-authenticate */
    val WWW_AUTHENTICATE = new HttpHeaderKey("www-authenticate")

    /** x-frame-options */
    val X_FRAME_OPTIONS = new HttpHeaderKey("x-frame-options")

    /** x-requested-with */
    val X_REQUESTED_WITH = new HttpHeaderKey("x-requested-with")

    /** alt-svc */
    val ALT_SVC = new HttpHeaderKey("alt-svc")

    val values: Array[HttpHeaderKey] = Array(
      ACCEPT,
      ACCEPT_CHARSET,
      ACCEPT_ENCODING,
      ACCEPT_LANGUAGE,
      ACCEPT_RANGES,
      ACCEPT_PATCH,
      ACCESS_CONTROL_ALLOW_CREDENTIALS,
      ACCESS_CONTROL_ALLOW_HEADERS,
      ACCESS_CONTROL_ALLOW_METHODS,
      ACCESS_CONTROL_ALLOW_ORIGIN,
      ACCESS_CONTROL_ALLOW_PRIVATE_NETWORK,
      ACCESS_CONTROL_EXPOSE_HEADERS,
      ACCESS_CONTROL_MAX_AGE,
      ACCESS_CONTROL_REQUEST_HEADERS,
      ACCESS_CONTROL_REQUEST_METHOD,
      ACCESS_CONTROL_REQUEST_PRIVATE_NETWORK,
      AGE,
      ALLOW,
      AUTHORIZATION,
      CACHE_CONTROL,
      CONNECTION,
      CONTENT_BASE,
      CONTENT_ENCODING,
      CONTENT_LANGUAGE,
      CONTENT_LENGTH,
      CONTENT_LOCATION,
      CONTENT_TRANSFER_ENCODING,
      CONTENT_DISPOSITION,
      CONTENT_MD5,
      CONTENT_RANGE,
      CONTENT_SECURITY_POLICY,
      CONTENT_TYPE,
      COOKIE,
      DATE,
      DNT,
      ETAG,
      EXPECT,
      EXPIRES,
      FROM,
      HOST,
      IF_MATCH,
      IF_MODIFIED_SINCE,
      IF_NONE_MATCH,
      IF_RANGE,
      IF_UNMODIFIED_SINCE,
      KEEP_ALIVE,
      LAST_MODIFIED,
      LOCATION,
      MAX_FORWARDS,
      ORIGIN,
      PRAGMA,
      PROXY_AUTHENTICATE,
      PROXY_AUTHORIZATION,
      PROXY_CONNECTION,
      RANGE,
      REFERER,
      RETRY_AFTER,
      SEC_WEBSOCKET_PROTOCOL,
      SEC_WEBSOCKET_VERSION,
      SEC_WEBSOCKET_KEY,
      SEC_WEBSOCKET_ORIGIN,
      SEC_WEBSOCKET_ACCEPT,
      SEC_WEBSOCKET_EXTENSIONS,
      SERVER,
      SET_COOKIE,
      SET_COOKIE2,
      TE,
      TRAILER,
      TRANSFER_ENCODING,
      UPGRADE,
      UPGRADE_INSECURE_REQUESTS,
      USER_AGENT,
      VARY,
      VIA,
      WARNING,
      WWW_AUTHENTICATE,
      X_FRAME_OPTIONS,
      X_REQUESTED_WITH,
      ALT_SVC
    )

}
