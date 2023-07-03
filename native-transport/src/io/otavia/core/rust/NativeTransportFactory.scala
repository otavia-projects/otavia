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

package io.otavia.core.rust

import io.otavia.core.channel.Channel
import io.otavia.core.channel.socket.SocketProtocolFamily
import io.otavia.core.reactor.{IoHandler, Reactor}
import io.otavia.core.system.ActorSystem
import io.otavia.core.transport.TransportFactory

import java.net.ProtocolFamily

class NativeTransportFactory extends TransportFactory {

    override def openServerSocketChannel(): Channel = ???

    override def openServerSocketChannel(family: ProtocolFamily): Channel = ???

    override def openSocketChannel(): Channel = ???

    override def openSocketChannel(family: ProtocolFamily): Channel = ???

    override def openDatagramChannel(): Channel = ???

    override def openDatagramChannel(family: SocketProtocolFamily): Channel = ???

    override def openFileChannel(): Channel = ???

    override def openReactor(system: ActorSystem): Reactor = ???

    override def openIoHandler(system: ActorSystem): IoHandler = ???

}
