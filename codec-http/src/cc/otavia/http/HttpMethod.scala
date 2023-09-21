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

/** The request method of HTTP or its derived protocols, such as <a
 *  href="https://en.wikipedia.org/wiki/Real_Time_Streaming_Protocol">RTSP</a> and <a
 *  href="https://en.wikipedia.org/wiki/Internet_Content_Adaptation_Protocol">ICAP</a>.
 */
enum HttpMethod(val bytes: Array[Byte]) {

    /** The GET method means retrieve whatever information (in the form of an entity) is identified by the Request-URI.
     *  If the Request-URI refers to a data-producing process, it is the produced data which shall be returned as the
     *  entity in the response and not the source text of the process, unless that text happens to be the output of the
     *  process.
     */
    case GET extends HttpMethod("GET".getBytes(StandardCharsets.US_ASCII))

    /** The POST method is used to request that the origin server accept the entity enclosed in the request as a new
     *  subordinate of the resource identified by the Request-URI in the Request-Line.
     */
    case POST extends HttpMethod("POST".getBytes(StandardCharsets.US_ASCII))

    /** The OPTIONS method represents a request for information about the communication options available on the
     *  request/response chain identified by the Request-URI. This method allows the client to determine the options
     *  and/or requirements associated with a resource, or the capabilities of a server, without implying a resource
     *  action or initiating a resource retrieval.
     */
    case OPTIONS extends HttpMethod("OPTIONS".getBytes(StandardCharsets.US_ASCII))

    /** The HEAD method is identical to GET except that the server MUST NOT return a message-body in the response. */
    case HEAD extends HttpMethod("HEAD".getBytes(StandardCharsets.US_ASCII))

    /** The PUT method requests that the enclosed entity be stored under the supplied Request-URI. */
    case PUT extends HttpMethod("PUT".getBytes(StandardCharsets.US_ASCII))

    /** The PATCH method requests that a set of changes described in the request entity be applied to the resource
     *  identified by the Request-URI.
     */
    case PATCH extends HttpMethod("PATCH".getBytes(StandardCharsets.US_ASCII))

    /** The DELETE method requests that the origin server delete the resource identified by the Request-URI. */
    case DELETE extends HttpMethod("DELETE".getBytes(StandardCharsets.US_ASCII))

    /** The TRACE method is used to invoke a remote, application-layer loop- back of the request message. */
    case TRACE extends HttpMethod("TRACE".getBytes(StandardCharsets.US_ASCII))

    /** This specification reserves the method name CONNECT for use with a proxy that can dynamically switch to being a
     *  tunnel
     */
    case CONNECT extends HttpMethod("CONNECT".getBytes(StandardCharsets.US_ASCII))

}
