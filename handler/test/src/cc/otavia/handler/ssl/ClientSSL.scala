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

package cc.otavia.handler.ssl

import cc.otavia.core.actor.ChannelsActor.ChannelEstablished
import cc.otavia.core.actor.SocketChannelsActor.{Connect, ConnectChannel}
import cc.otavia.core.actor.{MainActor, SocketChannelsActor}
import cc.otavia.core.address.Address
import cc.otavia.core.channel.Channel
import cc.otavia.core.message.{Ask, Reply}
import cc.otavia.core.stack.helper.{ChannelFutureState, FutureState, FuturesState, StartState}
import cc.otavia.core.stack.{AskStack, NoticeStack, StackState, StackYield}
import cc.otavia.core.system.ActorSystem

import java.net.InetSocketAddress

object ClientSSL {
    def main(args: Array[String]): Unit = {
        val sys = ActorSystem()

        sys.buildActor(() =>
            new MainActor() {
                private val sslCtx                   = SslContextBuilder.forClient().build()
                private val handler                  = sslCtx.newHandler()
                private var client: Address[Connect] = _

                override def main0(stack: NoticeStack[MainActor.Args]): StackYield = {
                    stack.state match
                        case state: StartState =>
                            client = system.buildActor(() =>
                                new SocketChannelsActor[Connect] {
                                    override protected def initChannel(channel: Channel): Unit = {
                                        channel.pipeline.addLast(sslCtx.newHandler())
                                    }

                                    override protected def resumeAsk(stack: AskStack[Connect]): StackYield =
                                        connect(stack)
                                }
                            )
                            val newState = FutureState[ChannelEstablished]()
                            val remote   = new InetSocketAddress("www.baidu.com", 443)

                            client.ask(ConnectChannel(remote, None), newState.future)

                            stack.suspend(newState)
                        case state: FutureState[ChannelEstablished] =>
                            stack.`return`()
                }
            }
        )

    }

}
