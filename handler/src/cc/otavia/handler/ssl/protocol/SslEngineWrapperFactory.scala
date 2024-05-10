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

/** Abstract factory pattern for wrapping an [[SSLEngine]] object. This is useful for NPN/APLN JDK support. */
trait SslEngineWrapperFactory {

    /** Abstract factory pattern for wrapping an [[SSLEngine]] object. This is useful for NPN/APLN support.
     *
     *  @param engine
     *    The engine to wrap.
     *  @param applicationNegotiator
     *    The application level protocol negotiator
     *  @param isServer
     *    <ul> <li>`true` if the engine is for server side of connections</li> <li>`false` if the engine is for client
     *    side of connections</li> </ul>
     *  @return
     *    The resulting wrapped engine. This may just be engine.
     */
    def wrapSslEngine(
        engine: SSLEngine,
        applicationNegotiator: JdkApplicationProtocolNegotiator,
        isServer: Boolean
    ): SSLEngine
}
