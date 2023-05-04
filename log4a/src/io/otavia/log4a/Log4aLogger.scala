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

import io.otavia.core.slf4a.helpers.Util
import io.otavia.core.slf4a.{AbstractLogger, LogLevel}
import io.otavia.core.system.ActorSystem
import io.otavia.log4a.InternalLogger.{AppenderLogger, BufferedLogger}

import java.time.LocalDateTime
import scala.language.unsafeNulls

class Log4aLogger(val name: String, val level: LogLevel, val appenderNames: Array[String]) extends AbstractLogger {

    private var internalLogger: InternalLogger = new BufferedLogger()

    override def getName: String = name

    override def onLoaded(system: ActorSystem): Unit = {
        // TODO:
    }

    override def isTraceEnabled: Boolean = ???

    override def isDebugEnabled: Boolean = ???

    override def isWarnEnabled: Boolean = ???

    override def isErrorEnabled: Boolean = ???

    override def trace(msg: String): Unit =
        println(s"${LocalDateTime.now()}\tTRACE\t[${Thread.currentThread().getName}]\t${name}: ${msg}")

    override def debug(msg: String): Unit =
        println(s"${LocalDateTime.now()}\tDEBUG\t[${Thread.currentThread().getName}]\t${name}: ${msg}")

    override def info(msg: String): Unit =
        println(s"${LocalDateTime.now()}\tINFO\t[${Thread.currentThread().getName}]\t${name}: ${msg}")

    override def warn(msg: String): Unit =
        println(s"${LocalDateTime.now()}\tWARN\t[${Thread.currentThread().getName}]\t${name}: ${msg}")

    override def error(msg: String): Unit =
        println(s"${LocalDateTime.now()}\tERROR\t[${Thread.currentThread().getName}]\t${name}: ${msg}")

}
