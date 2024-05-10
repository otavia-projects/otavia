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

import java.io.{File, InputStream}
import java.security.cert.X509Certificate
import java.security.{KeyStore, PrivateKey, Provider}
import javax.net.ssl.{KeyManagerFactory, TrustManager, TrustManagerFactory}
import scala.language.unsafeNulls

/** Builder for configuring a new [[SslContext]] for creation.
 *
 *  @param forServer
 *    is server-side [[SslContext]]
 */
class SslContextBuilder private (private val forServer: Boolean) {

    private var provider: SslProvider                               = SslProvider.JDK
    private var sslContextProvider: Option[Provider]                = None
    private var trustCertCollection: Option[Array[X509Certificate]] = None
    private var trustManagerFactory: Option[TrustManagerFactory]    = None
    private var keyCertChain: Option[Array[X509Certificate]]        = None
    private var key: Option[PrivateKey]                             = None
    private var keyPassword: Option[String]                         = None
    private var keyManagerFactory: Option[KeyManagerFactory]        = None
    private var ciphers: Option[Seq[String]]                        = None
    private var cipherFilter: CipherSuiteFilter                     = IdentityCipherSuiteFilter.INSTANCE
    private var apn: Option[ApplicationProtocolConfig]              = None
    private var sessionCacheSize                                    = 0L
    private var sessionTimeout                                      = 0L
    private var clientAuth: ClientAuth                              = ClientAuth.NONE
    private var protocols: Array[String]                            = _
    private var startTls                                            = false
    private var enableOcsp                                          = false
    private var keyStoreType                                        = KeyStore.getDefaultType

    /** The SslContext implementation to use. if not set to uses the default one. */
    def sslProvider(provider: SslProvider): this.type = {
        this.provider = provider
        this
    }

    /** Sets the KeyStore type that should be used.  if not set to uses the default one. */
    def keyStoreType(keyStoreType: String): this.type = {
        this.keyStoreType = keyStoreType
        this
    }

    /** The SSLContext Provider to use. if not set to uses the default one. This is only used with [[SslProvider.JDK]].
     */
    def sslContextProvider(sslContextProvider: Provider): this.type = {
        this.sslContextProvider = Some(sslContextProvider)
        this
    }

    /** Trusted certificates for verifying the remote endpoint's certificate. The file should contain an X.509
     *  certificate collection in PEM format. null uses the system default.
     */
    def trustManager(trustCertCollectionFile: File): this.type = {
        try {
            trustManager(SslContext.toX509Certificates(trustCertCollectionFile))
        } catch {
            case e: Exception =>
                val msg = "File does not contain valid certificates: "
                    + trustCertCollectionFile
                throw new IllegalArgumentException(msg, e)
        }
    }

    /** Trusted certificates for verifying the remote endpoint's certificate. The input stream should contain an X.509
     *  certificate collection in PEM format. null uses the system default. The caller is responsible for calling
     *  [[InputStream]].close() after [[build]]() has been called.
     */
    def trustManager(trustCertCollectionInputStream: InputStream): this.type = {
        try {
            trustManager(SslContext.toX509Certificates(trustCertCollectionInputStream))
        } catch {
            case e: Exception =>
                throw new IllegalArgumentException("Input stream does not contain valid certificates.", e)
        }
    }

    /** Trusted certificates for verifying the remote endpoint's certificate, null uses the system default. */
    def trustManager(trustCertCollection: Array[X509Certificate]): this.type = {
        this.trustCertCollection = Some(trustCertCollection)
        trustManagerFactory = null
        this
    }

    /** Trusted manager for verifying the remote endpoint's certificate. null uses the system default. */
    def trustManager(trustManagerFactory: TrustManagerFactory): this.type = {
        trustCertCollection = None
        this.trustManagerFactory = Some(trustManagerFactory)
        this
    }

    /** Identifying certificate for this host. keyCertChainFile and keyFile may be null for client contexts, which
     *  disables mutual authentication.
     *
     *  @param keyCertChainFile
     *    an X.509 certificate chain file in PEM format
     *  @param keyFile
     *    a PKCS#8 private key file in PEM format
     *  @param keyPassword
     *    the password of the keyFile, or null if it's not password-protected
     */
    def keyManager(keyCertChainFile: File, keyFile: File, keyPassword: Option[String]): this.type = {
        var keyCertChain: Array[X509Certificate] = null
        var key: PrivateKey                      = null
        try {
            keyCertChain = SslContext.toX509Certificates(keyCertChainFile)
        } catch {
            case e: Exception =>
                throw new IllegalArgumentException("File does not contain valid certificates: " + keyCertChainFile, e)
        }
        try {
            key = SslContext.toPrivateKey(keyFile, keyPassword)
        } catch {
            case e: Exception =>
                throw new IllegalArgumentException("File does not contain valid private key: " + keyFile, e)
        }
        keyManager(key, keyPassword, keyCertChain: _*)
    }

