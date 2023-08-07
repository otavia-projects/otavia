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

package cc.otavia.examples.timer

import cc.otavia.core.actor.StateActor
import cc.otavia.core.message.TimeoutEvent
import cc.otavia.core.system.ActorSystem
import cc.otavia.core.timer.TimeoutTrigger

import java.util.concurrent.TimeUnit

object Tick {

    def main(args: Array[String]): Unit = {
        val system = ActorSystem()
        system.buildActor(() => new TickOnceActor())
        system.buildActor(() => new TickPeriodActor())
    }

    private class TickOnceActor extends StateActor[Nothing] {

        override protected def afterMount(): Unit = {
            timer.registerActorTimeout(TimeoutTrigger.DelayTime(1, TimeUnit.SECONDS), self)
        }
        override protected def handleActorTimeout(timeoutEvent: TimeoutEvent): Unit = {
            println(s"TickOnceActor received timeout event ${timeoutEvent}")
        }

    }

    private class TickPeriodActor extends StateActor[Nothing] {

        override protected def afterMount(): Unit = {
            timer.registerActorTimeout(TimeoutTrigger.DelayPeriod(1, 5, TimeUnit.SECONDS, TimeUnit.SECONDS), self)
        }

        override protected def handleActorTimeout(timeoutEvent: TimeoutEvent): Unit = {
            println(s"TickPeriodActor received timeout event ${timeoutEvent} ${System.currentTimeMillis() / 1000}")
        }

    }

}
