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

package cc.otavia.handler.ssl

import cc.otavia.buffer.Buffer

import java.security.{KeyManagementException, NoSuchAlgorithmException, Provider}
import javax.net.ssl.{SSLContext, TrustManager}
import scala.language.unsafeNulls

/** Constants for SSL packets. */
object SslUtils {

    // See https://tools.ietf.org/html/rfc8446#appendix-B.4
    val TLSV13_CIPHERS: Set[String] = Set(
      "TLS_AES_256_GCM_SHA384",
      "TLS_CHACHA20_POLY1305_SHA256",
      "TLS_AES_128_GCM_SHA256",
      "TLS_AES_128_CCM_8_SHA256",
      "TLS_AES_128_CCM_SHA256"
    )

    private val DTLS_1_0: Short                  = 0xfeff.toShort
    private val DTLS_1_2: Short                  = 0xfefd.toShort
    private val DTLS_1_3: Short                  = 0xfefc.toShort
    private val DTLS_RECORD_HEADER_LENGTH: Short = 13

    /** GMSSL Protocol Version */
    val GMSSL_PROTOCOL_VERSION = 0x101

    val INVALID_CIPHER = "SSL_NULL_WITH_NULL_NULL"

    /** change cipher spec */
    val SSL_CONTENT_TYPE_CHANGE_CIPHER_SPEC = 20

    /** alert */
    val SSL_CONTENT_TYPE_ALERT = 21

    /** handshake */
    val SSL_CONTENT_TYPE_HANDSHAKE = 22

    /** application data */
    val SSL_CONTENT_TYPE_APPLICATION_DATA = 23

    /** HeartBeat Extension */
    val SSL_CONTENT_TYPE_EXTENSION_HEARTBEAT = 24

    /** the length of the ssl record header (in bytes) */
    val SSL_RECORD_HEADER_LENGTH = 5

    /** Not enough data in buffer to parse the record length */
    val NOT_ENOUGH_DATA: Int = -1

    /** data is not encrypted */
    val NOT_ENCRYPTED: Int = -2

    val DEFAULT_CIPHER_SUITES: Array[String] = Array(
      "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
      "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
      "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
      "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
      "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
      // AES256 requires JCE unlimited strength jurisdiction policy files.
      "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",
      // GCM (Galois/Counter Mode) requires JDK 8.
      "TLS_RSA_WITH_AES_128_GCM_SHA256",
      "TLS_RSA_WITH_AES_128_CBC_SHA",
      // AES256 requires JCE unlimited strength jurisdiction policy files.
      "TLS_RSA_WITH_AES_256_CBC_SHA",
      "TLS_AES_128_GCM_SHA256",
      "TLS_AES_256_GCM_SHA384"
    )
    private var DEFAULT_TLSV13_CIPHER_SUITES: Array[String] = Array("TLS_AES_128_GCM_SHA256", "TLS_AES_256_GCM_SHA384")

    val TLSV13_CIPHER_SUITES: Array[String] = Array("TLS_AES_128_GCM_SHA256", "TLS_AES_256_GCM_SHA384")

    private var TLSV1_3_JDK_SUPPORTED: Boolean       = true
    private var TLSV1_3_JDK_DEFAULT_ENABLED: Boolean = true

    def isTLSv13EnabledByJDK(provider: Option[Provider]): Boolean = provider match
        case Some(value) => isTLSv13EnabledByJDK0(provider)
        case None        => TLSV1_3_JDK_SUPPORTED

    def isTLSv13EnabledByJDK0(provider: Option[Provider]): Boolean = {
        try {
            newInitContext(provider).getDefaultSSLParameters.getProtocols.contains(SslProtocols.TLS_v1_3)
        } catch {
            case cause: Throwable => false
        }
    }

    @throws[NoSuchAlgorithmException]
    @throws[KeyManagementException]
    private def newInitContext(provider: Option[Provider]): SSLContext = {
        val context = provider match
            case None        => SSLContext.getInstance("TLS")
            case Some(value) => SSLContext.getInstance("TLS", value)
        context.init(null, Array.empty[TrustManager], null)
        context
    }

    def getEncryptedPacketLength(buffer: Buffer, offset: Int): Int = {
        var packetLength = 0
        // SSLv3 or TLS - Check ContentType
        var tls = buffer.getUnsignedByte(offset) match
            case SSL_CONTENT_TYPE_CHANGE_CIPHER_SPEC  => true
            case SSL_CONTENT_TYPE_ALERT               => true
            case SSL_CONTENT_TYPE_HANDSHAKE           => true
            case SSL_CONTENT_TYPE_APPLICATION_DATA    => true
            case SSL_CONTENT_TYPE_EXTENSION_HEARTBEAT => true
            case _                                    => false

        if (tls) {
            // SSLv3 or TLS or GMSSLv1.0 or GMSSLv1.1 - Check ProtocolVersion
            val major   = buffer.getUnsignedByte(offset + 1)
            val version = buffer.getShort(offset + 1)
            if (major == 3 || version == GMSSL_PROTOCOL_VERSION) {
                // SSLv3 or TLS or GMSSLv1.0 or GMSSLv1.1
                packetLength = buffer.getUnsignedShort(offset + 3) + SSL_RECORD_HEADER_LENGTH
                if (packetLength < SSL_RECORD_HEADER_LENGTH) tls = false
            } else if (version == DTLS_1_0 || version == DTLS_1_2 || version == DTLS_1_3) {
                if (buffer.readableBytes < offset + DTLS_RECORD_HEADER_LENGTH) {
                    packetLength = NOT_ENOUGH_DATA
                } else {
                    // length is the last 2 bytes in the 13 byte header.
                    packetLength = buffer.getUnsignedShort(offset + DTLS_RECORD_HEADER_LENGTH - 2) +
                        DTLS_RECORD_HEADER_LENGTH
                }
            } else {
                // Neither SSLv3 or TLSv1 (i.e. SSLv2 or bad data)
                tls = false
            }
        }

        if (!tls) {
            // SSLv2 or bad data - Check the version
            val headerLen = if ((buffer.getUnsignedByte(offset) & 0x80) != 0) 2 else 3
            val major     = buffer.getUnsignedByte(offset + headerLen + 1)
            if (major == 2 || major == 3) {
                // SSLv2
                packetLength =
                    if (headerLen == 2) (buffer.getShort(offset) & 0x7fff) + 2
                    else (buffer.getShort(offset) & 0x3fff) + 3

                if (packetLength <= headerLen) packetLength = NOT_ENOUGH_DATA
            } else packetLength = NOT_ENOUGH_DATA
        }

        packetLength
    }

}