    /** Identifying certificate for this host. keyCertChainInputStream and keyInputStream may be null for client
     *  contexts, which disables mutual authentication.
     *
     *  @param keyCertChainInputStream
     *    an input stream for an X.509 certificate chain in PEM format. The caller is responsible for calling
     *    [[InputStream]].close() after [[build]]() has been called.
     *  @param keyInputStream
     *    an input stream for a PKCS#8 private key in PEM format. The caller is responsible for calling
     *    [[InputStream]].close() after [[build]]() has been called.
     *  @param keyPassword
     *    the password of the keyInputStream, or null if it's not password-protected
     */
    def keyManager(
        keyCertChainInputStream: InputStream,
        keyInputStream: InputStream,
        keyPassword: Option[String]
    ): this.type = {
        var keyCertChain: Array[X509Certificate] = null
        var key: PrivateKey                      = null
        try {
            keyCertChain = SslContext.toX509Certificates(keyCertChainInputStream)
        } catch {
            case e: Exception => throw new IllegalArgumentException("Input stream not contain valid certificates.", e)
        }
        try {
            key = SslContext.toPrivateKey(keyInputStream, keyPassword)
        } catch {
            case e: Exception =>
                throw new IllegalArgumentException("Input stream does not contain valid private key.", e)
        }
        keyManager(key, keyPassword, keyCertChain: _*)
    }

    /** Identifying certificate for this host. keyCertChain and key may be null for client contexts, which disables
     *  mutual authentication.
     *
     *  @param key
     *    a PKCS#8 private key file
     *  @param keyPassword
     *    the password of the key, or null if it's not password-protected
     *  @param keyCertChain
     *    an X.509 certificate chain
     */
    def keyManager(key: PrivateKey, keyPassword: Option[String], keyCertChain: X509Certificate*): this.type = {
        if (forServer) {
            assert(keyCertChain.nonEmpty, "keyCertChain must not be empty!")
            assert(key != null, "key required for servers")
        }
        this.keyCertChain = Some(keyCertChain.toArray)
        this.key = Option(key)
        this.keyPassword = keyPassword
        this.keyManagerFactory = None
        this
    }

    /** Identifying manager for this host. keyManagerFactory may be null for client contexts, which disables mutual
     *  authentication. Using a [[KeyManagerFactory]] is only supported for [[SslProvider.JDK]].
     */
    def keyManager(keyManagerFactory: KeyManagerFactory): this.type = {
        this.keyCertChain = None
        this.key = None
        this.keyPassword = None
        this.keyManagerFactory = Some(keyManagerFactory)
        this
    }

    /** The cipher suites to enable, in the order of preference. cipherFilter will be applied to the ciphers before use.
     *  If ciphers is not setting, then the default cipher suites will be used.
     */
    def ciphers(ciphers: Seq[String], cipherFilter: CipherSuiteFilter): this.type = {
        this.cipherFilter = cipherFilter
        this.ciphers = Some(ciphers)
        this
    }

    /** Application protocol negotiation configuration. null disables support. */
    def applicationProtocolConfig(apn: ApplicationProtocolConfig): this.type = {
        this.apn = Some(apn)
        this
    }

    /** Set the size of the cache used for storing SSL session objects. 0 to use the default value. */
    def sessionCacheSize(sessionCacheSize: Long): this.type = {
        this.sessionCacheSize = sessionCacheSize
        this
    }

    /** Set the timeout for the cached SSL session objects, in seconds. 0 to use the default value. */
    def sessionTimeout(sessionTimeout: Long): this.type = {
        this.sessionTimeout = sessionTimeout
        this
    }

    /** Sets the client authentication mode. */
    def clientAuth(clientAuth: ClientAuth): this.type = {
        this.clientAuth = clientAuth
        this
    }

    /** The TLS protocol versions to enable.
     *  @param protocols
     *    The protocols to enable, or null to enable the default protocols.
     */
    def protocols(protocols: String*): this.type = {
        this.protocols = protocols.toArray
        this
    }

    /** true if the first write request shouldn't be encrypted. */
    def startTls(startTls: Boolean): this.type = {
        this.startTls = startTls
        this
    }

