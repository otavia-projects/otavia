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

package cc.otavia.postgres.utils

import java.nio.charset.StandardCharsets
import java.security.{MessageDigest, NoSuchAlgorithmException}
import scala.language.unsafeNulls

object MD5Authentication {

    private val HEX_ALPHABET = "0123456789abcdef".toCharArray

    private def toHex(bytes: Array[Byte]): String = {
        val hexChars = new Array[Char](bytes.length * 2)
        for (j <- bytes.indices) {
            val v = bytes(j) & 0xff
            hexChars(j * 2) = HEX_ALPHABET(v >>> 4)
            hexChars(j * 2 + 1) = HEX_ALPHABET(v & 0x0f)
        }
        new String(hexChars)
    }

    def encode(username: String, password: String, salt: Array[Byte]): String = {
        val msgDigest: MessageDigest =
            try {
                MessageDigest.getInstance("MD5")
            } catch {
                case e: NoSuchAlgorithmException =>
                    throw e
            }
        msgDigest.update(password.getBytes(StandardCharsets.UTF_8))
        msgDigest.update(username.getBytes(StandardCharsets.UTF_8))
        val digest = msgDigest.digest()

        val hexDigest: Array[Byte] = toHex(digest).getBytes(StandardCharsets.US_ASCII)
        msgDigest.update(hexDigest)
        msgDigest.update(salt)
        val passDigest = msgDigest.digest()

        "md5" + toHex(passDigest)
    }

}
