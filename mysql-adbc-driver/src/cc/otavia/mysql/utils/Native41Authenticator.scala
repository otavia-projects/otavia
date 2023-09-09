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

package cc.otavia.mysql.utils

import java.security.{MessageDigest, NoSuchAlgorithmException}
import scala.language.unsafeNulls

object Native41Authenticator {

    def encode(password: Array[Byte], salt: Array[Byte]): Array[Byte] = {
        val messageDigest =
            try {
                MessageDigest.getInstance("SHA-1")
            } catch {
                case e: NoSuchAlgorithmException => throw new RuntimeException(e)
            }

        // SHA1(password)
        val passwordHash1 = messageDigest.digest(password)
        messageDigest.reset()

        // SHA1(SHA1(password))
        val passwordHash2 = messageDigest.digest(passwordHash1)
        messageDigest.reset()

        // SHA1("20-bytes random data from server" <concat> SHA1(SHA1(password))
        messageDigest.update(salt)
        messageDigest.update(passwordHash2)
        val passwordHash3 = messageDigest.digest
        // result = passwordHash1 XOR passwordHash3
        for (i <- 0 until passwordHash1.length) {
            passwordHash1(i) = (passwordHash1(i) ^ passwordHash3(i)).toByte
        }
        passwordHash1
    }

}