    /** Create new [[SslContext]] instance with configured settings. */
    def build(): SslContext = {
        trustCertCollection match
            case Some(trustCerts) =>
                val factory = SslContext.buildTrustManagerFactory(trustCerts, keyStoreType)
                this.trustManagerFactory = Some(factory)
            case None =>

        keyCertChain match
            case Some(keyCerts) =>
                val factory = SslContext.buildKeyManagerFactory(keyCerts, None, key.get, keyPassword, keyStoreType)
                this.keyManagerFactory = Some(factory)
            case None =>

        // format: off
        if (forServer) SslContext.newServerContextInternal(provider, sslContextProvider,
                trustManagerFactory, keyManagerFactory,
                ciphers, cipherFilter, apn, sessionCacheSize, sessionTimeout, clientAuth, protocols, startTls,
                enableOcsp, keyStoreType)
        else SslContext.newClientContextInternal(provider, sslContextProvider,
            trustManagerFactory, keyManagerFactory,
            ciphers, cipherFilter, apn, protocols, sessionCacheSize, sessionTimeout, enableOcsp, keyStoreType)
        // format: on
    }

}

object SslContextBuilder {

    /** Creates a builder for new client-side [[SslContext]]. */
    def forClient(): SslContextBuilder = new SslContextBuilder(false)

    /** Creates a builder for new server-side [[SslContext]].
     *  @param keyCertChainFile
     *    an X.509 certificate chain file in PEM format
     *  @param keyFile
     *    a PKCS#8 private key file in PEM format
     */
    def forServer(keyCertChainFile: File, keyFile: File): SslContextBuilder =
        new SslContextBuilder(true).keyManager(keyCertChainFile, keyFile, None)

    /** Creates a builder for new server-side [[SslContext]].
     *
     *  @param keyCertChainInputStream
     *    an input stream for an X.509 certificate chain in PEM format. The caller is responsible for calling
     *    [[InputStream]].close() after [[build]]() has been called.
     *  @param keyInputStream
     *    an input stream for a PKCS#8 private key in PEM format. The caller is responsible for calling
     *    [[InputStream]].close() after [[build]]() has been called.
     */
    def forServer(keyCertChainInputStream: InputStream, keyInputStream: InputStream): SslContextBuilder =
        new SslContextBuilder(true).keyManager(keyCertChainInputStream, keyInputStream, None)

    /** Creates a builder for new server-side [[SslContext]].
     *  @param key
     *    a PKCS#8 private key
     *  @param keyCertChain
     *    the X.509 certificate chain
     */
    def forServer(key: PrivateKey, keyCertChain: X509Certificate*): SslContextBuilder =
        new SslContextBuilder(true).keyManager(key, None, keyCertChain: _*)

    /** Creates a builder for new server-side [[SslContext]].
     *
     *  @param keyCertChainFile
     *    an X.509 certificate chain file in PEM format
     *  @param keyFile
     *    a PKCS#8 private key file in PEM format
     *  @param keyPassword
     *    the password of the keyFile.
     */
    def forServer(keyCertChainFile: File, keyFile: File, keyPassword: String): SslContextBuilder =
        new SslContextBuilder(true).keyManager(keyCertChainFile, keyFile, Some(keyPassword))

    /** Creates a builder for new server-side [[SslContext]].
     *
     *  @param keyCertChainInputStream
     *    an input stream for an X.509 certificate chain in PEM format. The caller is responsible for calling
     *    [[InputStream]].close() after [[build]]() has been called.
     *  @param keyInputStream
     *    an input stream for a PKCS#8 private key in PEM format. The caller is responsible for calling
     *    [[InputStream]].close() after [[build]]() has been called.
     *  @param keyPassword
     *    the password of the keyFile.
     */
    def forServer(
        keyCertChainInputStream: InputStream,
        keyInputStream: InputStream,
        keyPassword: String
    ): SslContextBuilder =
        new SslContextBuilder(true).keyManager(keyCertChainInputStream, keyInputStream, Some(keyPassword))

    /** Creates a builder for new server-side [[SslContext]].
     *
     *  @param key
     *    a PKCS#8 private key
     *  @param keyPassword
     *    the password of the keyFile.
     *  @param keyCertChain
     *    the X.509 certificate chain
     */
    def forServer(key: PrivateKey, keyPassword: String, keyCertChain: X509Certificate*): SslContextBuilder =
        new SslContextBuilder(true).keyManager(key, Some(keyPassword), keyCertChain: _*)

    /** Creates a builder for new server-side [[SslContext]].
     *
     *  @param keyManagerFactory
     *    non-null factory for server's private key
     */
    def forServer(keyManagerFactory: KeyManagerFactory): SslContextBuilder =
        new SslContextBuilder(true).keyManager(keyManagerFactory)

}
