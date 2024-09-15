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

package cc.otavia.core.reactor

import cc.otavia.buffer.pool.RecyclablePageBuffer
import cc.otavia.core.channel.message.ReadPlan
import cc.otavia.core.channel.{AbstractChannel, Channel, ChannelShutdownDirection, FileRegion}
import cc.otavia.core.reactor.Reactor.Command
import cc.otavia.core.system.ActorSystem
import cc.otavia.core.util.Nextable

import java.net.SocketAddress
import java.nio.file.attribute.FileAttribute
import java.nio.file.{OpenOption, Path}

/** [[Reactor]] is an io event generator for [[AbstractChannel]]. */
trait Reactor {

    final def open(
        channel: AbstractChannel,
        path: Path,
        options: Seq[OpenOption],
        attrs: Seq[FileAttribute[?]]
    ): Unit = {
        val command = Command.Open(channel, path, options, attrs)
        submit(command)
    }

    final def disconnect(channel: AbstractChannel): Unit = {
        val command = Command.Disconnect(channel)
        submit(command)
    }

    final def close(channel: AbstractChannel): Unit = {
        val command = Command.Close(channel)
        submit(command)
    }

    final def read(channel: AbstractChannel, plan: ReadPlan): Unit = {
        val command = Command.Read(channel, plan)
        submit(command)
    }

    final def flush(channel: AbstractChannel, payload: FileRegion | RecyclablePageBuffer): Unit = {
        val command = Command.Flush(channel, payload)
        submit(command)
    }

    def submit(command: Command): Unit

}

object Reactor {

    val DEFAULT_MAX_TASKS_PER_RUN: Int = ActorSystem.DEFAULT_MAX_TASKS_PER_RUN

    abstract sealed class Command extends Nextable {
        def channel: AbstractChannel
    }

    object Command {

        case class Register(channel: AbstractChannel) extends Command

        case class Deregister(channel: AbstractChannel) extends Command

        case class Bind(channel: AbstractChannel, local: SocketAddress) extends Command

        case class Connect(
            channel: AbstractChannel,
            remote: SocketAddress,
            local: Option[SocketAddress],
            fastOpen: Boolean
        ) extends Command

        case class Disconnect(channel: AbstractChannel) extends Command

        case class Open(channel: AbstractChannel, path: Path, options: Seq[OpenOption], attrs: Seq[FileAttribute[?]])
            extends Command

        case class Close(channel: AbstractChannel) extends Command

        case class Shutdown(channel: AbstractChannel, direction: ChannelShutdownDirection) extends Command

        case class Read(channel: AbstractChannel, plan: ReadPlan) extends Command

        case class Flush(channel: AbstractChannel, payload: FileRegion | RecyclablePageBuffer) extends Command

    }

}
