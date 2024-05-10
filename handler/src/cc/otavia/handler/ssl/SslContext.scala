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

import java.io.{ByteArrayInputStream, File, IOException, InputStream}
import java.security.*
import java.security.cert.{Certificate, CertificateException, CertificateFactory, X509Certificate}
import java.security.spec.{InvalidKeySpecException, PKCS8EncodedKeySpec}
import javax.crypto.spec.PBEKeySpec
import javax.crypto.{Cipher, EncryptedPrivateKeyInfo, NoSuchPaddingException, SecretKeyFactory}
import javax.net.ssl.*
import scala.language.unsafeNulls
import scala.util.Try

/** A secure socket protocol implementation which acts as a factory for [[SSLEngine]] and SslHandler. Internally, it is
 *  implemented via JDK's [[SSLContext]]. <h3>Making your server support SSL/TLS</h3>
 *  {{{
 *      val pipeline = channel.pipeline()
 *      val sslCtx = SslContextBuilder.forServer(...).build()
 *      pipeline.addLast("tls", sslCtx.newHandler())
 *  }}}
 *  <h3>Making your client support SSL/TLS</h3>
 *  {{{
 *      val pipeline = channel.pipeline()
 *      val sslCtx = SslContextBuilder.forClient().build()
 *      pipeline.addLast("tls", sslCtx.newHandler(host, port))
 *  }}}
 *  @param startTls
 *    true if the first write request shouldn't be encrypted by the [[SSLEngine]]
 */
abstract class SslContext protected (val startTls: Boolean = false) {

    /** Returns true if and only if this context is for server-side. */
    def isServer: Boolean = !isClient

    /** Returns the true if and only if this context is for client-side. */
    def isClient: Boolean

    /** Returns the list of enabled cipher suites, in the order of preference. */
    def cipherSuites: List[String]

    /** Returns the size of the cache used for storing SSL session objects. */
    def sessionCacheSize: Long = sessionContext.getSessionCacheSize

    /** Returns the timeout for the cached SSL session objects, in seconds. */
    def sessionTimeout: Long = sessionContext.getSessionTimeout

    /** Creates a new [[SSLEngine]]. */
    def newEngine(): SSLEngine

    /** Creates a new [[SSLEngine]].
     *  @param peerHost
     *    the non-authoritative name of the host
     *  @param peerPort
     *    the non-authoritative port
     *  @return
     *    a new [[SSLEngine]]
     */
    def newEngine(peerHost: String, peerPort: Int): SSLEngine

    /** Returns the [[SSLSessionContext]] object held by this context. */
    def sessionContext: SSLSessionContext

    /** Create a new [[SslHandler]]. */
    final def newHandler(): SslHandler = newHandler(startTls)

    /** Create a new [[SslHandler]]. */
    protected def newHandler(startTls: Boolean): SslHandler = new SslHandler(newEngine(), startTls)

    /** Create a new [[SslHandler]]. */
    def newHandler(peerHost: String, peerPort: Int): SslHandler =
        new SslHandler(newEngine(peerHost, peerPort), startTls)

}

object SslContext {

    private val ALIAS = "key"

    val X509_CERT_FACTORY: CertificateFactory = CertificateFactory.getInstance("X.509")

    private final val OID_PKCS5_PBES2 = "1.2.840.113549.1.5.13"
    private final val PBES2           = "PBES2"

    /** Returns the default server-side implementation provider currently in use.
     *  @return
     *    only [[SslProvider.JDK]] supported.
     */
    def defaultServerProvider(): SslProvider = defaultProvider()

    /** Returns the default client-side implementation provider currently in use.
     *  @return
     *    only [[SslProvider.JDK]] supported.
     */
    def defaultClientProvider(): SslProvider = defaultProvider()

    private def defaultProvider(): SslProvider = SslProvider.JDK

