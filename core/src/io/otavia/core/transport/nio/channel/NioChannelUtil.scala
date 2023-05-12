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

package io.otavia.core.transport.nio.channel

import io.otavia.core.channel.socket.SocketProtocolFamily

import java.lang.invoke.MethodHandle
import java.net.{ProtocolFamily, SocketAddress, StandardProtocolFamily}
import scala.util.Properties

object NioChannelUtil {

    private var OF_METHOD_HANDLE: MethodHandle       = _
    private var GET_PATH_METHOD_HANDLE: MethodHandle = _

    private def init(): Unit = {
        Properties.ScalaCompilerVersion
    }

    init()

    def isDomainSocket(family: ProtocolFamily): Boolean = family match
        case family: SocketProtocolFamily   => family == SocketProtocolFamily.UNIX
        case family: StandardProtocolFamily => "UNIX".equals(family.name())
        case _                              => false

    def toDomainSocketAddress(address: SocketAddress): SocketAddress = ???

    def toUnixDomainSocketAddress(address: SocketAddress): SocketAddress = ???

    def toJdkFamily(family: ProtocolFamily): ProtocolFamily = family match
        case family: SocketProtocolFamily   => family.toJdkFamily
        case family: StandardProtocolFamily => family
        case _                              => family

}
