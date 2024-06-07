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

package cc.otavia.http.client

import cc.otavia.core.actor.ChannelsActor.*
import cc.otavia.core.actor.SocketChannelsActor
import cc.otavia.core.actor.SocketChannelsActor.*
import cc.otavia.core.channel.{Channel, ChannelAddress}
import cc.otavia.core.message.{Ask, Notice, Reply}
import cc.otavia.core.stack.helper.{ChannelFutureState, FutureState, FuturesState, StartState}
import cc.otavia.core.stack.{AskStack, NoticeStack, StackState, StackYield}
import cc.otavia.handler.ssl.SslContext
import cc.otavia.http.HttpVersion

import java.net.InetSocketAddress

class HttpClient(
    host: String,
    port: Int,
    sslCtx: Option[SslContext] = None,
    autoConnect: Boolean = true,
    httpVersion: HttpVersion = HttpVersion.HTTP_1_1
) extends SocketChannelsActor[HttpClientRequest | Connect] {

    private var channel: ChannelAddress = _

    override protected def initChannel(channel: Channel): Unit = {
        sslCtx match
            case None             =>
            case Some(sslContext) => channel.pipeline.addLast(sslContext.newHandler())

        channel.pipeline.addLast(new ClientCodec(httpVersion, sslCtx.nonEmpty))
    }

    override protected def afterMount(): Unit =
        if (autoConnect) this.noticeSelfHead(ConnectChannel(new InetSocketAddress(host, port)))

    override protected def resumeNotice(
        stack: NoticeStack[(HttpClientRequest | Connect) & Notice]
    ): StackYield = stack.state match
        case state: StartState =>
            val stk = stack.asInstanceOf[NoticeStack[Connect]]
            stack.suspend(connect(stk.notice.remote, stk.notice.local))
        case state: ChannelFutureState =>
            channel = state.future.channel
            stack.`return`()

    override protected def resumeAsk(stack: AskStack[HttpClientRequest | Connect]): StackYield =
        stack match
            case stack: AskStack[HttpClientRequest] if stack.ask.isInstanceOf[HttpClientRequest] =>
                handleRequest(stack)
            case stack: AskStack[Connect] if stack.ask.isInstanceOf[Connect] => handleConnect(stack)

    private def handleConnect(stack: AskStack[Connect]): StackYield = stack.state match
        case state: StartState =>
            stack.suspend(connect(stack.ask.remote, stack.ask.local))
        case state: ChannelFutureState =>
            channel = state.future.channel
            val established = ChannelEstablished(state.future.channel.id)
            stack.`return`(established)

    private def handleRequest(stack: AskStack[HttpClientRequest]): StackYield = stack.state match
        case state: StartState =>
            val futureState = ChannelFutureState()
            val request     = stack.ask
            channel.ask(request, futureState.future)
            stack.suspend(futureState)
        case state: ChannelFutureState =>
            stack.`return`(state.future.getNow.asInstanceOf[HttpClientResponse])

}

object HttpClient {}
