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

class JdkSslClientContext(
    sslContextProvider: Option[Provider],
    trustManagerFactory: Option[TrustManagerFactory],
    keyManagerFactory: Option[KeyManagerFactory],
    ciphers: Option[Seq[String]],
    cipherFilter: CipherSuiteFilter,
    apn: Option[ApplicationProtocolConfig],
    protocols: Array[String],
    sessionCacheSize: Long,
    sessionTimeout: Long,
    keyStoreType: String
) extends JdkSslContext(
      JdkSslClientContext.newSSLContext(
        sslContextProvider,
        trustManagerFactory,
        keyManagerFactory,
        sessionCacheSize,
        sessionTimeout,
        keyStoreType
      ),
      true,
      ciphers,
      cipherFilter,
      JdkBaseApplicationProtocolNegotiator.fromApplicationProtocolConfig(apn, false),
      ClientAuth.NONE,
      protocols,
      false
    ) {}

object JdkSslClientContext {
    def newSSLContext(
        sslContextProvider: Option[Provider],
        trustManagerFactory: Option[TrustManagerFactory],
        keyManagerFactory: Option[KeyManagerFactory],
        sessionCacheSize: Long,
        sessionTimeout: Long,
        keyStoreType: String
    ): SSLContext = try {
        val ctx = sslContextProvider match
            case Some(provider) => SSLContext.getInstance(JdkSslContext.PROTOCOL, provider)
            case None           => SSLContext.getInstance(JdkSslContext.PROTOCOL)

        ctx.init(
          keyManagerFactory.map(_.getKeyManagers).orNull,
          trustManagerFactory.map(_.getTrustManagers).orNull,
          null
        )

        val sessCtx = ctx.getClientSessionContext
        if (sessionCacheSize > 0) sessCtx.setSessionCacheSize(math.min(sessionCacheSize, Int.MaxValue.toLong).toInt)
        if (sessionTimeout > 0) sessCtx.setSessionTimeout(math.min(sessionTimeout, Int.MaxValue.toLong).toInt)

        ctx
    } catch {
        case e: SSLException => throw e
        case e: Exception    => throw new SSLException("failed to initialize the client-side SSL context", e)
    }
}
