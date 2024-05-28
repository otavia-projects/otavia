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
enum HttpHeaderKey(val string: String) {

    private val bytes: Array[Byte] = string.getBytes(StandardCharsets.US_ASCII).nn

    def getBytes: Array[Byte] = bytes

    def length: Int = bytes.length

    override def toString: String = string

    /** accept */
    case ACCEPT extends HttpHeaderKey("accept")

    /** accept-charset */
    case ACCEPT_CHARSET extends HttpHeaderKey("accept-charset")

    /** accept-encoding */
    case ACCEPT_ENCODING extends HttpHeaderKey("accept-encoding")

    /** accept-language */
    case ACCEPT_LANGUAGE extends HttpHeaderKey("accept-language")

    /** accept-ranges */
    case ACCEPT_RANGES extends HttpHeaderKey("accept-ranges")

    /** accept-patch */
    case ACCEPT_PATCH extends HttpHeaderKey("accept-patch")

    /** access-control-allow-credentials */
    case ACCESS_CONTROL_ALLOW_CREDENTIALS extends HttpHeaderKey("access-control-allow-credentials")

    /** access-control-allow-headers */
    case ACCESS_CONTROL_ALLOW_HEADERS extends HttpHeaderKey("access-control-allow-headers")

    /** access-control-allow-methods */
    case ACCESS_CONTROL_ALLOW_METHODS extends HttpHeaderKey("access-control-allow-methods")

    /** access-control-allow-origin */
    case ACCESS_CONTROL_ALLOW_ORIGIN extends HttpHeaderKey("access-control-allow-origin")

    /** access-control-allow-private-network */
    case ACCESS_CONTROL_ALLOW_PRIVATE_NETWORK extends HttpHeaderKey("access-control-allow-private-network")

    /** access-control-expose-headers */
    case ACCESS_CONTROL_EXPOSE_HEADERS extends HttpHeaderKey("access-control-expose-headers")

    /** access-control-max-age */
    case ACCESS_CONTROL_MAX_AGE extends HttpHeaderKey("access-control-max-age")

    /** access-control-request-headers */
    case ACCESS_CONTROL_REQUEST_HEADERS extends HttpHeaderKey("access-control-request-headers")

    /** access-control-request-method */
    case ACCESS_CONTROL_REQUEST_METHOD extends HttpHeaderKey("access-control-request-method")

    /** access-control-request-private-network */
    case ACCESS_CONTROL_REQUEST_PRIVATE_NETWORK extends HttpHeaderKey("access-control-request-private-network")

    /** age */
    case AGE extends HttpHeaderKey("age")

    /** allow */
    case ALLOW extends HttpHeaderKey("allow")

    /** authorization */
    case AUTHORIZATION extends HttpHeaderKey("authorization")

    /** cache-control */
    case CACHE_CONTROL extends HttpHeaderKey("cache-control")

    /** connection */
    case CONNECTION extends HttpHeaderKey("connection")

    /** content-base */
    case CONTENT_BASE extends HttpHeaderKey("content-base")

    /** content-encoding */
    case CONTENT_ENCODING extends HttpHeaderKey("content-encoding")

    /** content-language */
    case CONTENT_LANGUAGE extends HttpHeaderKey("content-language")

    /** content-length */
    case CONTENT_LENGTH extends HttpHeaderKey("Content-Length")

    /** content-location */
    case CONTENT_LOCATION extends HttpHeaderKey("content-location")

    /** content-transfer-encoding */
    case CONTENT_TRANSFER_ENCODING extends HttpHeaderKey("content-transfer-encoding")

    /** content-disposition */
    case CONTENT_DISPOSITION extends HttpHeaderKey("content-disposition")

    /** content-md5 */
    case CONTENT_MD5 extends HttpHeaderKey("content-md5")

    /** content-range */
    case CONTENT_RANGE extends HttpHeaderKey("content-range")

    /** content-security-policy */
    case CONTENT_SECURITY_POLICY extends HttpHeaderKey("content-security-policy")

    /** content-type */
    case CONTENT_TYPE extends HttpHeaderKey("Content-Type")

    /** cookie */
    case COOKIE extends HttpHeaderKey("Cookie")

    /** date */
    case DATE extends HttpHeaderKey("Date")

    /** dnt */
    case DNT extends HttpHeaderKey("dnt")

    /** etag */
    case ETAG extends HttpHeaderKey("etag")

