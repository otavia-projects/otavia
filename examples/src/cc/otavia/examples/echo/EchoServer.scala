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

package cc.otavia.examples.echo

import cc.otavia.core.actor.*
import cc.otavia.core.actor.AcceptorActor.AcceptedChannel
import cc.otavia.core.actor.ChannelsActor.*
import cc.otavia.core.channel.{Channel, ChannelAddress, ChannelHandler, ChannelHandlerContext}
import cc.otavia.core.message.{Ask, Reply}
import cc.otavia.core.stack.StackState.FutureState
import cc.otavia.core.stack.{AskStack, NoticeStack, StackState}
import cc.otavia.core.system.ActorSystem
import cc.otavia.examples.HandleStateActor

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
                    println("echo bind port 8080 success")
                    logger.info("echo bind port 8080 success")
                    stack.`return`()

        }

    }

    private class EchoServerWorker extends AcceptedWorkerActor[Nothing] {

        override protected def init(channel: Channel): Unit = {
            channel.pipeline.addLast(new WorkerHandler())
        }

        override def continueAsk(stack: AskStack[AcceptedChannel]): Option[StackState] = handleAccepted(stack)

        override protected def afterAccepted(channel: ChannelAddress): Unit = {
            println(s"EchoServerWorker accepted ${channel}")
            super.afterAccepted(channel)
        }

    }

    private class EchoServer extends AcceptorActor[EchoServerWorker] {
        override protected def workerFactory: AcceptorActor.WorkerFactory[EchoServerWorker] =
            () => new EchoServerWorker()

    }

    private class WorkerHandler extends ChannelHandler {

        override def channelRegistered(ctx: ChannelHandlerContext): Unit = {
            println(s"${ctx.channel} registered")
        }

        override def channelActive(ctx: ChannelHandlerContext): Unit = {
            println(s"${ctx.channel} active")
        }

        override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef): Unit = {
            println("read")
        }

    }

}
