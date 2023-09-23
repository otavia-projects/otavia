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

package cc.otavia.examples.basic

import cc.otavia.core.actor.*
import cc.otavia.core.address.Address
import cc.otavia.core.ioc.Injectable
import cc.otavia.core.message.{Ask, Notice, Reply, TimeoutEvent}
import cc.otavia.core.slf4a.Appender
import cc.otavia.core.stack.StackState.FutureState
import cc.otavia.core.stack.{AskStack, NoticeStack, ReplyFuture, StackState}
import cc.otavia.core.system.{ActorSystem, ActorThread}
import cc.otavia.core.timer.TimeoutTrigger
import cc.otavia.examples.HandleStateActor
import cc.otavia.examples.basic.Basic.*

import java.util.concurrent.TimeUnit

class Basic(args: Array[String]) extends MainActor(args) {
    override def main0(stack: NoticeStack[MainActor.Args]): Option[StackState] = {
        logger.info("main0 return")
        println("main0 return")
        stack.`return`()
    }

}

object Basic {

    // -XX:NewRatio=1 -XX:SurvivorRatio=8
    def main(args: Array[String]): Unit = {
        val system = ActorSystem()
        system.runMain(() => new Basic(args))
        val start = System.currentTimeMillis()
        for (id <- 0 until 200_000) {
            val pongActor = system.buildActor[PongActor](() => new PongActor())
            val pingActor = system.buildActor[PingActor](() => new PingActor(pongActor))
            val start1    = System.currentTimeMillis()
            for (idx <- 0 until 1_000) {
                pingActor.notice(Start(idx))
            }
            val end1 = System.currentTimeMillis()
//            if (id % 100 == 0) println(s"spend ${end1 - start1}")
//            Thread.sleep(100)
        }
        val end = System.currentTimeMillis()

        println(s"main exit with ${end - start}")
    }

    private case class Start(id: Int) extends Notice

    private case class Ping() extends Ask[Pong]

    private case class Pong() extends Reply

    private class PingActor(val pongActor: Address[Ping]) extends StateActor[Start] {

        override protected def afterMount(): Unit = {
//            logger.info("The PingActor has been mounted to ActorSystem.")
        }

        override def continueNotice(stack: NoticeStack[Start]): Option[StackState] = {
            stack.state match
                case StackState.start =>
                    val state = new FutureState[Pong]
                    pongActor.ask(Ping(), state.future)
//                    logger.info("Send ping to pongActor")
                    state.suspend()
                case state: FutureState[Pong] =>
                    val pong = state.future.getNow
//                    logger.info(s"Get pong message $pong")
                    stack.`return`()
        }

    }

    private class PongActor extends StateActor[Ping] {

        override protected def afterMount(): Unit = {
            val trigger = TimeoutTrigger.DelayPeriod(1, 2, TimeUnit.SECONDS, TimeUnit.SECONDS)
//            timer.registerActorTimeout(trigger, self)
//            logger.info("PongActor register timeout trigger")
        }

        override def continueAsk(stack: AskStack[Ping]): Option[StackState] = {
//            logger.info(s"PongActor received ask message ${stack.ask}")
            stack.`return`(Pong())
        }

        override protected def handleActorTimeout(timeoutEvent: TimeoutEvent): Unit = {
//            logger.info(s"PongActor handle timeout event ${timeoutEvent}")
        }

    }

}
