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

import java.nio.charset.StandardCharsets
import scala.language.unsafeNulls

object HttpHeaderUtil {

    private val emptyKey   = Array.empty[HttpHeaderKey]
    private val emptyValue = Array.empty[HttpHeaderValue]

    private val keyGroup = {
        val grps = HttpHeaderKey.values.groupBy(_.getBytes.length)
        val max  = grps.keys.max
        val arr  = new Array[Array[HttpHeaderKey]](max)
        var i    = 0
        while (i < arr.length) {
            arr(i) = emptyKey
            i += 1
        }
        for ((idx, keys) <- grps) arr(idx - 1) = keys
        arr
    }

    private val valueGroup = {
        val grps = HttpHeaderValue.values.groupBy(_.getBytes.length)
        val max  = grps.keys.max
        val arr  = new Array[Array[HttpHeaderValue]](max)
        var i    = 0
        while (i < arr.length) {
            arr(i) = emptyValue
            i += 1
        }
        for ((idx, values) <- grps) arr(idx - 1) = values
        arr
    }

    def readKey(buffer: Buffer, keyLen: Int): HttpHeaderKey = {
        if (keyLen < keyGroup.length) {
            val keys               = keyGroup(keyLen - 1)
            var key: HttpHeaderKey = null
            var continue           = true
            var i                  = 0
            while (continue && i < keys.length) {
                val k = keys(i)
                if (buffer.skipIfNextIgnoreCaseAre(k.getBytes)) {
                    key = k
                    continue = false
                } else i += 1
            }

            if (key == null) {
                new HttpHeaderKey(buffer.readCharSequence(keyLen, StandardCharsets.US_ASCII).toString)
            } else key
        } else new HttpHeaderKey(buffer.readCharSequence(keyLen, StandardCharsets.US_ASCII).toString)
    }

    def readValue(buffer: Buffer, len: Int): HttpHeaderValue = {
        if (len < valueGroup.length) {
            val vs                     = valueGroup(len - 1)
            var value: HttpHeaderValue = null

            var continue = true
            var i        = 0
            while (continue && i < vs.length) {
                val v = vs(i)
                if (buffer.skipIfNextIgnoreCaseAre(v.getBytes)) {
                    value = v
                    continue = false
                } else i += 1
            }

            if (value == null) {
                new HttpHeaderValue(buffer.readCharSequence(len, StandardCharsets.US_ASCII).toString)
            } else value
        } else new HttpHeaderValue(buffer.readCharSequence(len, StandardCharsets.US_ASCII).toString)
    }

}
