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

import cc.otavia.handler.ssl.ApplicationProtocolConfig.*
import cc.otavia.handler.ssl.protocol.ApplicationProtocolNegotiator

import javax.net.ssl.SSLEngine

/** Provides an [[SSLEngine]] agnostic way to configure a [[ApplicationProtocolNegotiator]]. */
class ApplicationProtocolConfig private (
    val supportedProtocols: List[String],
    val protocol: Protocol,
    val selectorFailureBehavior: SelectorFailureBehavior,
    val selectedListenerFailureBehavior: SelectedListenerFailureBehavior
) {}

object ApplicationProtocolConfig {

    val DISABLED: ApplicationProtocolConfig = new ApplicationProtocolConfig(
      List.empty,
      Protocol.NONE,
      SelectorFailureBehavior.CHOOSE_MY_LAST_PROTOCOL,
      SelectedListenerFailureBehavior.ACCEPT
    )

    /** Create a new [[ApplicationProtocolConfig]] instance.
     *  @param supportedProtocols
     *    The order of iteration determines the preference of support for protocols.
     *  @param protocol
     *    The application protocol functionality to use.
     *  @param selectorBehavior
     *    How the peer selecting the protocol should behave.
     *  @param selectedBehavior
     *    How the peer being notified of the selected protocol should behave.
     */
    def apply(
        supportedProtocols: List[String],
        protocol: Protocol,
        selectorBehavior: SelectorFailureBehavior,
        selectedBehavior: SelectedListenerFailureBehavior
    ): ApplicationProtocolConfig = {
        if (protocol == Protocol.NONE)
            throw new IllegalArgumentException(s"protocol (${Protocol.NONE}) must not be ${Protocol.NONE}")
        if (supportedProtocols.isEmpty)
            throw new IllegalArgumentException(s"Param 'supportedProtocols' must not be empty.")
        new ApplicationProtocolConfig(supportedProtocols, protocol, selectorBehavior, selectedBehavior)
    }

    /** Defines which application level protocol negotiation to use. */
    enum Protocol {

        case NONE
        case NPN
        case ALPN
        case NPN_AND_ALPN

    }

    /** Defines the most common behaviors for the peer that selects the application protocol. */
    enum SelectorFailureBehavior {

        /** If the peer who selects the application protocol doesn't find a match this will result in the failing the
         *  handshake with a fatal alert. <p> For example in the case of ALPN this will result in a <a
         *  herf="https://tools.ietf.org/html/rfc7301#section-3.2">no_application_protocol(120)</a> alert.
         */
        case FATAL_ALERT

        /** If the peer who selects the application protocol doesn't find a match it will pretend no to support the TLS
         *  extension by not advertising support for the TLS extension in the handshake. This is used in cases where a
         *  "best effort" is desired to talk even if there is no matching protocol.
         */
        case NO_ADVERTISE

        /** If the peer who selects the application protocol doesn't find a match it will just select the last protocol
         *  it advertised support for. This is used in cases where a "best effort" is desired to talk even if there is
         *  no matching protocol, and the assumption is the "most general" fallback protocol is typically listed last.
         *  <p> This may be <a href="https://tools.ietf.org/html/rfc7301#section-3.2">illegal for some RFCs</a> but was
         *  observed behavior by some SSL implementations, and is supported for flexibility/compatibility.
         */
        case CHOOSE_MY_LAST_PROTOCOL

    }

    /** Defines the most common behaviors for the peer which is notified of the selected protocol. */
    enum SelectedListenerFailureBehavior {

        /** If the peer who is notified what protocol was selected determines the selection was not matched, or the peer
         *  didn't advertise support for the TLS extension then the handshake will continue and the application protocol
         *  is assumed to be accepted.
         */
        case ACCEPT

        /** If the peer who is notified what protocol was selected determines the selection was not matched, or the peer
         *  didn't advertise support for the TLS extension then the handshake will be failed with a fatal alert.
         */
        case FATAL_ALERT

        /** If the peer who is notified what protocol was selected determines the selection was not matched, or the peer
         *  didn't advertise support for the TLS extension then the handshake will continue assuming the last protocol
         *  supported by this peer is used. This is used in cases where a "best effort" is desired to talk even if there
         *  is no matching protocol, and the assumption is the "most general" fallback protocol is typically listed
         *  last.
         */
        case CHOOSE_MY_LAST_PROTOCOL

    }

}
