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

package io.otavia.log4a

import io.otavia.core.actor.MainActor
import io.otavia.core.slf4a.Logger
import io.otavia.core.stack.{NoticeStack, StackState}
import io.otavia.core.system.ActorSystem
import org.scalatest.funsuite.AnyFunSuite

class ActorSystemSuite extends AnyFunSuite {

    import ActorSystemSuite.*

    test("SPI load log4a") {
        val system = ActorSystem()
        system.runMain(() => new Main(Array.empty))
        Thread.sleep(1000 * 5)
        assert(true)
    }

}

object ActorSystemSuite {

    class Main(args: Array[String]) extends MainActor(args) {

        override def main0(stack: NoticeStack[MainActor.Args]): Option[StackState] = {
            logger.info("main actor running")
            stack.`return`()
        }

    }

}
