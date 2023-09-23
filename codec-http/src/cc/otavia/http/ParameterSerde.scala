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
import cc.otavia.http.server.RouterMatcher
import cc.otavia.serde.Serde

import java.net.{URLDecoder, URLEncoder}
import java.nio.charset.StandardCharsets
import scala.collection.mutable
import scala.language.unsafeNulls

/** [[Serde]] for [[MediaType.APP_X_WWW_FORM_URLENCODED]] or variable in url */
trait ParameterSerde[P] extends Serde[P] {

    /** Only use in [[deserialize]] */
    def setPathVars(vars: mutable.Map[String, String]): Unit

    /** Only use in [[deserialize]] */
    def setParams(p: mutable.Map[String, String]): Unit

    /** Only use in [[deserialize]] */
    def pathVars: mutable.Map[String, String]

    /** Only use in [[deserialize]] */
    def params: mutable.Map[String, String]

}

object ParameterSerde {

    private val PARAM_SPLITS: Array[Byte] = "& ".getBytes(StandardCharsets.US_ASCII)

    given shortSerde: ParameterSerde[Short] = new AbstractParameterSerde[Short] {

        override def deserialize(in: Buffer): Short = {
            val len    = in.bytesBeforeIn(PARAM_SPLITS)
            val encode = in.readCharSequence(len, StandardCharsets.US_ASCII).toString
            val short  = URLDecoder.decode(encode, RouterMatcher.URL_CHARSET).toShort
            in.skipIfNext('&')
            in.skipIfNext(' ')
            short
        }

        override def serialize(value: Short, out: Buffer): Unit =
            out.writeBytes(value.toString.getBytes(StandardCharsets.US_ASCII))

    }

    given intSerde: ParameterSerde[Int] = new AbstractParameterSerde[Int] {

        override def deserialize(in: Buffer): Int = {
            val len    = in.bytesBeforeIn(PARAM_SPLITS)
            val encode = in.readCharSequence(len, StandardCharsets.US_ASCII).toString
            val int    = URLDecoder.decode(encode, RouterMatcher.URL_CHARSET).toInt
            in.skipIfNext('&')
            in.skipIfNext(' ')
            int
        }

        override def serialize(value: Int, out: Buffer): Unit =
            out.writeBytes(value.toString.getBytes(StandardCharsets.US_ASCII))

    }

    given longSerde: ParameterSerde[Long] = new AbstractParameterSerde[Long] {

        override def deserialize(in: Buffer): Long = throw new UnsupportedOperationException()

        override def serialize(value: Long, out: Buffer): Unit =
            out.writeBytes(value.toString.getBytes(StandardCharsets.US_ASCII))

    }

    given stringSerde: ParameterSerde[String] = new AbstractParameterSerde[String] {

        override def deserialize(in: Buffer): String = throw new UnsupportedOperationException()

        override def serialize(value: String, out: Buffer): Unit =
            out.writeBytes(URLEncoder.encode(value, RouterMatcher.URL_CHARSET).getBytes(StandardCharsets.US_ASCII))

    }

}
