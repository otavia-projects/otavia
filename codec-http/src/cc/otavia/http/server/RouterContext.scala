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
import cc.otavia.http.HttpMethod

import scala.collection.mutable
import scala.language.unsafeNulls

class RouterContext {

    var method: HttpMethod                        = _
    var remaining: String                         = _
    val requestPath: StringBuilder                = new StringBuilder()
    val pathVars: mutable.HashMap[String, String] = mutable.HashMap.empty

    def setMethod(httpMethod: HttpMethod): Unit           = method = httpMethod
    def setRemaining(file: String): Unit                  = remaining = file
    def appendPath(part: String): Unit                    = requestPath.append(part)
    def putPathVariable(key: String, value: String): Unit = pathVars.put(key, value)

    def path: String = requestPath.toString()

    def clear(): Unit = {
        remaining = null
        method = null
        requestPath.clear()
        pathVars.clear()
    }

}
