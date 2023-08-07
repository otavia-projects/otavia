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

import cc.otavia.core.slf4a.{AbstractLogger, LogLevel}
import cc.otavia.core.system.ActorSystem
import cc.otavia.core.util.{Report, ThrowableUtil}
import cc.otavia.log4a.InternalLogger.{AppenderLogger, BufferedLogger}

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.language.unsafeNulls

class Log4aLogger(val name: String, val level: LogLevel, val appenderNames: Array[String]) extends AbstractLogger {

    private var internalLogger: InternalLogger = new BufferedLogger()

    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    override def getName: String = name

    override def onLoaded(system: ActorSystem): Unit = {
        // TODO:
    }

    override def isTraceEnabled: Boolean = ???

    override def isDebugEnabled: Boolean = ???

    override def isWarnEnabled: Boolean = ???

    override def isErrorEnabled: Boolean = ???

    override def trace(msg: String): Unit =
        println(
          s"${Console.WHITE}${LocalDateTime.now().format(formatter)}\tTRACE\t[${Thread
                  .currentThread()
                  .getName}]\t${name} -\t${msg}${Console.RESET}"
        )

    override def debug(msg: String): Unit =
        println(
          s"${LocalDateTime.now().format(formatter)}\tDEBUG\t[${Thread
                  .currentThread()
                  .getName}]\t${name} -\t${msg}"
        )

    override def info(msg: String): Unit =
        println(
          s"${Console.GREEN}${LocalDateTime.now().format(formatter)}\tINFO\t[${Thread.currentThread().getName}]\t${name} -\t${msg}${Console.RESET}"
        )

    override def warn(msg: String): Unit =
        println(s"${Console.YELLOW}${LocalDateTime.now().format(formatter)}\tWARN\t[${Thread
                .currentThread()
                .getName}]\t${name} -\t${msg}${Console.RESET}")

    override def error(msg: String): Unit =
        println(
          s"${Console.RED}${LocalDateTime.now().format(formatter)}\tERROR\t[${Thread.currentThread().getName}]\t${name} -\t${msg}${Console.RESET}"
        )

    override def error(msg: String, e: Throwable): Unit =
        println(s"${Console.RED}${LocalDateTime.now().format(formatter)}\tERROR\t[${Thread
                .currentThread()
                .getName}]\t${name} -\t${msg}\n${ThrowableUtil.stackTraceToString(e)}${Console.RESET}")

}
