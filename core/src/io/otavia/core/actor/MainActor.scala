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

package io.otavia.core.actor

import io.otavia.core.actor.MainActor.Args
import io.otavia.core.message.{Ask, Call, Notice, UnitReply}
import io.otavia.core.stack.{AskStack, NoticeStack, StackState}

abstract class MainActor(val args: Array[String]) extends StateActor[Args] {

    final override def afterMount(): Unit = {
        self.notice(Args(args))
        logInfo(s"Send ${args.mkString("Array(", ", ", ")")} to ${getClass.getSimpleName}[MainActor]")
    }

    final override def continueNotice(stack: NoticeStack[Args]): Option[StackState] = main0(stack)

    def main0(stack: NoticeStack[Args]): Option[StackState]

}

object MainActor {
    case class Args(args: Array[String]) extends Notice

}
