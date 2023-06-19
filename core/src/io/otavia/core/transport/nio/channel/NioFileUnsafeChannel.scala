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

import io.otavia.core.channel.{AbstractUnsafeChannel, Channel, ChannelShutdownDirection}

import java.nio.channels.FileChannel
import java.nio.file.attribute.FileAttribute
import java.nio.file.{OpenOption, Path}
import scala.jdk.CollectionConverters.*
import scala.language.unsafeNulls

class NioFileUnsafeChannel(channel: Channel) extends AbstractUnsafeChannel(channel) {

    private var ch: FileChannel = _

    def doOpen(path: Path, options: Seq[OpenOption], attrs: Seq[FileAttribute[?]]): Unit = this.synchronized {
        ch = FileChannel.open(path, options.toSet.asJava, attrs: _*)
    }

    private def javaChannel: FileChannel = ch

    override protected def doReadNow(): Boolean = ???

    override protected def unsafeBind(): Unit = ???

    override protected def unsafeDisconnect(): Unit = ???

    override protected def unsafeClose(): Unit = ???

    override protected def unsafeShutdown(direction: ChannelShutdownDirection): Unit = ???

    override def unsafeRead(): Unit = ???
}
