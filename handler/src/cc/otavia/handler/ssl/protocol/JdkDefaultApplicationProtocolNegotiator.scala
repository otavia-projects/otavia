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

import javax.net.ssl.SSLEngine

object JdkDefaultApplicationProtocolNegotiator extends JdkApplicationProtocolNegotiator {

    private val DEFAULT_SSL_ENGINE_WRAPPER_FACTORY = new SslEngineWrapperFactory {
        override def wrapSslEngine(
            engine: SSLEngine,
            applicationNegotiator: JdkApplicationProtocolNegotiator,
            isServer: Boolean
        ): SSLEngine = engine
    }

    override def wrapperFactory: SslEngineWrapperFactory = DEFAULT_SSL_ENGINE_WRAPPER_FACTORY

    override def protocolSelectorFactory: ProtocolSelectorFactory =
        throw new UnsupportedOperationException("Application protocol negotiation unsupported")

    override def protocolListenerFactory: ProtocolSelectionListenerFactory =
        throw new UnsupportedOperationException("Application protocol negotiation unsupported")

    override def protocols: List[String] = List.empty

}
