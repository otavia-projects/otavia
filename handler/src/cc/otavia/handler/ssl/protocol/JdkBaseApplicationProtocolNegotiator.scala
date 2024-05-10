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

import cc.otavia.handler.ssl.ApplicationProtocolConfig
import cc.otavia.handler.ssl.ApplicationProtocolConfig.{Protocol, SelectedListenerFailureBehavior, SelectorFailureBehavior}
import cc.otavia.handler.ssl.protocol.ProtocolSelectionListenerFactory.*
import cc.otavia.handler.ssl.protocol.ProtocolSelectorFactory.*

/** Common base class for [[JdkApplicationProtocolNegotiator]] classes to inherit from. */
class JdkBaseApplicationProtocolNegotiator(
    override val wrapperFactory: SslEngineWrapperFactory,
    override val protocolSelectorFactory: ProtocolSelectorFactory,
    override val protocolListenerFactory: ProtocolSelectionListenerFactory,
    override val protocols: List[String]
) extends JdkApplicationProtocolNegotiator

object JdkBaseApplicationProtocolNegotiator {

    val ALPN_WRAPPER = new AlpnWrapper()

    def fromApplicationProtocolConfig(
        config: Option[ApplicationProtocolConfig],
        isServer: Boolean
    ): JdkApplicationProtocolNegotiator = {
        config match
            case Some(c) =>
                c.protocol match
                    case Protocol.ALPN =>
                        if (isServer) {
                            c.selectorFailureBehavior match
                                case SelectorFailureBehavior.FATAL_ALERT =>
                                    new JdkBaseApplicationProtocolNegotiator(
                                      ALPN_WRAPPER,
                                      FAIL_SELECTOR_FACTORY,
                                      FAIL_SELECTION_LISTENER_FACTORY,
                                      c.supportedProtocols
                                    )
                                case SelectorFailureBehavior.NO_ADVERTISE =>
                                    new JdkBaseApplicationProtocolNegotiator(
                                      ALPN_WRAPPER,
                                      NO_FAIL_SELECTOR_FACTORY,
                                      NO_FAIL_SELECTION_LISTENER_FACTORY,
                                      c.supportedProtocols
                                    )
                                case _ =>
                                    val msg = new StringBuilder("JDK provider does not support ")
                                        .append(c.selectorFailureBehavior)
                                        .append(" failure behavior")
                                        .toString()
                                    throw new UnsupportedOperationException(msg)
                        } else {
                            c.selectedListenerFailureBehavior match
                                case SelectedListenerFailureBehavior.ACCEPT =>
                                    new JdkBaseApplicationProtocolNegotiator(
                                      ALPN_WRAPPER,
                                      NO_FAIL_SELECTOR_FACTORY,
                                      NO_FAIL_SELECTION_LISTENER_FACTORY,
                                      c.supportedProtocols
                                    )
                                case SelectedListenerFailureBehavior.FATAL_ALERT =>
                                    new JdkBaseApplicationProtocolNegotiator(
                                      ALPN_WRAPPER,
                                      FAIL_SELECTOR_FACTORY,
                                      FAIL_SELECTION_LISTENER_FACTORY,
                                      c.supportedProtocols
                                    )
                                case _ =>
                                    val msg = new StringBuilder("JDK provider does not support ")
                                        .append(c.selectedListenerFailureBehavior)
                                        .append(" failure behavior")
                                        .toString()
                                    throw new UnsupportedOperationException(msg)
                        }
                    case Protocol.NPN =>
                        val msg = "Otavia JDK provider does not support NPN "
                        throw new UnsupportedOperationException(msg)
                    case Protocol.NPN_AND_ALPN =>
                        val msg = new StringBuilder("JDK provider does not support ")
                            .append(c.protocol)
                            .append(" protocol")
                            .toString()
                        throw new UnsupportedOperationException(msg)
                    case Protocol.NONE => JdkDefaultApplicationProtocolNegotiator
            case None => JdkDefaultApplicationProtocolNegotiator
    }

}
