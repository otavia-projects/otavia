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

/** The default [[AddressedEnvelope]] implementation.
 *  @tparam M
 *    the type of the wrapped message
 *  @tparam A
 *    the type of the address
 */
class DefaultAddressedEnvelope[M, A <: SocketAddress](
    final val message: M,
    final val recipient: A,
    final val sender: Option[A] = None
) extends AddressedEnvelope[M, A] {

    override def toString: String =
        if (sender.nonEmpty) s"${this.getClass.getSimpleName}(${sender.get} => $recipient, $message)"
        else s"${this.getClass.getSimpleName}(=> $recipient, $message)"

    override def content: M = message

}
