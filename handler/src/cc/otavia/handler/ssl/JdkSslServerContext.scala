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

import cc.otavia.handler.ssl.protocol.JdkBaseApplicationProtocolNegotiator

import java.security.*
import java.security.cert.X509Certificate
import javax.net.ssl.*
import scala.language.unsafeNulls

class JdkSslServerContext(
    provider: Option[Provider],
    trustManagerFactory: Option[TrustManagerFactory],
    keyManagerFactory: KeyManagerFactory,
    ciphers: Option[Seq[String]],
    cipherFilter: CipherSuiteFilter,
    apn: Option[ApplicationProtocolConfig],
    sessionCacheSize: Long,
    sessionTimeout: Long,
    clientAuth: ClientAuth,
    protocols: Array[String],
    startTls: Boolean,
    keyStore: String
) extends JdkSslContext(
      JdkSslServerContext.newSSLContext(
        provider,
        trustManagerFactory,
        keyManagerFactory,
        sessionCacheSize,
        sessionTimeout,
        keyStore
      ),
      false,
      ciphers,
      cipherFilter,
      JdkBaseApplicationProtocolNegotiator.fromApplicationProtocolConfig(apn, true),
      clientAuth,
      protocols,
      startTls
    ) {}

object JdkSslServerContext {
    def newSSLContext(
        provider: Option[Provider],
        trustManagerFactory: Option[TrustManagerFactory],
        keyManagerFactory: KeyManagerFactory,
        sessionCacheSize: Long,
        sessionTimeout: Long,
        keyStore: String
    ): SSLContext = try {
        val ctx = provider match
            case Some(value) => SSLContext.getInstance(JdkSslContext.PROTOCOL, value)
            case None        => SSLContext.getInstance(JdkSslContext.PROTOCOL)

        val trustFactory = trustManagerFactory match
            case Some(value) => value
            case None =>
                val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
                factory.init(null.asInstanceOf[KeyStore])
                factory

        ctx.init(keyManagerFactory.getKeyManagers, trustFactory.getTrustManagers, null)

        val sessCtx = ctx.getServerSessionContext
        if (sessionCacheSize > 0) sessCtx.setSessionCacheSize(math.min(sessionCacheSize, Int.MaxValue.toLong).toInt)
        if (sessionTimeout > 0) sessCtx.setSessionTimeout(math.min(sessionTimeout, Int.MaxValue.toLong).toInt)

        ctx
    } catch {
        case e: SSLException => throw e
        case e: Exception    => throw new SSLException("failed to initialize the server-side SSL context", e)
    }
}
