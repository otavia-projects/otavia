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

package cc.otavia.log4a

import cc.otavia.common.ThrowableUtil
import cc.otavia.core.address.Address
import cc.otavia.core.slf4a.{AbstractLogger, LogLevel}
import cc.otavia.core.system.ActorSystem
import cc.otavia.log4a.appender.Appender
import cc.otavia.log4a.appender.Appender.*

import java.time.LocalDateTime
import scala.collection.mutable
import scala.language.unsafeNulls

class Log4aLogger(val name: String, val level: LogLevel, val appenderNames: Array[String]) extends AbstractLogger {

    private var appender: Address[Appender.LogMsg] = _

    private var tempMessageBuffer: mutable.ArrayBuffer[Appender.LogMsg] = mutable.ArrayBuffer.empty[Appender.LogMsg]

    @volatile private var loaded: Boolean = false

    override def getName: String = name

    override def onLoaded(system: ActorSystem): Unit = {
        appender = system.getAddress(classOf[Appender], qualifier = Some(appenderNames.head))
        loaded = true
        for (msg <- tempMessageBuffer) appender.notice(msg)
        tempMessageBuffer = null
    }

    override def isTraceEnabled: Boolean = level >= LogLevel.TRACE

    override def isDebugEnabled: Boolean = level >= LogLevel.DEBUG

    override def isInfoEnabled: Boolean = level >= LogLevel.INFO

    override def isWarnEnabled: Boolean = level >= LogLevel.WARN

    override def isErrorEnabled: Boolean = level >= LogLevel.ERROR

    override def trace(msg: String): Unit = if (isTraceEnabled) {
        val notice = Trace(LocalDateTime.now(), Thread.currentThread().getName, name, msg)
        if (loaded) appender.notice(notice) else tempMessageBuffer.addOne(notice)
    }

    override def debug(msg: String): Unit = if (isDebugEnabled) {
        val notice = Debug(LocalDateTime.now(), Thread.currentThread().getName, name, msg)
        if (loaded) appender.notice(notice) else tempMessageBuffer.addOne(notice)
    }

    override def info(msg: String): Unit = if (isInfoEnabled) {
        val notice = Info(LocalDateTime.now(), Thread.currentThread().getName, name, msg)
        if (loaded) appender.notice(notice) else tempMessageBuffer.addOne(notice)
    }

    override def warn(msg: String): Unit = if (isWarnEnabled) {
        val notice = Warn(LocalDateTime.now(), Thread.currentThread().getName, name, msg)
        if (loaded) appender.notice(notice) else tempMessageBuffer.addOne(notice)
    }

    override def error(msg: String): Unit = if (isErrorEnabled) {
        val notice = Error(LocalDateTime.now(), Thread.currentThread().getName, name, msg)
        if (loaded) appender.notice(notice) else tempMessageBuffer.addOne(notice)
    }

    override def error(msg: String, e: Throwable): Unit = if (isErrorEnabled) {
        val notice = Error(
          LocalDateTime.now(),
          Thread.currentThread().getName,
          name,
          s"$msg \n ${ThrowableUtil.stackTraceToString(e)}"
        )
        if (loaded) appender.notice(notice) else tempMessageBuffer.addOne(notice)
    }

}