    def newServerContextInternal(
        provider: SslProvider,
        sslContextProvider: Option[Provider],
        trustManagerFactory: Option[TrustManagerFactory],
        keyManagerFactory: Option[KeyManagerFactory],
        ciphers: Option[Seq[String]],
        cipherFilter: CipherSuiteFilter,
        apn: Option[ApplicationProtocolConfig],
        sessionCacheSize: Long,
        sessionTimeout: Long,
        clientAuth: ClientAuth,
        protocols: Array[String],
        startTls: Boolean,
        enableOcsp: Boolean,
        keyStoreType: String
    ): SslContext = {
        provider match
            case SslProvider.JDK =>
                if (enableOcsp)
                    throw new IllegalArgumentException("OCSP is not supported with this SslProvider: " + provider)

                if (keyManagerFactory.isEmpty)
                    throw new NullPointerException("server side must have key or keyManagerFactory")

                new JdkSslServerContext(
                  sslContextProvider,
                  trustManagerFactory,
                  keyManagerFactory.get,
                  ciphers,
                  cipherFilter,
                  apn,
                  sessionCacheSize,
                  sessionTimeout,
                  clientAuth,
                  protocols,
                  startTls,
                  keyStoreType
                )
    }

    def newClientContextInternal(
        provider: SslProvider,
        sslContextProvider: Option[Provider],
        trustManagerFactory: Option[TrustManagerFactory],
        keyManagerFactory: Option[KeyManagerFactory],
        ciphers: Option[Seq[String]],
        cipherFilter: CipherSuiteFilter,
        apn: Option[ApplicationProtocolConfig],
        protocols: Array[String],
        sessionCacheSize: Long,
        sessionTimeout: Long,
        enableOcsp: Boolean,
        keyStoreType: String
    ): SslContext = {
        provider match
            case SslProvider.JDK =>
                if (enableOcsp)
                    throw new IllegalArgumentException("OCSP is not supported with this SslProvider: " + provider)

                new JdkSslClientContext(
                  sslContextProvider,
                  trustManagerFactory,
                  keyManagerFactory,
                  ciphers,
                  cipherFilter,
                  apn,
                  protocols,
                  sessionCacheSize,
                  sessionTimeout,
                  keyStoreType
                )
    }

    /** Generates a key specification for an (encrypted) private key.
     *
     *  @param password
     *    characters, if [[None]] an unencrypted key is assumed
     *  @param key
     *    bytes of the DER encoded private key
     *  @throws IOException
     *    if parsing [[key]] fails
     *  @throws NoSuchAlgorithmException
     *    if the algorithm used to encrypt [[key]] is unknown
     *  @throws NoSuchPaddingException
     *    if the padding scheme specified in the decryption algorithm is unknown
     *  @throws InvalidKeySpecException
     *    if the decryption key based on [[password]] cannot be generated
     *  @throws InvalidKeyException
     *    if the decryption key based on [[password]] cannot be used to decrypt [[key]]
     *  @throws InvalidAlgorithmParameterException
     *    if decryption algorithm parameters are somehow faulty
     *  @return
     *    a key specification
     */
    @throws[IOException]
    @throws[NoSuchAlgorithmException]
    @throws[NoSuchPaddingException]
    @throws[InvalidKeySpecException]
    @throws[InvalidKeyException]
    @throws[InvalidAlgorithmParameterException]
    protected def generateKeySpec(password: Option[Array[Char]], key: Array[Byte]): PKCS8EncodedKeySpec = password match
        case None => new PKCS8EncodedKeySpec(key)
        case Some(value) =>
            val encryptedPrivateKeyInfo = new EncryptedPrivateKeyInfo(key)
            val pbeAlgorithm            = getPBEAlgorithm(encryptedPrivateKeyInfo)
            val keyFactory              = SecretKeyFactory.getInstance(pbeAlgorithm)
            val pbeKeySpec              = new PBEKeySpec(value)
            val pbeKey                  = keyFactory.generateSecret(pbeKeySpec)
            val cipher                  = Cipher.getInstance(pbeAlgorithm)
            cipher.init(Cipher.DECRYPT_MODE, pbeKey, encryptedPrivateKeyInfo.getAlgParameters)
            encryptedPrivateKeyInfo.getKeySpec(cipher)

