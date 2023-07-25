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

package io.otavia.core.reactor

import io.netty5.util.internal.SystemPropertyUtil
import io.otavia.core.channel.Channel
import io.otavia.core.channel.message.ReadPlan
import io.otavia.core.reactor.Reactor.Command
import io.otavia.core.system.ActorSystem
import io.otavia.core.util.Nextable

import java.net.SocketAddress
import java.nio.file.attribute.FileAttribute
import java.nio.file.{OpenOption, Path}

/** [[Reactor]] is an io event generator for [[Channel]]. */
trait Reactor {

    /** API for [[io.otavia.core.actor.ChannelsActor]] to register [[Channel]] to [[Reactor]].
     *  @param channel
     *    [[Channel]] to listen io event.
     */
    final def register(channel: Channel): Unit = {
        val command = Command.Register(channel)
        submit(command)
    }

    /** API for [[io.otavia.core.actor.ChannelsActor]] to deregister [[Channel]] to [[Reactor]].
     *  @param channel
     *    [[Channel]] to cancel listen io event.
     */
    final def deregister(channel: Channel): Unit = {
        val command = Command.Deregister(channel)
        submit(command)
    }

    final def bind(channel: Channel, local: SocketAddress): Unit = {
        val command = Command.Bind(channel, local)
        submit(command)
    }

    final def open(channel: Channel, path: Path, options: Seq[OpenOption], attrs: Seq[FileAttribute[?]]): Unit = {
        val command = Command.Open(channel, path, options, attrs)
        submit(command)
    }

    final def connect(channel: Channel, remote: SocketAddress, local: Option[SocketAddress], fast: Boolean): Unit = {
        val command = Command.Connect(channel, remote, local, fast)
        submit(command)
    }

    final def disconnect(channel: Channel): Unit = {
        val command = Command.Disconnect(channel)
        submit(command)
    }

    final def close(channel: Channel): Unit = {
        val command = Command.Close(channel)
        submit(command)
    }

    final def read(channel: Channel, plan: ReadPlan): Unit = {
        val command = Command.Read(channel, plan)
        submit(command)
    }

    def submit(command: Command): Unit

}

object Reactor {

    val DEFAULT_MAX_TASKS_PER_RUN: Int = ActorSystem.DEFAULT_MAX_TASKS_PER_RUN

    abstract sealed class Command extends Nextable {
        def channel: Channel
    }

    object Command {

        case class Register(channel: Channel) extends Command

        case class Deregister(channel: Channel) extends Command

        case class Bind(channel: Channel, local: SocketAddress) extends Command

        case class Connect(channel: Channel, remote: SocketAddress, local: Option[SocketAddress], fastOpen: Boolean)
            extends Command

        case class Disconnect(channel: Channel) extends Command

        case class Open(channel: Channel, path: Path, options: Seq[OpenOption], attrs: Seq[FileAttribute[?]])
            extends Command

        case class Close(channel: Channel) extends Command

        case class Read(channel: Channel, plan: ReadPlan) extends Command

    }

}
