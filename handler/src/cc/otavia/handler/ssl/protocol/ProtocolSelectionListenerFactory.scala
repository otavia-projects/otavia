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

import javax.net.ssl.{SSLEngine, SSLHandshakeException}
import scala.language.unsafeNulls

/** Factory interface for [[ProtocolSelectionListener]] objects. */
trait ProtocolSelectionListenerFactory {

    /** Generate a new instance of [[ProtocolSelectionListener]].
     *  @param engine
     *    The [[SSLEngine]] that the returned [[ProtocolSelectionListener]] will be used to create an instance for.
     *  @param supportedProtocols
     *    The protocols that are supported in preference order.
     *  @return
     *    A new instance of [[ProtocolSelectionListener]].
     */
    def newListener(engine: SSLEngine, supportedProtocols: List[String]): ProtocolSelectionListener

}

object ProtocolSelectionListenerFactory {

    private class NoFailProtocolSelectionListener(val engineWrapper: JdkSslEngine, supportedProtocols: List[String])
        extends ProtocolSelectionListener {

        override def unsupported(): Unit = engineWrapper.setNegotiatedApplicationProtocol(null)

        override def select(protocol: String): Unit = if (supportedProtocols.contains(protocol))
            engineWrapper.setNegotiatedApplicationProtocol(protocol)
        else noSelectedMatchFound(protocol)

        @throws[Exception]
        protected def noSelectedMatchFound(protocol: String): Unit = {} // Will never be called.

    }

    private class FailProtocolSelectionListener(engineWrapper: JdkSslEngine, supportedProtocols: List[String])
        extends NoFailProtocolSelectionListener(engineWrapper, supportedProtocols) {
        override protected def noSelectedMatchFound(protocol: String): Unit =
            throw new SSLHandshakeException("No compatible protocols found")
    }

    val FAIL_SELECTION_LISTENER_FACTORY: ProtocolSelectionListenerFactory = new ProtocolSelectionListenerFactory {
        override def newListener(engine: SSLEngine, supportedProtocols: List[String]): ProtocolSelectionListener =
            new FailProtocolSelectionListener(engine.asInstanceOf[JdkSslEngine], supportedProtocols)
    }

    val NO_FAIL_SELECTION_LISTENER_FACTORY: ProtocolSelectionListenerFactory = new ProtocolSelectionListenerFactory {
        override def newListener(engine: SSLEngine, supportedProtocols: List[String]): ProtocolSelectionListener =
            new NoFailProtocolSelectionListener(engine.asInstanceOf[JdkSslEngine], supportedProtocols)
    }

}
