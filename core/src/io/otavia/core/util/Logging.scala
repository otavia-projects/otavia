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

package io.otavia.core.util

import io.netty5.util.internal.ThrowableUtil
import io.otavia.core.actor.Actor
import io.otavia.core.address.Address
import io.otavia.core.ioc.Injectable
import io.otavia.core.message.Message
import io.otavia.core.util.Logger
import io.otavia.core.util.Logger.*

import java.time.LocalDateTime

private[core] trait Logging {
    this: Actor[?] =>

    protected lazy val logger: Address[Logger.LogMsg] = system.getAddress(classOf[Logger])

    def logTrace(log: String): Unit = if (system.logLevel >= 6) logger.notice(Trace(getClass, LocalDateTime.now(), log))

    def logDebug(log: String): Unit = if (system.logLevel >= 5) logger.notice(Debug(getClass, LocalDateTime.now(), log))

    def logInfo(log: String): Unit = if (system.logLevel >= 4) logger.notice(Info(getClass, LocalDateTime.now(), log))

    def logWarn(log: String): Unit = if (system.logLevel >= 3) logger.notice(Warn(getClass, LocalDateTime.now(), log))
    def logWarn(log: String, e: Throwable): Unit = logWarn(s"${log}\n${ThrowableUtil.stackTraceToString(e)}")

    def logError(log: String): Unit = if (system.logLevel >= 2) logger.notice(Error(getClass, LocalDateTime.now(), log))
    def logError(log: String, e: Throwable): Unit = logError(s"${log}\n${ThrowableUtil.stackTraceToString(e)}")

    def logFatal(log: String): Unit = if (system.logLevel >= 1) logger.notice(Fatal(getClass, LocalDateTime.now(), log))
    def logFatal(log: String, e: Throwable): Unit = logFatal(s"${log}\n${ThrowableUtil.stackTraceToString(e)}")
}
