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

package cc.otavia.sql

import cc.otavia.core.actor.SocketChannelsActor.{Connect, ConnectReply}
import cc.otavia.core.actor.{ChannelsActor, SocketChannelsActor}
import cc.otavia.core.channel.{Channel, ChannelAddress, ChannelInitializer, ChannelState}
import cc.otavia.core.message.{Ask, ExceptionMessage, Reply}
import cc.otavia.core.stack.StackState.*
import cc.otavia.core.stack.helper.*
import cc.otavia.core.stack.{AskStack, ChannelFuture, StackState}
import cc.otavia.sql.Authentication
import cc.otavia.sql.Statement.*

import java.net.{ProtocolFamily, StandardProtocolFamily}

class Connection(override val family: ProtocolFamily = StandardProtocolFamily.INET)
    extends SocketChannelsActor[Connection.MSG] {

    private var channel: ChannelAddress = _
    private var driver: Driver          = _

    override def handler: Option[ChannelInitializer[? <: Channel]] = Some(
      new ChannelInitializer[Channel] {
          override protected def initChannel(ch: Channel): Unit = ch.pipeline.addLast(driver)
      }
    )

    override def continueAsk(stack: AskStack[Connection.MSG]): Option[StackState] = {
        stack match
            case stack: AskStack[?] if stack.ask.isInstanceOf[Authentication] =>
                handleAuthentication(stack.asInstanceOf[AskStack[Authentication]])
            case stack: AskStack[?] if stack.ask.isInstanceOf[ExecuteUpdate] =>
                handleExecuteUpdate(stack.asInstanceOf[AskStack[ExecuteUpdate]])
            case stack: AskStack[?] if stack.ask.isInstanceOf[ExecuteQuery[?]] =>
                handleExecuteQuery(stack.asInstanceOf[AskStack[ExecuteQuery[?]]])
    }

    private def handleAuthentication(stack: AskStack[Authentication]): Option[StackState] = {
        stack.state match
            case StackState.start => // waiting for socket connected
                val auth = stack.ask
                val driverFactory = auth.driver match
                    case Some(name) => DriverManager.getDriverFactory(name)
                    case None       => DriverManager.defaultDriver(auth.url)
                val options = driverFactory.parseOptions(auth.url, auth.info)
                driver = driverFactory.newDriver(options)
                connect(options.socketAddress, None)
            case state: ChannelFutureState => // waiting to authentication
                channel = state.future.channel
                val authState = ChannelFutureState()
                channel.ask(stack.ask, authState.future)
                authState.suspend()
            case state: ChannelFutureState => // return authenticate result
                if (state.future.isSuccess) stack.`return`(ConnectReply(channel.id))
                else stack.`throw`(ExceptionMessage(state.future.causeUnsafe))
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
    type MSG = Authentication | ExecuteUpdate | ExecuteQuery[? <: Row]
}
