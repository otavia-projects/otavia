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

import cc.otavia.handler.ssl.ClientAuth.{NONE, OPTIONAL, REQUIRE}
import cc.otavia.handler.ssl.JdkSslContext.*
import cc.otavia.handler.ssl.SslUtils.DEFAULT_CIPHER_SUITES
import cc.otavia.handler.ssl.protocol.JdkApplicationProtocolNegotiator

import java.security.Provider
import javax.net.ssl.{SSLContext, SSLEngine, SSLSessionContext}
import scala.collection.mutable
import scala.language.unsafeNulls

abstract class JdkSslContext(
    val context: SSLContext,
    val isClient: Boolean,
    ciphers: Option[Seq[String]],
    cipherFilter: CipherSuiteFilter,
    val apn: JdkApplicationProtocolNegotiator,
    val clientAuth: ClientAuth,
    val pts: Array[String],
    startTls: Boolean
) extends SslContext(startTls) {

    private var defaultCiphers: List[String]  = _
    private var supportedCiphers: Set[String] = _

    private val protocols: Array[String] = if (pts.isEmpty) DEFAULT_PROTOCOLS else pts

    val cipherSuites: List[String] =
        cipherFilter.filterCipherSuites(ciphers, DEFAULT_CIPHERS, SUPPORTED_CIPHERS).toList

    /** Returns the JDK [[SSLSessionContext]] object held by this context. */
    override def sessionContext: SSLSessionContext =
        if (isServer) context.getServerSessionContext
        else context.getClientSessionContext

    override def newEngine(): SSLEngine = configureAndWrapEngine(context.createSSLEngine())

    override def newEngine(peerHost: String, peerPort: Int): SSLEngine =
        configureAndWrapEngine(context.createSSLEngine(peerHost, peerPort))

    private def configureAndWrapEngine(engine: SSLEngine): SSLEngine = {
        engine.setEnabledCipherSuites(cipherSuites.toArray)
        engine.setEnabledProtocols(protocols)
        engine.setUseClientMode(isClient)
        if (isServer) clientAuth match
            case NONE     =>
            case OPTIONAL => engine.setWantClientAuth(true)
            case REQUIRE  => engine.setNeedClientAuth(true)
        val factory = apn.wrapperFactory
        factory.wrapSslEngine(engine, apn, isServer)
    }

}

object JdkSslContext {

    val PROTOCOL = "TLS"

    private var DEFAULT_PROTOCOLS: Array[String]          = _
    private var DEFAULT_CIPHERS: Array[String]            = _
    private var DEFAULT_CIPHERS_NON_TLSV13: List[String]  = _
    private var SUPPORTED_CIPHERS: Set[String]            = _
    private var SUPPORTED_CIPHERS_NON_TLSV13: Set[String] = _
    private var DEFAULT_PROVIDER: Provider                = _

    init()

    def init(): Unit = {
        val context =
            try {
                val ctx = SSLContext.getInstance(PROTOCOL)
                ctx.init(null, null, null)
                ctx
            } catch {
                case e: Exception => throw new Error("failed to initialize the default SSL context", e)
            }

        DEFAULT_PROVIDER = context.getProvider

        val engine = context.createSSLEngine()
        DEFAULT_PROTOCOLS = defaultProtocols(context, engine)

        SUPPORTED_CIPHERS = supportedCiphers(engine)

        DEFAULT_CIPHERS = defaultCiphers(engine, SUPPORTED_CIPHERS)
    }

    private def defaultProtocols(context: SSLContext, engine: SSLEngine): Array[String] = {
        // Choose the sensible default list of protocols that respects JDK flags, eg. jdk.tls.client.protocols
        val supportedProtocols = context.getDefaultSSLParameters.getProtocols.toSet
        val protocols = Array(SslProtocols.TLS_v1_3, SslProtocols.TLS_v1_2, SslProtocols.TLS_v1_1, SslProtocols.TLS_v1)
            .filter(p => supportedProtocols.contains(p))

        if (protocols.nonEmpty) protocols else engine.getEnabledProtocols
    }

    private def supportedCiphers(engine: SSLEngine): Set[String] = {
        // Choose the sensible default list of cipher suites.
        val supportedCiphers = engine.getSupportedCipherSuites
        val set              = mutable.HashSet.empty[String]
        supportedCiphers.foreach { supportedCipher =>
            set.add(supportedCipher)
            // IBM's J9 JVM utilizes a custom naming scheme for ciphers and only returns ciphers with the "SSL_"
            // prefix instead of the "TLS_" prefix (as defined in the JSSE cipher suite names [1]). According to IBM's
            // documentation [2] the "SSL_" prefix is "interchangeable" with the "TLS_" prefix.
            // See the IBM forum discussion [3] and issue on IBM's JVM [4] for more details.
            // [1] https://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html#ciphersuites
            // [2] https://www.ibm.com/support/knowledgecenter/en/SSYKE2_8.0.0/com.ibm.java.security.component.80.doc/
            // security-component/jsse2Docs/ciphersuites.html
            // [3] https://www.ibm.com/developerworks/community/forums/html/topic?id=9b5a56a9-fa46-4031-b33b-df91e28d77c2
            // [4] https://www.ibm.com/developerworks/rfe/execute?use_case=viewRfe&CR_ID=71770
            if (supportedCipher.startsWith("SSL_")) {
                val tlsPrefixedCipherName = "TLS_" + supportedCipher.substring("SSL_".length)
                try {
                    engine.setEnabledCipherSuites(Array(tlsPrefixedCipherName))
                    set.add(tlsPrefixedCipherName)
                } catch {
                    case ignored: IllegalArgumentException =>
                }
            }
        }
        set.toSet
    }

    private def defaultCiphers(engine: SSLEngine, supportedCiphers: Set[String]): Array[String] = {
        val c = DEFAULT_CIPHER_SUITES.filter(a => supportedCiphers.contains(a))
        if (c.isEmpty) {
            engine.getEnabledCipherSuites.filter(a => !a.startsWith("SSL_") && !a.contains("_RC4_"))
        } else c
    }

}
