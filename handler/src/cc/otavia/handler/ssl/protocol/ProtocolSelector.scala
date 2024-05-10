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

package cc.otavia.handler.ssl.protocol

import cc.otavia.handler.ssl.{ApplicationProtocolConfig, JdkSslEngine}

import javax.net.ssl.SSLHandshakeException
import scala.language.unsafeNulls

/** Interface to define the role of an application protocol selector in the SSL handshake process. Either
 *  [[unsupported]]() OR [[select]](List) will be called for each SSL handshake.
 */
trait ProtocolSelector {

    /** Callback invoked to let the application know that the peer does not support this [[ApplicationProtocolConfig]]
     */
    def unsupported(): Unit

    /** Callback invoked to select the application level protocol from the protocols provided.
     *  @param protocols
     *    the protocols sent by the protocol advertiser
     *  @return
     *    the protocol selected by this [[ProtocolSelector]]. A null value will indicate the no protocols were selected
     *    but the handshake should not fail. The decision to fail the handshake is left to the other end negotiating the
     *    SSL handshake.
     *  @throws Exception
     *    If the protocols provide warrant failing the SSL handshake with a fatal alert.
     */
    @throws[Exception]
    def select(protocols: List[String]): String

}

object ProtocolSelector {

    class NoFailProtocolSelector(
        private val engineWrapper: JdkSslEngine,
        private val supportedProtocols: Set[String]
    ) extends ProtocolSelector {

        override def unsupported(): Unit = engineWrapper.setNegotiatedApplicationProtocol(null)

        override def select(protocols: List[String]): String = {
            protocols.find(p => supportedProtocols.contains(p)) match
                case Some(p) =>
                    engineWrapper.setNegotiatedApplicationProtocol(p)
                    p
                case None =>
                    noSelectMatchFound()
        }

        def noSelectMatchFound(): String = {
            engineWrapper.setNegotiatedApplicationProtocol(null)
            null
        }

    }

    class FailProtocolSelector(engineWrapper: JdkSslEngine, supportedProtocols: Set[String])
        extends NoFailProtocolSelector(engineWrapper, supportedProtocols) {
        override def noSelectMatchFound(): String =
            throw new SSLHandshakeException("Selected protocol is not supported")
    }

}
