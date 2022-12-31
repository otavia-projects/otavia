/*
 * Copyright 2022 Yan Kun
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

package io.otavia.core.channel.socket

import java.io.File
import java.net.SocketAddress

@SerialVersionUID(-6934618000832236893L)
class DomainSocketAddress(private final val socketPath: String) extends SocketAddress {

    def this(file: File) = this(file.getPath)

    /** The path to the domain socket. */
    def path: String = socketPath

    override def toString: String = path

    override def equals(obj: Any): Boolean = if (this == obj) true
    else if (!obj.isInstanceOf[DomainSocketAddress]) false
    else
        obj.asInstanceOf[DomainSocketAddress].socketPath.equals(socketPath)

    override def hashCode(): Int = socketPath.hashCode

}
