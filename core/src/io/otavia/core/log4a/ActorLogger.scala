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

import io.netty5.util.internal.ThrowableUtil
import io.otavia.core.actor.{AbstractActor, Actor}
import io.otavia.core.address.Address
import io.otavia.core.log4a.Appender
import io.otavia.core.log4a.Appender.{Debug, Error, Fatal, Info, Trace, Warn}
import io.otavia.core.message.IdAllocator
import io.otavia.core.system.ActorSystem

import java.time.LocalDateTime
import scala.language.unsafeNulls

class ActorLogger private[core] (val clz: Class[?], actor: AbstractActor[?]) {

    private val system: ActorSystem            = actor.system
    private val logger: Address[Appender.LogMsg] = system.getAddress(classOf[Appender])
    private given AbstractActor[?]             = actor

    def logTrace(log: String): Unit =
        if (system.logLevel >= LogLevel.TRACE) logger.notice(Trace(clz, LocalDateTime.now(), log))
    def logDebug(log: String): Unit =
        if (system.logLevel >= LogLevel.DEBUG) logger.notice(Debug(getClass, LocalDateTime.now(), log))
    def logDebug(log: String, e: Throwable): Unit = logDebug(s"${log}\n${ThrowableUtil.stackTraceToString(e)}")

    def logInfo(log: String): Unit =
        if (system.logLevel >= LogLevel.INFO) logger.notice(Info(getClass, LocalDateTime.now(), log))

    def logWarn(log: String): Unit =
        if (system.logLevel >= LogLevel.WARN) logger.notice(Warn(getClass, LocalDateTime.now(), log))

    def logWarn(log: String, e: Throwable): Unit = logWarn(s"${log}\n${ThrowableUtil.stackTraceToString(e)}")

    def logError(log: String): Unit =
        if (system.logLevel >= LogLevel.ERROR) logger.notice(Error(getClass, LocalDateTime.now(), log))

    def logError(log: String, e: Throwable): Unit = logError(s"${log}\n${ThrowableUtil.stackTraceToString(e)}")
    def logError(e: Throwable): Unit              = logError(s"${ThrowableUtil.stackTraceToString(e)}")

    def logFatal(log: String): Unit =
        if (system.logLevel >= LogLevel.FATAL) logger.notice(Fatal(getClass, LocalDateTime.now(), log))

    def logFatal(log: String, e: Throwable): Unit = logFatal(s"${log}\n${ThrowableUtil.stackTraceToString(e)}")

}

object ActorLogger {
    def getLogger(clz: Class[?])(using actor: AbstractActor[?]): ActorLogger = new ActorLogger(clz, actor)
}
