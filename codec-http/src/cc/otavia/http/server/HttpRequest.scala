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

package cc.otavia.http.server

import cc.otavia.core.address.Address
import cc.otavia.core.message.{Ask, Reply}
import cc.otavia.http.*
import cc.otavia.http.server.HttpRequest.{empty, emptyHeaders}
import cc.otavia.http.server.Router

import scala.collection.mutable

abstract class HttpRequest[C, R <: Reply] extends Ask[R] {

    private var rt: Router = _

    private var mth: HttpMethod                                      = _
    private var requestPath: String                                  = _
    private var vs: HttpVersion                                      = HttpVersion.HTTP_1_1
    private var headers: mutable.Map[HttpHeaderKey, HttpHeaderValue] = emptyHeaders
    private var media: MediaType                                     = _

    private var c: Option[C] = None

    private var pas: Map[String, String]   = HttpRequest.empty
    private var pvars: Map[String, String] = HttpRequest.empty

    def setMethod(method: HttpMethod): Unit = mth = method

    def setPath(path: String): Unit = requestPath = path

    def setHttpVersion(version: HttpVersion): Unit = vs = version

    def setHttpHeaders(headers: mutable.Map[HttpHeaderKey, HttpHeaderValue]): Unit = {
        if (this.headers == emptyHeaders) this.headers = headers
        else this.headers.addAll(headers)
    }

    def addHeader(key: HttpHeaderKey, value: HttpHeaderValue): this.type = {
        if (this.headers == emptyHeaders) this.headers = mutable.Map.empty
        this.headers.put(key, value)
        this
    }

    def setMediaType(mediaType: MediaType): Unit = media = mediaType

    def setPathVariables(variables: Map[String, String]): Unit = pvars = variables
    def setParam(params: Map[String, String]): Unit            = pas = params
    def setContent(content: Any): Unit                         = c = Some(content.asInstanceOf[C])

    private[otavia] def setRouter(r: Router): Unit = rt = r
    private[otavia] def router: Router             = rt

    def method: HttpMethod                                       = mth
    def path: String                                             = requestPath
    def httpVersion: HttpVersion                                 = vs
    def httpHeaders: mutable.Map[HttpHeaderKey, HttpHeaderValue] = headers
    def mediaType: MediaType                                     = media

    def pathVariables: Map[String, String] = pvars
    def params: Map[String, String]        = pas
    def content: Option[C]                 = c

    override def toString: String = s"${mth} ${requestPath}"

}

object HttpRequest {

    private val empty: Map[String, String] = Map.empty

    private val emptyHeaders: mutable.Map[HttpHeaderKey, HttpHeaderValue] = mutable.Map.empty

}
