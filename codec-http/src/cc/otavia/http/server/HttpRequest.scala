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
import cc.otavia.http.server.Router
import cc.otavia.http.{HttpHeaders, HttpMethod, HttpVersion, MediaType}

import scala.collection.mutable

abstract class HttpRequest[P, C, R <: Reply] extends Ask[R] {

    private var mth: HttpMethod                        = _
    private var requestPath: String                    = _
    private var vs: HttpVersion                        = HttpVersion.HTTP_1_1
    private var hs: Option[HttpHeaders]                = None
    private var media: MediaType                       = _
    private var p: Option[P]                           = None
    private var c: Option[C]                           = None
    private var pvars: mutable.HashMap[String, String] = _

    private var rt: Router = _

    def setMethod(method: HttpMethod): Unit        = mth = method
    def setPath(path: String): Unit                = requestPath = path
    def setHttpVersion(version: HttpVersion): Unit = vs = version
    def setHttpHeaders(headers: HttpHeaders): Unit = hs = Option(headers)
    def setMediaType(mediaType: MediaType): Unit   = media = mediaType

    def setPathVariables(variables: mutable.HashMap[String, String]): Unit = pvars = variables
    def setParam(param: P): Unit                                           = p = Option(param)
    def setContent(content: Any): Unit                                     = c = Option(content.asInstanceOf[C])

    private[otavia] def setRouter(r: Router): Unit = rt = r
    private[otavia] def router: Router             = rt

    def method: HttpMethod               = mth
    def path: String                     = requestPath
    def httpVersion: HttpVersion         = vs
    def httpHeaders: Option[HttpHeaders] = hs
    def mediaType: MediaType             = media

    def params: Option[P]  = p
    def content: Option[C] = c

    override def toString: String = s"${mth} ${requestPath}"

}
