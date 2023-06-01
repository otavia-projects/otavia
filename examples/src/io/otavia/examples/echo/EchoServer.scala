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

import io.otavia.core.actor.*
import io.otavia.core.actor.ChannelsActor.*
import io.otavia.core.channel.{Channel, ChannelAddress}
import io.otavia.core.stack.StackState.FutureState
import io.otavia.core.stack.{NoticeStack, StackState}
import io.otavia.core.system.ActorSystem
import io.otavia.examples.HandleStateActor

object EchoServer {

    def main(args: Array[String]): Unit = {

        val system = ActorSystem()

        system.buildActor(() => new Main(args))

    }

    private class Main(args: Array[String]) extends MainActor(args) {
        override def main0(stack: NoticeStack[MainActor.Args]): Option[StackState] = {
            stack.stackState match
                case StackState.start =>
                    val server = system.buildActor(() => new EchoServer())
                    server.ask(Bind(8080)).suspend()
                case state: FutureState[BindReply] =>
                    logger.info("echo bind port 8080 success")
                    stack.`return`()

        }

    }

    private class EchoServerWorker extends AcceptedWorkerActor[Nothing] {

        override protected def init(channel: Channel): Unit = {}

//        override protected def afterAccepted(channel: ChannelAddress): Unit = channels.put(channel.id, channel)

    }

    private class EchoServer extends AcceptorActor[EchoServerWorker] {
        override protected def workerFactory: AcceptorActor.WorkerFactory[EchoServerWorker] =
            () => new EchoServerWorker()

    }

}
