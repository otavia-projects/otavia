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

import cc.otavia.buffer.Buffer
import cc.otavia.core.message.Reply
import cc.otavia.serde.Serde

import java.nio.charset.StandardCharsets
import scala.collection.mutable
import scala.language.unsafeNulls

abstract class HttpRequestSerde[P, C, R <: Reply](
    private val contentSerde: Option[Serde[C]] = None,
    private val parameterSerde: Option[ParameterSerde[P]] = None,
    val requireHeaders: Seq[String] = HttpRequestSerde.EMPTY_STRING
) extends Serde[HttpRequest[P, C, R]] {

    private[http] def setPathVars(vars: mutable.Map[String, String]): Unit = parameterSerde match
        case Some(serde) => serde.setPathVars(vars)
        case None        =>

    private[http] def setParams(vars: mutable.Map[String, String]): Unit = parameterSerde match
        case Some(serde) => serde.setParams(vars)
        case None        =>

    final override def deserialize(in: Buffer): HttpRequest[P, C, R] = {
        val httpRequest = createHttpRequest()
        // construct params object
        parameterSerde match
            case Some(serde) =>
                httpRequest.setParam(serde.deserialize(in))
            case None =>
        contentSerde match
            case Some(serde) =>
                httpRequest.setContent(serde.deserialize(in))
            case None =>

        httpRequest
    }

    override def serialize(request: HttpRequest[P, C, R], out: Buffer): Unit = ???

    final def hasParams: Boolean = parameterSerde.nonEmpty

    protected def createHttpRequest(): HttpRequest[P, C, R]

}

object HttpRequestSerde {
    private val EMPTY_STRING: Seq[String] = Seq.empty
}
