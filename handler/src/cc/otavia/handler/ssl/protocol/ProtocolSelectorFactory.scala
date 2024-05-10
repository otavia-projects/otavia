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

import cc.otavia.handler.ssl.JdkSslEngine
import cc.otavia.handler.ssl.protocol.ProtocolSelector.{FailProtocolSelector, NoFailProtocolSelector}

import javax.net.ssl.SSLEngine

/** Factory interface for [[ProtocolSelector]] objects. */
trait ProtocolSelectorFactory {

    /** Generate a new instance of [[ProtocolSelector]].
     *  @param engine
     *    The [[SSLEngine]] that the returned [[ProtocolSelector]] will be used to create an instance for.
     *  @param supportedProtocols
     *    The protocols that are supported.
     *  @return
     *    A new instance of [[ProtocolSelector]].
     */
    def newSelector(engine: SSLEngine, supportedProtocols: Set[String]): ProtocolSelector
}

object ProtocolSelectorFactory {

    val FAIL_SELECTOR_FACTORY: ProtocolSelectorFactory = new ProtocolSelectorFactory {
        override def newSelector(engine: SSLEngine, supportedProtocols: Set[String]): ProtocolSelector =
            new FailProtocolSelector(engine.asInstanceOf[JdkSslEngine], supportedProtocols)
    }

    val NO_FAIL_SELECTOR_FACTORY: ProtocolSelectorFactory = new ProtocolSelectorFactory {
        override def newSelector(engine: SSLEngine, supportedProtocols: Set[String]): ProtocolSelector =
            new NoFailProtocolSelector(engine.asInstanceOf[JdkSslEngine], supportedProtocols)
    }


}
