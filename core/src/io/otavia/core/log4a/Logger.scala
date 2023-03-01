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

package io.otavia.core.log4a

import io.otavia.core.actor.{Actor, StateActor}
import io.otavia.core.log4a.Logger.LogMsg
import io.otavia.core.message.{IdAllocator, Notice}

import java.time.LocalDateTime

trait Logger extends Actor[LogMsg]

object Logger {

    sealed trait LogMsg extends Notice {
        val clz: Class[?]
        val time: LocalDateTime
        val log: String
    }

    final case class Trace(clz: Class[?], time: LocalDateTime, log: String) extends LogMsg

    final case class Debug(clz: Class[?], time: LocalDateTime, log: String) extends LogMsg

    final case class Info(clz: Class[?], time: LocalDateTime, log: String) extends LogMsg

    final case class Warn(clz: Class[?], time: LocalDateTime, log: String) extends LogMsg

    final case class Error(clz: Class[?], time: LocalDateTime, log: String) extends LogMsg

    final case class Fatal(clz: Class[?], time: LocalDateTime, log: String) extends LogMsg
}
