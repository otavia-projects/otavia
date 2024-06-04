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

package cc.otavia.http.client

import cc.otavia.core.cache.ActorThreadLocal
import cc.otavia.core.message.{Ask, Reply}
import cc.otavia.http.client.HttpClientRequest.defaultHeaders
import cc.otavia.http.{HttpHeaderKey, HttpHeaderValue, HttpMethod, MediaType}
import cc.otavia.json.JsonSerde
import cc.otavia.serde.Serde

import scala.collection.mutable
import scala.language.unsafeNulls

class HttpClientRequest private () extends Ask[HttpClientResponse] {

    private[http] var method: HttpMethod                           = _
    private[http] var path: String                                 = _
    private[http] var params: Map[String, String]                  = _
    private[http] var headers: Map[HttpHeaderKey, HttpHeaderValue] = defaultHeaders
    private[http] var mediaType: MediaType                         = _
    private[http] var body: Any                                    = _
    private[http] var bodySerde: Serde[?]                          = _

    private[http] var responseSerde: Serde[?] = _

}

object HttpClientRequest {

    def builder: RequestBuilder = threadLocal.get()

    class RequestBuilder {

        private var method: HttpMethod          = HttpMethod.GET
        private var path: String                = "/"
        private var params: Map[String, String] = _
        private val headers                     = mutable.Map.empty[HttpHeaderKey, HttpHeaderValue]
        private var mediaType: MediaType        = MediaType.TEXT_PLAIN
        private var body: Any                   = _
        private var bodySerde: Serde[?]         = _

        def setMethod(method: HttpMethod): this.type = {
            this.method = method
            this
        }

        /** Shorthand for [[setMethod]](HttpMethod.GET) */
        def get(): this.type = setMethod(HttpMethod.GET)

        /** Shorthand for [[setMethod]](HttpMethod.POST) */
        def post(): this.type = setMethod(HttpMethod.POST)

        def addHeader(key: HttpHeaderKey, value: HttpHeaderValue): this.type = {
            headers.put(key, value)
            this
        }

        def addHeaders(headers: Map[HttpHeaderKey, HttpHeaderValue]): this.type = {
            this.headers.addAll(headers)
            this
        }

        def addHeaders(headers: (HttpHeaderKey, HttpHeaderValue)*): this.type = {
            this.headers.addAll(headers)
            this
        }

        def setPath(path: String): this.type = {
            assert(path.startsWith("/"), "http path must start with '/'")
            this.path = path
            this
        }

        def setParams(params: Map[String, String]): this.type = {
            this.params = params
            this
        }

        def setMediaType(mediaType: MediaType): this.type = {
            this.mediaType = mediaType
            this
        }

        /** Setting the request body and [[Serde]] for body.
         *
         *  @param body
         *    body
         *  @param bodySerde
         *    body [[Serde]]
         *  @tparam C
         *    Type of request body.
         *  @return
         *    this object.
         */
        def setBody[C](body: C, bodySerde: Serde[C]): this.type = {
            this.body = body
            this.bodySerde = bodySerde
            if (bodySerde.isInstanceOf[JsonSerde[?]]) this.mediaType = MediaType.APP_JSON
            this
        }

        /** Setting the request body and [[Serde]] for body.
         *
         *  @param body
         *    body
         *  @param bodySerde
         *    body [[JsonSerde]]
         *  @tparam C
         *    Type of request body.
         *  @return
         *    this object.
         */
        def setJsonBody[C](body: C)(using bodySerde: JsonSerde[C]): this.type = {
            this.body = body
            this.bodySerde = bodySerde
            this.mediaType = MediaType.APP_JSON
            this
        }

        /** Setting the map as request body and serde to json.
         *  @param body
         *    body
         *  @return
         *    this object.
         */
        def setMapJsonBody(body: Map[String, String]): this.type = {
            this.body = body
            this.bodySerde = jsonMapSerde
            this.mediaType = MediaType.APP_JSON
            this
        }

        private def check(): Unit = {
            assert(this.method != null, "http method can not be null, use setMethod to set it.")
            assert(this.path != null, "http url path can not be null, use setPath to set it.")

            if (body != null) {
                assert(this.mediaType != null, "http media type can not be null, use setMediaType to set it.")
            }
        }

        def build[R](responseSerde: Serde[R]): HttpClientRequest = {
            val request = build()
            request.responseSerde = responseSerde
            request
        }

        def build(): HttpClientRequest = {
            check()
            val request = new HttpClientRequest()
            request.method = this.method
            request.path = this.path
            request.params = this.params
            if (this.headers.isEmpty) request.headers = defaultHeaders else request.headers = this.headers.toMap
            request.mediaType = this.mediaType
            request.body = this.body
            request.bodySerde = this.bodySerde

            clean()

            request
        }

        private def clean(): Unit = {
            method = HttpMethod.GET
            path = "/"
            params = null
            headers.clear()
            mediaType = MediaType.TEXT_PLAIN
            body = null
            bodySerde = null
        }

    }

    private val threadLocal = new ActorThreadLocal[RequestBuilder] {
        override protected def initialValue(): RequestBuilder = new RequestBuilder()
    }

    private val defaultHeaders = Map(
      HttpHeaderKey.CONNECTION -> HttpHeaderValue.KEEP_ALIVE,
      HttpHeaderKey.USER_AGENT -> HttpHeaderValue.UA_FIREFOX
    )

    private val jsonMapSerde: JsonSerde[Map[String, String]] = JsonSerde.derived

}
