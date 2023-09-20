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

import scala.collection.mutable
import scala.language.unsafeNulls

class HttpHeaders extends mutable.Map[String, String] {

    // high frequency headers
    // Host
    private var host: String = _
    // User-Agent
    private var userAgent: String = _
    // Accept
    private var accept: String = _

    // low frequency headers
    private var others: mutable.Map[String, String] = _

    override def subtractOne(elem: String): HttpHeaders.this.type = ???

    override def addOne(elem: (String, String)): HttpHeaders.this.type = ???

    override def get(key: String): Option[String] = ???

    def getUnsafe(key: String): String | Null = ???

    override def iterator: Iterator[(String, String)] = ???

}

object HttpHeaders {

    val REQUEST_DEFAULT = new HttpHeaders()

    val RESPONSE_DEFAULT = new HttpHeaders()

}
