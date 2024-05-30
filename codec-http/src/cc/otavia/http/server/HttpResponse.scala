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

import cc.otavia.core.cache.ActorThreadLocal
import cc.otavia.core.message.Reply
import cc.otavia.http.{HttpHeaderKey, HttpHeaderValue}

import scala.collection.mutable
import scala.language.unsafeNulls

class HttpResponse[C <: AnyRef] extends Reply {

    private[http] var headers: Map[HttpHeaderKey, HttpHeaderValue] = _
    private[http] var content: C                                   = _

}

object HttpResponse {

    private val builderLocal = new ActorThreadLocal[Builder[?]] {
        override protected def initialValue(): Builder[?] = new Builder[AnyRef]()
    }

    def builder[C <: AnyRef]: Builder[C] = builderLocal.get().asInstanceOf[Builder[C]]

    class Builder[C <: AnyRef] {

        private var content: AnyRef                                      = _
        private val headers: mutable.Map[HttpHeaderKey, HttpHeaderValue] = mutable.Map.empty

        def setContent(content: C): this.type = {
            this.content = content
            this
        }

        def setHeaders(headers: mutable.Map[HttpHeaderKey, HttpHeaderValue]): this.type = {
            this.headers.addAll(headers)
            this
        }

        def setHeaders(headers: Map[HttpHeaderKey, HttpHeaderValue]): this.type = {
            this.headers.addAll(headers)
            this
        }

        def addHeader(key: HttpHeaderKey, value: HttpHeaderValue): this.type = {
            this.headers.put(key, value)
            this
        }

        def addHeaders(headers: (HttpHeaderKey, HttpHeaderValue)*): this.type = {
            this.headers.addAll(headers)
            this
        }

        def build(): HttpResponse[C] = {
            val response = new HttpResponse[C]()

            response.content = content.asInstanceOf[C]
            if (this.headers.nonEmpty) response.headers = this.headers.toMap

            content = null
            this.headers.clear()

            response
        }

    }

}