    /** expect */
    case EXPECT extends HttpHeaderKey("expect")

    /** expires */
    case EXPIRES extends HttpHeaderKey("expires")

    /** from */
    case FROM extends HttpHeaderKey("from")

    /** host */
    case HOST extends HttpHeaderKey("host")

    /** if-match */
    case IF_MATCH extends HttpHeaderKey("if-match")

    /** if-modified-since */
    case IF_MODIFIED_SINCE extends HttpHeaderKey("if-modified-since")

    /** if-none-match */
    case IF_NONE_MATCH extends HttpHeaderKey("if-none-match")

    /** if-range */
    case IF_RANGE extends HttpHeaderKey("if-range")

    /** if-unmodified-since */
    case IF_UNMODIFIED_SINCE extends HttpHeaderKey("if-unmodified-since")

    /** keep-alive */
    case KEEP_ALIVE extends HttpHeaderKey("keep-alive")

    /** last-modified */
    case LAST_MODIFIED extends HttpHeaderKey("last-modified")

    /** location */
    case LOCATION extends HttpHeaderKey("location")

    /** max-forwards */
    case MAX_FORWARDS extends HttpHeaderKey("max-forwards")

    /** origin */
    case ORIGIN extends HttpHeaderKey("origin")

    /** pragma */
    case PRAGMA extends HttpHeaderKey("pragma")

    /** proxy-authenticate */
    case PROXY_AUTHENTICATE extends HttpHeaderKey("proxy-authenticate")

    /** proxy-authorization */
    case PROXY_AUTHORIZATION extends HttpHeaderKey("proxy-authorization")

    /** proxy-connection */
    case PROXY_CONNECTION extends HttpHeaderKey("proxy-connection")

    /** range */
    case RANGE extends HttpHeaderKey("range")

    /** referer */
    case REFERER extends HttpHeaderKey("referer")

    /** retry-after */
    case RETRY_AFTER extends HttpHeaderKey("retry-after")

    /** sec-websocket-protocol */
    case SEC_WEBSOCKET_PROTOCOL extends HttpHeaderKey("sec-websocket-protocol")

    /** sec-websocket-version */
    case SEC_WEBSOCKET_VERSION extends HttpHeaderKey("sec-websocket-version")

    /** sec-websocket-key */
    case SEC_WEBSOCKET_KEY extends HttpHeaderKey("sec-websocket-key")

    /** Sec-WebSocket-Origin */
    case SEC_WEBSOCKET_ORIGIN extends HttpHeaderKey("Sec-WebSocket-Origin")

    /** sec-websocket-accept */
    case SEC_WEBSOCKET_ACCEPT extends HttpHeaderKey("sec-websocket-accept")

    /** sec-websocket-extensions */
    case SEC_WEBSOCKET_EXTENSIONS extends HttpHeaderKey("sec-websocket-extensions")

    /** server */
    case SERVER extends HttpHeaderKey("server")

    /** set-cookie */
    case SET_COOKIE extends HttpHeaderKey("set-cookie")

    /** set-cookie2 */
    case SET_COOKIE2 extends HttpHeaderKey("set-cookie2")

    /** te */
    case TE extends HttpHeaderKey("te")

    /** trailer */
    case TRAILER extends HttpHeaderKey("trailer")

    /** transfer-encoding */
    case TRANSFER_ENCODING extends HttpHeaderKey("transfer-encoding")

    /** upgrade */
    case UPGRADE extends HttpHeaderKey("upgrade")

    /** upgrade-insecure-requests */
    case UPGRADE_INSECURE_REQUESTS extends HttpHeaderKey("upgrade-insecure-requests")

    /** user-agent */
    case USER_AGENT extends HttpHeaderKey("user-agent")

    /** vary */
    case VARY extends HttpHeaderKey("vary")

    /** via */
    case VIA extends HttpHeaderKey("via")

    /** warning */
    case WARNING extends HttpHeaderKey("warning")

    /** www-authenticate */
    case WWW_AUTHENTICATE extends HttpHeaderKey("www-authenticate")

    /** x-frame-options */
    case X_FRAME_OPTIONS extends HttpHeaderKey("x-frame-options")

    /** x-requested-with */
    case X_REQUESTED_WITH extends HttpHeaderKey("x-requested-with")

    /** alt-svc */
    case ALT_SVC extends HttpHeaderKey("alt-svc")

}
