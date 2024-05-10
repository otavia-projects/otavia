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

/** A listener to be notified by which protocol was select by its peer. Either the [[unsupported]]() OR the
 *  [[selected]](String) method will be called for each SSL handshake.
 */
trait ProtocolSelectionListener {

    /** Callback invoked to let the application know that the peer does not support this */
    def unsupported(): Unit

    /** Callback invoked to let this application know the protocol chosen by the peer.
     *
     *  @param protocol
     *    the protocol selected by the peer. May be null or empty as supported by the application negotiation protocol.
     *  @throws Exception
     *    This may be thrown if the selected protocol is not acceptable and the desired behavior is to fail the
     *    handshake with a fatal alert.
     */
    @throws[Exception]
    def select(protocol: String): Unit

}
