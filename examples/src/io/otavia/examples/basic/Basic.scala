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

package io.otavia.examples.basic

import io.otavia.core.actor.*
import io.otavia.core.address.Address
import io.otavia.core.ioc.Injectable
import io.otavia.core.log4a.Appender
import io.otavia.core.message.{Ask, Notice, Reply}
import io.otavia.core.stack.StackState.FutureState
import io.otavia.core.stack.{AskStack, NoticeStack, ReplyFuture, StackState}
import io.otavia.core.system.ActorSystem
import io.otavia.examples.HandleStateActor
import io.otavia.examples.basic.Basic.*

class Basic(args: Array[String]) extends MainActor(args) {
    override def main0(stack: NoticeStack[MainActor.Args]): Option[StackState] = {
        val pongActor = system.buildActor[PongActor](() => new PongActor())
        val pingActor = system.buildActor[PingActor](() => new PingActor(pongActor))

        pingActor.notice(Start())

        stack.`return`()
    }

}

object Basic {

    def main(args: Array[String]): Unit = {
        ActorSystem().runMain(() => new Basic(args))
    }

    private case class Start() extends Notice

    private case class Ping() extends Ask[Pong]

    private case class Pong() extends Reply

    private class PingActor(val pongActor: Address[Ping]) extends StateActor[Start] {

        override protected def afterMount(): Unit =
            println("The PingActor has been mounted to ActorSystem.")

        override def continueNotice(stack: NoticeStack[Start]): Option[StackState] = {
            stack.stackState match
                case StackState.initialState =>
                    val state = FutureState[Pong]
                    pongActor.ask(Ping(), state.future)
                    state.suspend()
                case state: FutureState[Pong] =>
                    val pong = state.future.getNow
                    println("Get pong message")
                    stack.`return`()
        }

    }

    private class PongActor extends StateActor[Ping] {
        override def continueAsk(stack: AskStack[Ping]): Option[StackState] = {
            stack.`return`(Pong())
        }

    }

}
