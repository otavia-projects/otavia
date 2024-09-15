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
import cc.otavia.core.actor.SocketChannelsActor
import cc.otavia.core.channel.{Channel, ChannelAddress}
import cc.otavia.core.message.*
import cc.otavia.core.stack.*
import cc.otavia.core.stack.helper.*
import cc.otavia.sql.statement.*

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

    override protected def resumeAsk(stack: AskStack[Connection.MSG]): StackYield = {
        stack match
            case stack: AskStack[?] if stack.ask.isInstanceOf[Authentication] =>
                handleAuthentication(stack.asInstanceOf[AskStack[Authentication]])
            case stack: AskStack[?] if stack.ask.isInstanceOf[SimpleQuery[?]] =>
                handleSimpleQuery(stack.asInstanceOf[AskStack[SimpleQuery[Reply]]])
            case stack: AskStack[?] if stack.ask.isInstanceOf[PrepareQuery[?]] =>
                handlePrepareQuery(stack.asInstanceOf[AskStack[PrepareQuery[Reply]]])
    }

    override protected def resumeNotice(stack: NoticeStack[Connection.MSG & Notice]): StackYield =
        stack.state match
            case _: StartState =>
                val auth = stack.notice.asInstanceOf[Authentication]
                createDriver(auth.driver, auth.url, auth.info)
                stack.suspend(connect(connectOptions.socketAddress, None))
            case state: ChannelFutureState if state.id == 0 => // waiting to authentication
                channel = state.future.channel
                val authState = ChannelFutureState(1)
                channel.ask(stack.notice.asInstanceOf[Authentication], authState.future)
                stack.suspend(authState)
            case state: ChannelFutureState => // return authenticate result
                if (state.future.isSuccess) {
                    authenticated = true
                    stack.`return`()
                } else {
                    authenticationException = new AuthenticationException(state.future.causeUnsafe)
                    logger.error(s"Database[$url] authenticate failed! ", authenticationException)
                    stack.`return`()
                }

    private def handleAuthentication(stack: AskStack[Authentication]): StackYield =
        stack.state match
            case _: StartState => // waiting for socket connected
                val auth = stack.ask
                createDriver(auth.driver, auth.url, auth.info)
                stack.suspend(connect(connectOptions.socketAddress, None))
            case state: ChannelFutureState if state.id == 0 => // waiting to authentication
                channel = state.future.channel
                val authState = ChannelFutureState(1)
                channel.ask(stack.ask, authState.future)
                stack.suspend(authState)
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

    private def handleSimpleQuery(stack: AskStack[SimpleQuery[Reply]]): StackYield = {
        stack.state match
            case _: StartState =>
                val query        = stack.ask
                val channelState = ChannelFutureState()
                channel.ask(query, channelState.future)
                stack.suspend(channelState)
            case state: ChannelFutureState =>
                if (state.future.isSuccess) stack.`return`(state.future.getNow.asInstanceOf[Reply])
                else stack.`throw`(ExceptionMessage(state.future.causeUnsafe))
    }

    private def handlePrepareQuery(stack: AskStack[PrepareQuery[Reply]]): StackYield = {
        stack.state match
            case _: StartState =>
                val query        = stack.ask
                val channelState = ChannelFutureState()
                channel.ask(query, channelState.future)
                stack.suspend(channelState)
            case state: ChannelFutureState =>
                if (state.future.isSuccess) stack.`return`(state.future.getNow.asInstanceOf[Reply])
                else stack.`throw`(ExceptionMessage(state.future.causeUnsafe))
    }

    private def handleCursor(stack: AskStack[ExecuteCursor[?]]): StackYield = {

        ???
    }

}

object Connection {
    type MSG = Authentication | SimpleQuery[? <: Reply] | PrepareQuery[? <: Reply]
}
