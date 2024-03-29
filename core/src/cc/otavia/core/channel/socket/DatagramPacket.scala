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

package cc.otavia.core.channel.socket

import cc.otavia.buffer.Buffer
import cc.otavia.core.channel.udp.BufferAddressedEnvelope

import java.net.SocketAddress

/** The message container that is used for [[DatagramChannel]] to communicate with the remote peer. */
class DatagramPacket(message: Buffer, recipient: SocketAddress, sender: Option[SocketAddress] = None)
    extends BufferAddressedEnvelope[SocketAddress, DatagramPacket](message, recipient, sender) {

    override def replace(content: Buffer): DatagramPacket = new DatagramPacket(content, recipient, sender)

    def touch(hint: Any): DatagramPacket = {
//        super.touch(hint)
        this
    }

}
