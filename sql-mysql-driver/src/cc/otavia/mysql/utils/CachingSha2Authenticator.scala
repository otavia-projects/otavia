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

object CachingSha2Authenticator {

    def encode(password: Array[Byte], nonce: Array[Byte]): Array[Byte] = {
        val messageDigest =
            try {
                MessageDigest.getInstance("SHA-256")
            } catch {
                case e: NoSuchAlgorithmException => throw new RuntimeException(e)
            }

        // SHA256(password)
        val passwordHash1 = messageDigest.digest(password)
        messageDigest.reset()

        // SHA256(SHA256(password))
        val passwordHash2 = messageDigest.digest(passwordHash1)
        messageDigest.reset()

        //  SHA256(SHA256(SHA256(password)), Nonce)
        messageDigest.update(passwordHash2)
        val passwordDigest = messageDigest.digest(nonce)

        // result = passwordHash1 XOR passwordDigest
        for (i <- 0 until passwordHash1.length) {
            passwordHash1(i) = (passwordHash1(i) ^ passwordDigest(i)).toByte
        }
        passwordHash1
    }

}
