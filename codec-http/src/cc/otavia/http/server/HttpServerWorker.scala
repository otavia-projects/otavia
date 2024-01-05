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

package cc.otavia.http.server

import cc.otavia.core.actor.AcceptedWorkerActor
import cc.otavia.core.actor.AcceptorActor.AcceptedChannel
import cc.otavia.core.cache.ActorThreadLocal
import cc.otavia.core.channel.{Channel, ChannelAddress, ChannelOption}
import cc.otavia.core.message.Reply
import cc.otavia.core.stack.helper.{ChannelFutureState, FutureState, StartState}
import cc.otavia.core.stack.{AskStack, ChannelStack, StackState}
import cc.otavia.http.codec.ServerCodec
import cc.otavia.http.server.Router.ControllerRouter
import cc.otavia.http.{HttpMethod, OK}

import java.nio.charset.StandardCharsets
import scala.language.unsafeNulls

class HttpServerWorker(routerMatcher: RouterMatcher, dates: ActorThreadLocal[Array[Byte]], serverName: String)
    extends AcceptedWorkerActor[Nothing] {

    private val serverNameBytes: Array[Byte] = serverName.replaceAll("\r|\n", "").getBytes(StandardCharsets.US_ASCII)

    final override protected def initChannel(channel: Channel): Unit = {
        channel.setOption(ChannelOption.CHANNEL_STACK_BARRIER, HttpServerWorker.BARRIER_FUNC)
        channel.setOption(ChannelOption.CHANNEL_STACK_HEAD_OF_LINE, true)
        channel.setOption(ChannelOption.CHANNEL_MAX_STACK_INFLIGHT, 10240)
        channel.pipeline.addLast(new ServerCodec(routerMatcher, dates, serverNameBytes))
    }

    override def resumeAsk(stack: AskStack[AcceptedChannel]): Option[StackState] = handleAccepted(stack)

    override protected def afterAccepted(channel: ChannelAddress): Unit = logger.debug("Accepted {}", channel)

    override protected def resumeChannelStack(stack: ChannelStack[AnyRef]): Option[StackState] = {
        stack.state match
            case StackState.start =>
                val request = stack.message.asInstanceOf[HttpRequest[?, ?]]
                request match
                    case request: InternalHttpRequest => stack.`return`(OK())
                    case _ =>
                        val state = FutureState[Reply]()
                        request.router.asInstanceOf[ControllerRouter].controller.askUnsafe(request, state.future)
                        state.suspend()
            case state: FutureState[?] =>
                val response = state.future.getNow
                stack.`return`(response)
    }

}

object HttpServerWorker {
    private val BARRIER_FUNC: AnyRef => Boolean = request => {
        val method = request.asInstanceOf[HttpRequest[?, ?]].method
        !(method == HttpMethod.GET || method == HttpMethod.HEAD)
    }
}
