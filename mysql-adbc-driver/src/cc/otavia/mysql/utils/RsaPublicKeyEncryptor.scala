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

import java.security.interfaces.RSAPublicKey
import java.security.spec.{InvalidKeySpecException, X509EncodedKeySpec}
import java.security.{InvalidKeyException, KeyFactory, NoSuchAlgorithmException, PublicKey}
import java.util.Base64
import javax.crypto.{BadPaddingException, Cipher, IllegalBlockSizeException, NoSuchPaddingException}
import scala.language.unsafeNulls

object RsaPublicKeyEncryptor {

    def encrypt(password: Array[Byte], nonce: Array[Byte], serverRsaPublicKey: String): Array[Byte] = {
        val rsaPublicKey       = generateRsaPublicKey(serverRsaPublicKey)
        val obfuscatedPassword = obfuscate(password, nonce)
        encrypt(rsaPublicKey, obfuscatedPassword)
    }

    @throws[InvalidKeySpecException]
    @throws[NoSuchAlgorithmException]
    private def generateRsaPublicKey(serverRsaPublicKey: String) = {
        val content = serverRsaPublicKey
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\n", "")
        val key        = Base64.getDecoder.decode(content.getBytes)
        val keySpec    = new X509EncodedKeySpec(key)
        val keyFactory = KeyFactory.getInstance("RSA")
        keyFactory.generatePublic(keySpec).asInstanceOf[RSAPublicKey]
    }

    private def obfuscate(password: Array[Byte], nonce: Array[Byte]) = { // the password input can be mutated here
        for (i <- password.indices) {
            password(i) = (password(i) ^ nonce(i % nonce.length)).toByte
        }
        password
    }

    @throws[NoSuchAlgorithmException]
    @throws[NoSuchPaddingException]
    @throws[InvalidKeyException]
    @throws[IllegalBlockSizeException]
    @throws[BadPaddingException]
    private def encrypt(key: PublicKey, plainData: Array[Byte]) = {
        val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.doFinal(plainData)
    }

}
