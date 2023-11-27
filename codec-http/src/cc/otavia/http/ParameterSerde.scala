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
import cc.otavia.core.system.ActorThread
import cc.otavia.http.server.RouterMatcher
import cc.otavia.http.server.RouterMatcher.URL_CHARSET
import cc.otavia.serde.Serde

import java.net.{URLDecoder, URLEncoder}
import java.nio.charset.StandardCharsets
import scala.collection.mutable
import scala.language.unsafeNulls

/** [[Serde]] for [[MediaType.APP_X_WWW_FORM_URLENCODED]] or parameters in url */
trait ParameterSerde[P] extends Serde[P] {

    final override def deserialize(in: Buffer): P = {
        val params = ActorThread.threadMap[String, String]
        ParameterSerde.parse(in, params)
        val obj = construct(params)
        params.clear()
        obj
    }

    final protected def serializeParamsStart(out: Buffer): this.type = {
        out.writeByte(HttpConstants.PARAM_START)
        this
    }

    final protected def serializeParam(name: String, value: String, out: Buffer): this.type = {
        out.writeCharSequence(URLEncoder.encode(name, URL_CHARSET), StandardCharsets.US_ASCII)
        out.writeByte(HttpConstants.EQ)
        out.writeCharSequence(URLEncoder.encode(value, URL_CHARSET), StandardCharsets.US_ASCII)
        this
    }

    final protected def serializeParamSplitter(out: Buffer): this.type = {
        out.writeByte(HttpConstants.PARAM_SPLITTER)
        this
    }

    final protected def serializeParamsEnd(out: Buffer): this.type = {
        out.writeByte(HttpConstants.SP)
        this
    }

    protected def construct(params: mutable.HashMap[String, String]): P

}

object ParameterSerde {

    import StandardCharsets.US_ASCII

    private val PARAM_SPLITS: Array[Byte] = "& ".getBytes(US_ASCII)

    def parse(in: Buffer, params: mutable.HashMap[String, String]): Unit = {
        assert(in.skipIfNextIs(HttpConstants.PARAM_START), "http params not start with '?'")
        while (in.readableBytes > 0 && !in.skipIfNextIs(HttpConstants.SP)) {
            val key =
                URLDecoder.decode(in.readCharSequence(in.bytesBefore(HttpConstants.EQ), US_ASCII).toString, URL_CHARSET)
            in.skipReadableBytes(1) // skip '='
            val value = URLDecoder.decode(
              in.readCharSequence(in.bytesBeforeIn(HttpConstants.PARAMS_END), US_ASCII).toString,
              URL_CHARSET
            )
            in.skipIfNextIs(HttpConstants.PARAM_SPLITTER)
            params.put(key, value)
        }
    }

}
