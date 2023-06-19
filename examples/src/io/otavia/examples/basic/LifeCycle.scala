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

import io.otavia.core.actor.{BeforeStop, StateActor}
import io.otavia.core.message.Notice
import io.otavia.core.stack.{NoticeStack, StackState}
import io.otavia.core.system.ActorSystem

import scala.language.unsafeNulls

object LifeCycle {

    def main(args: Array[String]): Unit = {
        val system  = ActorSystem()
        var address = system.buildActor(() => new LifeActor())

        address.notice(Start())

        // let garbage collector move address to ReferenceQueue
        // than ActorThread will call AbstractActor.stop -> LifeActor.beforeStop
        // than the LifeActor instance will be reclaimed by garbage collector
        address = null

    }

    private case class Start() extends Notice

    private class LifeActor extends StateActor[Start] with BeforeStop {

        override protected def afterMount(): Unit = {
            println("LifeActor: afterMount")
        }

        override protected def beforeRestart(): Unit = {
            println("LifeActor: beforeRestart")
        }

        override protected def restart(): Unit = {
            println("LifeActor: restart")
        }

        override protected def afterRestart(): Unit = {
            println("LifeActor: afterRestart")
        }

        override def continueNotice(stack: NoticeStack[Start]): Option[StackState] =
            // if occurs some error which developer is not catch, this will trigger the actor restart
            throw new Error("")

    }

}