    private def getPBEAlgorithm(encryptedPrivateKeyInfo: EncryptedPrivateKeyInfo): String = {
        val parameters = encryptedPrivateKeyInfo.getAlgParameters
        val algName    = encryptedPrivateKeyInfo.getAlgName
        // Java 8 ~ 16 returns OID_PKCS5_PBES2
        // Java 17+ returns PBES2
        if (parameters != null && (OID_PKCS5_PBES2.equals(algName) || PBES2.equals(algName))) {
            /*
             * This should be "PBEWith<prf>And<encryption>".
             * Relying on the toString() implementation is potentially
             * fragile but acceptable in this case since the JRE depends on
             * the toString() implementation as well.
             * In the future, if necessary, we can parse the value of
             * parameters.getEncoded() but the associated complexity and
             * unlikeliness of the JRE implementation changing means that
             * Tomcat will use to toString() approach for now.
             */
            parameters.toString
        } else encryptedPrivateKeyInfo.getAlgName
    }

    def toPrivateKey(keyFile: File, keyPassword: Option[String]): PrivateKey =
        getPrivateKeyFromByteBuffer(PemReader.readPrivateKey(keyFile), keyPassword)

    def toPrivateKey(keyInputStream: InputStream, keyPassword: Option[String]): PrivateKey =
        getPrivateKeyFromByteBuffer(PemReader.readPrivateKey(keyInputStream), keyPassword)

    def getPrivateKeyFromByteBuffer(encodedKey: Array[Byte], keyPassword: Option[String]): PrivateKey = {
        val encodedKeySpec  = generateKeySpec(keyPassword.map(_.toCharArray), encodedKey)
        var res: PrivateKey = null
        try {
            res = KeyFactory.getInstance("RSA").generatePrivate(encodedKeySpec)
        } catch {
            case ignore: InvalidKeySpecException =>
                try {
                    res = KeyFactory.getInstance("DSA").generatePrivate(encodedKeySpec)
                } catch {
                    case ignore2: InvalidKeySpecException =>
                        try {
                            res = KeyFactory.getInstance("EC").generatePrivate(encodedKeySpec)
                        } catch {
                            case e: InvalidKeySpecException =>
                                throw new InvalidKeySpecException("Neither RSA, DSA nor EC worked", e)
                        }
                }
        }
        res
    }

    @throws[CertificateException]
    def toX509Certificates(file: File): Array[X509Certificate] =
        getCertificatesFromBuffers(PemReader.readCertificates(file))

    @throws[CertificateException]
    def toX509Certificates(in: InputStream): Array[X509Certificate] =
        getCertificatesFromBuffers(PemReader.readCertificates(in))

    private def getCertificatesFromBuffers(certs: Array[Array[Byte]]): Array[X509Certificate] = {
        val cf = CertificateFactory.getInstance("X.509")
        val res = certs.map { cert =>
            val is = new ByteArrayInputStream(cert)
            cf.generateCertificate(is).asInstanceOf[X509Certificate]
        }
        res
    }

    def buildTrustManagerFactory(
        certCollection: Array[X509Certificate],
        keyStoreType: String
    ): TrustManagerFactory = {
        val ks = KeyStore.getInstance(keyStoreType)
        ks.load(null, null)

        var i = 1
        for (cert <- certCollection) {
            val alias = i.toString
            ks.setCertificateEntry(alias, cert)
            i += 1
        }

        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)

        factory.init(ks)
        factory
    }

    def buildKeyManagerFactory(
        certChainFile: Array[X509Certificate],
        keyAlgorithm: Option[String],
        key: PrivateKey,
        keyPassword: Option[String],
        keyStore: String
    ): KeyManagerFactory = {
        val algorithm        = keyAlgorithm.getOrElse(KeyManagerFactory.getDefaultAlgorithm)
        val keyPasswordChars = keyPassword.map(_.toCharArray).getOrElse(Array.empty[Char])
        val ks               = KeyStore.getInstance(keyStore)
        ks.load(null, null)
        ks.setKeyEntry(ALIAS, key, keyPasswordChars, certChainFile.asInstanceOf[Array[Certificate | Null] | Null])
        val kmf = KeyManagerFactory.getInstance(algorithm)
        kmf.init(ks, keyPasswordChars)
        kmf
    }

}
