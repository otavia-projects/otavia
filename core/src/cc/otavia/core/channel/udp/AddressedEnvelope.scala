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

package cc.otavia.core.channel.udp

import java.net.SocketAddress

/** A message that wraps another message with a sender address and a recipient address.
 *
 *  @tparam M
 *    the type of the wrapped message
 *  @tparam A
 *    the type of the address
 */
trait AddressedEnvelope[M, A <: SocketAddress] {

    /** Returns the message wrapped by this envelope message. */
    def content: M

    /** Returns the address of the sender of this message. */
    def sender: Option[A]

    /** Returns the address of the recipient of this message. */
    def recipient: A

}
