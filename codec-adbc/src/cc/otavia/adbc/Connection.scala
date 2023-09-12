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

package cc.otavia.adbc

import cc.otavia.adbc.Connection.Connect
import cc.otavia.adbc.Statement.*
import cc.otavia.core.actor.{ChannelsActor, SocketChannelsActor}
import cc.otavia.core.channel.{Channel, ChannelAddress, ChannelState}
import cc.otavia.core.message.{Ask, Reply}
import cc.otavia.core.stack.StackState.*
import cc.otavia.core.stack.{AskStack, ChannelFuture, StackState}

import java.net.{ProtocolFamily, StandardProtocolFamily}
import java.util
import java.util.Properties

class Connection(override val family: ProtocolFamily = StandardProtocolFamily.INET)
    extends ChannelsActor[Connect | ExecuteUpdate | ExecuteQuery[?]] {

    private var channel: ChannelAddress = _
    private var driver: Driver          = _

    override protected def init(channel: Channel): Unit = {
        channel.pipeline.addLast(driver)
    }

    override protected def newChannel(): Channel = system.channelFactory.openSocketChannel(family)

    private def handleConnect(stack: AskStack[Connect]): Option[StackState] = {
        stack.stackState match
            case StackState.start =>
                val auth = stack.ask
                val driverFactory = auth.driver match
                    case Some(name) => DriverManager.getDriverFactory(name)
                    case None       => DriverManager.defaultDriver(auth.url)
                val options = driverFactory.parseOptions(auth.url, auth.info)
                driver = driverFactory.newDriver(options)

                channel = newChannelAndInit()
                val state = new ChannelFutureState()
                channel.connect(???, state.future)
                state.suspend()
            case state: ChannelFutureState =>
                ???
    }

    private def handleExecuteUpdate(stack: AskStack[ExecuteUpdate]): Option[StackState] = {
        ???
    }

    private def handleExecuteQuery(stack: AskStack[ExecuteQuery[?]]): Option[StackState] = {
        ???
    }

    private def handleExecuteQueries(stack: AskStack[ExecuteQueries[?]]): Option[StackState] = {
        ???
    }

    private def handleCursor(stack: AskStack[ExecuteCursor[?]]): Option[StackState] = {

        ???
    }

}

object Connection {

    case class ConnectResult(connectionId: Int)                                               extends Reply
    case class Connect(url: String, info: Map[String, String], driver: Option[String] = None) extends Ask[ConnectResult]
    object Connect {
        def apply(url: String, user: String, password: String): Connect = {
            val info = Map("user" -> user, "password" -> password)
            new Connect(url, info, None)
        }
    }

}
