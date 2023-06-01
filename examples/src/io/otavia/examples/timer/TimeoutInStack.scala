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

package io.otavia.examples.timer

import io.otavia.core.actor.{ChannelsActor, MainActor, SocketChannelsActor}
import io.otavia.core.stack.{NoticeStack, StackState, TimeoutEventFuture}
import io.otavia.core.system.ActorSystem
import io.otavia.examples.HandleStateActor

object TimeoutInStack {

    def main(args: Array[String]): Unit = {
        val system = ActorSystem()
        system.buildActor(() => new TimeoutInStackActor(args))
    }

    private class TimeoutInStackActor(args: Array[String]) extends MainActor(args) {
        override def main0(stack: NoticeStack[MainActor.Args]): Option[StackState] = {
            stack.stackState match
                case StackState.start =>
                    val state = new TimeoutState()
                    timer.askTimeout(state.timeoutEventFuture, 2000)
                    state.suspend()
                case state: TimeoutState =>
                    println(s"timeout event reach: ${state.timeoutEventFuture.getNow}")
                    stack.`return`()
        }

    }

    private class TimeoutState extends StackState {
        val timeoutEventFuture: TimeoutEventFuture = TimeoutEventFuture()
    }

}
