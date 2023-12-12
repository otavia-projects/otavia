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

import cc.otavia.core.actor.ChannelsActor.ChannelEstablished
import cc.otavia.core.actor.SocketChannelsActor.Connect
import cc.otavia.core.actor.{ChannelsActor, SocketChannelsActor}
import cc.otavia.core.channel.{Channel, ChannelAddress, ChannelInitializer, ChannelState}
import cc.otavia.core.message.*
import cc.otavia.core.stack.StackState.*
import cc.otavia.core.stack.helper.*
import cc.otavia.core.stack.{AskStack, ChannelFuture, NoticeStack, StackState}
import cc.otavia.sql.Authentication
import cc.otavia.sql.Statement.*

import java.net.{ProtocolFamily, StandardProtocolFamily}
import scala.language.unsafeNulls

class Connection(
    url: String,
    info: Map[String, String],
    driverName: Option[String] = None,
    override val family: ProtocolFamily = StandardProtocolFamily.INET
) extends SocketChannelsActor[Connection.MSG] {

    private var channel: ChannelAddress        = _
    private var driver: Driver                 = _
    private var connectOptions: ConnectOptions = _

    private var authenticated: Boolean                           = false
    private var authenticationException: AuthenticationException = _

    def this() = this(null, null)

    def this(url: String, user: String, password: String) = this(url, Map("user" -> user, "password" -> password))

    override protected def initChannel(channel: Channel): Unit = {
        driver.setChannelOptions(channel)
        channel.pipeline.addLast(driver)
    }

    override protected def isBarrierCall(call: Call): Boolean = call.isInstanceOf[Authentication]

    override protected def afterMount(): Unit = if (url != null) this.noticeSelfHead(Authentication(url, info))

    override protected def resumeAsk(stack: AskStack[Connection.MSG]): Option[StackState] = {
        stack match
            case stack: AskStack[?] if stack.ask.isInstanceOf[Authentication] =>
                handleAuthentication(stack.asInstanceOf[AskStack[Authentication]])
            case stack: AskStack[?] if stack.ask.isInstanceOf[ExecuteUpdate] =>
                handleExecuteUpdate(stack.asInstanceOf[AskStack[ExecuteUpdate]])
            case stack: AskStack[?] if stack.ask.isInstanceOf[ExecuteQuery[?]] =>
                handleExecuteQuery(stack.asInstanceOf[AskStack[ExecuteQuery[Row]]])
            case stack: AskStack[?] if stack.ask.isInstanceOf[ExecuteQueries[?]] =>
                handleExecuteQueries(stack.asInstanceOf[AskStack[ExecuteQueries[Row]]])
    }

    override protected def resumeNotice(stack: NoticeStack[Connection.MSG & Notice]): Option[StackState] =
        stack.state match
            case StackState.start =>
                val auth = stack.notice.asInstanceOf[Authentication]
                createDriver(auth.driver, auth.url, auth.info)
                connect(connectOptions.socketAddress, None)
            case state: ChannelFutureState if state.id == 0 => // waiting to authentication
                channel = state.future.channel
                val authState = ChannelFutureState(1)
                channel.ask(stack.notice.asInstanceOf[Authentication], authState.future)
                authState.suspend()
            case state: ChannelFutureState => // return authenticate result
                if (state.future.isSuccess) {
                    authenticated = true
                    stack.`return`()
                } else {
                    authenticationException = new AuthenticationException(state.future.causeUnsafe)
                    logger.error(s"Database[$url] authenticate failed! ", authenticationException)
                    stack.`return`()
                }

    private def handleAuthentication(stack: AskStack[Authentication]): Option[StackState] =
        stack.state match
            case StackState.start => // waiting for socket connected
                val auth = stack.ask
                createDriver(auth.driver, auth.url, auth.info)
                connect(connectOptions.socketAddress, None)
            case state: ChannelFutureState if state.id == 0 => // waiting to authentication
                channel = state.future.channel
                val authState = ChannelFutureState(1)
                channel.ask(stack.ask, authState.future)
                authState.suspend()
            case state: ChannelFutureState => // return authenticate result
                if (state.future.isSuccess) {
                    authenticated = true
                    stack.`return`(ChannelEstablished(channel.id))
                } else {
                    authenticationException = new AuthenticationException(state.future.causeUnsafe)
                    stack.`throw`(ExceptionMessage(authenticationException))
                }

    private def createDriver(driverName: Option[String], url: String, info: Map[String, String]): Unit = {
        val factory = driverName match
            case Some(name) => DriverManager.getDriverFactory(name)
            case None       => DriverManager.defaultDriver(url)
        connectOptions = factory.parseOptions(url, info)
        driver = factory.newDriver(connectOptions)
    }

    private def handleExecuteUpdate(stack: AskStack[ExecuteUpdate]): Option[StackState] = {
        stack.state match
            case state: StartState =>
                val stat         = stack.ask
                val channelState = ChannelFutureState()
                channel.ask(stat, channelState.future)
                channelState.suspend()
            case state: ChannelFutureState =>
                if (state.future.isSuccess) stack.`return`(state.future.getNow.asInstanceOf[ModifyRows])
                else stack.`throw`(ExceptionMessage(state.future.causeUnsafe))
    }

    private def handleExecuteQuery(stack: AskStack[ExecuteQuery[Row]]): Option[StackState] = {
        stack.state match
            case state: StartState =>
                val stat         = stack.ask
                val channelState = ChannelFutureState()
                channel.ask(stat, channelState.future)
                channelState.suspend()
            case state: ChannelFutureState =>
                if (state.future.isSuccess) stack.`return`(state.future.getNow.asInstanceOf[Row])
                else stack.`throw`(ExceptionMessage(state.future.causeUnsafe))
    }

    private def handleExecuteQueries(stack: AskStack[ExecuteQueries[Row]]): Option[StackState] = {
        stack.state match
            case state: StartState =>
                val stat         = stack.ask
                val channelState = ChannelFutureState()
                channel.ask(stat, channelState.future)
                channelState.suspend()
            case state: ChannelFutureState =>
                if (state.future.isSuccess) stack.`return`(state.future.getNow.asInstanceOf[RowSet[Row]])
                else stack.`throw`(ExceptionMessage(state.future.causeUnsafe))
    }

    private def handleCursor(stack: AskStack[ExecuteCursor[?]]): Option[StackState] = {

        ???
    }

}

object Connection {
    type MSG = Authentication | ExecuteUpdate | ExecuteQuery[? <: Row] | ExecuteQueries[? <: Row]
}
