/*
 * Copyright 2022 Yan Kun <yan_kun_1992@foxmail.com>
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

package cc.otavia.core.channel

import cc.otavia.core.channel.socket.SocketProtocolFamily
import cc.otavia.core.transport.TransportFactory
import cc.otavia.core.channel.socket.SocketProtocolFamily
import cc.otavia.core.transport.TransportFactory

import java.net.ProtocolFamily

class ChannelFactory(val transportFactory: TransportFactory) {

    def openServerSocketChannel(): Channel = transportFactory.createServerSocketChannel()

    def openServerSocketChannel(family: ProtocolFamily): Channel = transportFactory.createServerSocketChannel(family)

    def openSocketChannel(): Channel = transportFactory.createSocketChannel()

    def openSocketChannel(family: ProtocolFamily): Channel = transportFactory.createSocketChannel(family)

    def openDatagramChannel(): Channel = transportFactory.createDatagramChannel()

    def openDatagramChannel(family: SocketProtocolFamily): Channel = transportFactory.createDatagramChannel(family)

    def openFileChannel(): Channel = transportFactory.createFileChannel()

}
