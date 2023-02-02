/*
 * Copyright 2022 Yan Kun <yan_kun_1992@foxmail.com>
 *
 * This file fork from netty.
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

package io.otavia.handler.codec

import io.otavia.handler.codec.UnsupportedMessageTypeException.message

/** Thrown if an unsupported message is received by a codec. */
class UnsupportedMessageTypeException(msg: String | Null, cause: Throwable | Null) extends CodecException(msg, cause) {

    def this() = this(null, null)

    def this(s: String) = this(s, null)

    def this(s: AnyRef | Null, expectedTypes: Seq[Class[?]] = Seq.empty) = {
        this(
          message(
            s match
                case null: Null => "null"
                case m: AnyRef  => m.getClass.getName
            ,
            expectedTypes
          )
        )
    }

    def this(cause: Throwable) = this(null, cause)

}

object UnsupportedMessageTypeException {
    private def message(actualType: String, expectedTypes: Seq[Class[?]] = Seq.empty): String = {
        val buf = new StringBuilder(actualType)
        if (expectedTypes.nonEmpty) {
            buf.append(expectedTypes.map(_.getName).mkString(" (expected: ", ", ", ")"))
        }
        buf.toString()
    }
}
