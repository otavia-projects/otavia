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

package cc.otavia.core.transport.reactor.rust

import cc.otavia.core.channel.Channel
import cc.otavia.core.channel.message.ReadPlan
import cc.otavia.core.reactor.*
import cc.otavia.core.slf4a.Logger
import cc.otavia.core.system.ActorSystem

import java.net.SocketAddress
import java.nio.file.attribute.FileAttribute
import java.nio.file.{OpenOption, Path}
import scala.language.unsafeNulls

class NativeHandler(val maxEvents: Int, val strategy: SelectStrategy, sys: ActorSystem) extends IoHandler(sys) {

    def this(sys: ActorSystem) = this(0, DefaultSelectStrategyFactory.newSelectStrategy(), sys)

    val logger: Logger = Logger.getLogger(getClass, system) // TODO: null bug for system

    /** Rust `Box<Poll>` */
    private var nativePoll: Long = ???

    override def run(context: IoExecutionContext): Int = ???

    override def prepareToDestroy(): Unit = ???

    override def destroy(): Unit = ???

    override def register(channel: Channel): Unit = ???

    override def deregister(channel: Channel): Unit = ???

    override def bind(channel: Channel, local: SocketAddress): Unit = ???

    override def open(channel: Channel, path: Path, options: Seq[OpenOption], attrs: Seq[FileAttribute[?]]): Unit = ???

    override def connect(
        channel: Channel,
        remote: SocketAddress,
        local: Option[SocketAddress],
        fastOpen: Boolean
    ): Unit = ???

    override def read(channel: Channel, plan: ReadPlan): Unit = ???

    override def wakeup(inEventLoop: Boolean): Unit = ???

    override def isCompatible(handleType: Class[? <: Channel]): Boolean = ???

}

object NativeHandler {}
