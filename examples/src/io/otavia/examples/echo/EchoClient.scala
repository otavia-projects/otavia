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

package io.otavia.examples.echo

import io.otavia.core.actor.ChannelsActor.{Connect, ConnectReply}
import io.otavia.core.actor.{MainActor, SocketChannelsActor}
import io.otavia.core.message.{Ask, Reply}
import io.otavia.core.stack.StackState.FutureState
import io.otavia.core.stack.{AskStack, NoticeStack, StackState}
import io.otavia.core.system.ActorSystem

object EchoClient {

    def main(args: Array[String]): Unit = {
        val system = ActorSystem()

        system.buildActor(() => new Main(args))
    }

    private class Main(args: Array[String]) extends MainActor(args) {
        override def main0(stack: NoticeStack[MainActor.Args]): Option[StackState] = {
            stack.stackState match
                case StackState.start =>
                    val client = system.buildActor(() => new ClientActor())
                    client.ask(Connect("localhost", 8080)).suspend()
                case state: FutureState[ConnectReply] =>
                    println("connected")
                    stack.`return`()
        }

    }

    class ClientActor extends SocketChannelsActor[Nothing] {

        override def continueAsk(stack: AskStack[Connect]): Option[StackState] = connect(stack)

    }

}
