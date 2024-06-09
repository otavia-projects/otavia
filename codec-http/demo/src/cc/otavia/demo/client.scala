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

package cc.otavia.demo

import cc.otavia.core.actor.MainActor
import cc.otavia.core.stack.helper.{ChannelFutureState, FutureState, FuturesState, StartState}
import cc.otavia.core.stack.{NoticeStack, StackState, StackYield}
import cc.otavia.core.system.ActorSystem
import cc.otavia.demo.ServerMain.HelloMessage
import cc.otavia.handler.ssl.SslContextBuilder
import cc.otavia.http.HttpHeaderKey
import cc.otavia.http.client.{HttpClient, HttpClientRequest, HttpClientResponse}
import cc.otavia.serde.helper.StringSerde

class ClientMain() extends MainActor() {
    override def main0(stack: NoticeStack[MainActor.Args]): StackYield = stack.state match
        case state: StartState =>
            val sslCtx  = Some(SslContextBuilder.forClient().build())
            val client  = system.buildActor(() => new HttpClient("www.baidu.com", 443, sslCtx))
            val request = HttpClientRequest.builder.get().setPath("/").build(StringSerde.utf8)
            stack.suspend(client.ask(request))
        case state: FutureState[HttpClientResponse] =>
            val response = state.future.getNow
            println(state.future.getNow.body)
            stack.`return`()
}

@main def client(): Unit =
    val system = ActorSystem()
    system.buildActor(() => ClientMain())
