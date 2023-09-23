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

import cc.otavia.buffer.pool.AdaptiveBuffer
import cc.otavia.core.actor.ChannelsActor.{Connect, ConnectReply}
import cc.otavia.core.actor.{ChannelsActor, MainActor, SocketChannelsActor}
import cc.otavia.core.address.Address
import cc.otavia.core.channel.{Channel, ChannelHandler, ChannelHandlerContext, ChannelInitializer}
import cc.otavia.core.message.{Ask, Reply}
import cc.otavia.core.stack.StackState.FutureState
import cc.otavia.core.stack.{AskStack, ChannelReplyFuture, NoticeStack, StackState}
import cc.otavia.core.system.ActorSystem
import cc.otavia.handler.codec.MessageToByteEncoder

import java.net.InetAddress
import java.nio.charset.{Charset, StandardCharsets}
import scala.language.unsafeNulls

object EchoClient {

    def main(args: Array[String]): Unit = {
        val system = ActorSystem()
        system.buildActor(() => new Main(args))
    }

    private class Main(args: Array[String]) extends MainActor(args) {

        private var clientActor: Address[Connect | Echo] = _

        override def main0(stack: NoticeStack[MainActor.Args]): Option[StackState] =
            stack.state match
                case StackState.start =>
                    clientActor = system.buildActor(() => new ClientActor())
                    clientActor.ask(Connect(InetAddress.getByName("localhost"), 8080)).suspend()
                case state: FutureState[ConnectReply] =>
                    state.future.cause match
                        case Some(value) =>
                            value.printStackTrace()
                            stack.`return`()
                        case None =>
                            println("connected")
                            clientActor.ask(Echo("hello otavia!")).suspend()
                case state: FutureState[EchoReply] =>
                    if (state.future.isSuccess) {
                        println(s"get echo reply: ${state.future.getNow.answer}")
                    }
                    stack.`return`()

    }

    private class ClientActor extends SocketChannelsActor[Echo] {

        override def handler: Option[ChannelInitializer[? <: Channel]] = Some(
          new ChannelInitializer[Channel] {
              override protected def initChannel(ch: Channel): Unit = ch.pipeline.addFirst(new ClientHandler())
          }
        )

        override def continueAsk(stack: AskStack[Connect | Echo]): Option[StackState] = {
            stack match
                case s: AskStack[Connect] if s.ask.isInstanceOf[Connect] => connect(s)
                case s: AskStack[Echo] if s.ask.isInstanceOf[Echo]       => echo(s)
        }

        private def echo(stack: AskStack[Echo]): Option[StackState] = {
            stack.state match
                case StackState.start =>
                    val state = new EchoWaitState()
                    channels.head._2.ask(stack.ask, state.future)
                    state.suspend()
                case state: EchoWaitState =>
                    val answer = state.future.getNow.asInstanceOf[String]
                    stack.`return`(EchoReply(answer))
        }

    }

    private case class Echo(question: String)    extends Ask[EchoReply]
    private case class EchoReply(answer: String) extends Reply

    private class EchoWaitState extends StackState { val future = ChannelReplyFuture() }

    private class ClientHandler extends MessageToByteEncoder {

        override protected def encode(ctx: ChannelHandlerContext, output: AdaptiveBuffer, msg: AnyRef, msgId: Long): Unit =
            output.writeCharSequence(msg.asInstanceOf[Echo].question)

        override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef): Unit = {
            val buffer = ctx.inboundAdaptiveBuffer
            val sc     = buffer.readCharSequence(buffer.readableBytes, StandardCharsets.UTF_8)
            ctx.fireChannelRead(sc.toString)
        }

    }

}
