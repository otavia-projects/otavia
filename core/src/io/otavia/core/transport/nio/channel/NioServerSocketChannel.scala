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

import io.otavia.core.channel.*
import io.otavia.core.channel.estimator.ServerChannelWriteHandleFactory
import io.otavia.core.channel.internal.{ReadSink, WriteSink}
import io.otavia.core.channel.message.{ReadPlan, ReadPlanFactory}
import io.otavia.core.message.ReactorEvent
import io.otavia.core.system.ActorSystem
import io.otavia.core.transport.nio.channel.NioServerSocketChannel.NioServerSocketReadPlan

import java.net.SocketAddress

class NioServerSocketChannel(system: ActorSystem) extends AbstractServerChannel(system) {

    setReadPlanFactory((channel: Channel) => new NioServerSocketReadPlan())

    override def unsafeChannel: NioUnsafeServerSocketChannel =
        super.unsafeChannel.asInstanceOf[NioUnsafeServerSocketChannel]

    override protected def setExtendedOption[T](option: ChannelOption[T], value: T): Unit = {
        if (option == ChannelOption.SO_BACKLOG) unsafeChannel.setBacklog(value.asInstanceOf[Int])
        else {
            val socketOption = NioChannelOption.toSocketOption(option)
            if (socketOption != null) {
                // NioChannelOption.setOption(javaChannel, socketOption, value)
            } else
                super.setExtendedOption(option, value)
        }
    }

}

object NioServerSocketChannel {

    class NioServerSocketReadPlan extends ReadPlan {

        override def estimatedNextSize: Int = 0

        override def lastRead(attemptedBytesRead: Int, actualBytesRead: Int, numMessagesRead: Int): Boolean = false

        override def readComplete(): Unit = {}

    }

}
