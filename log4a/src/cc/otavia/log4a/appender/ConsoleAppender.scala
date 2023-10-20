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

package cc.otavia.log4a.appender

import cc.otavia.core.actor.StateActor
import cc.otavia.core.address.Address
import cc.otavia.core.ioc.{Component, Primary}
import cc.otavia.core.message.Notice
import cc.otavia.core.stack.{BatchNoticeStack, NoticeStack, StackState}
import cc.otavia.log4a.appender.Appender
import cc.otavia.log4a.appender.Appender.*

import java.time.format.DateTimeFormatter
import scala.collection.mutable

class ConsoleAppender extends StateActor[LogMsg], Appender {

    import ConsoleAppender.*

    override def batchable: Boolean = true
    override def maxBatchSize: Int  = 1000

    private val cache     = new mutable.StringBuilder()
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    override protected def batchContinueNotice(stack: BatchNoticeStack[LogMsg]): Option[StackState] = {
        stack.notices.foreach(serializeLogMsg)
        print(cache.toString())
        cache.clear()
        stack.`return`()
    }

    override protected def continueNotice(stack: NoticeStack[LogMsg]): Option[StackState] = {
        serializeLogMsg(stack.notice)
        print(cache.toString())
        cache.clear()
        stack.`return`()
    }

    private def serializeLogMsg(logMsg: LogMsg): Unit = logMsg match
        case trace: Trace =>
            cache.append(Console.WHITE)
            cache.append(trace.time.format(formatter))
            cache.append("\tTRACE\t")
            writeLogLine(trace)
            cache.append(Console.RESET)
        case debug: Debug =>
            cache.append(debug.time.format(formatter))
            cache.append("\tDEBUG\t")
            writeLogLine(debug)
        case info: Info =>
            cache.append(Console.GREEN)
            cache.append(info.time.format(formatter))
            cache.append("\tINFO\t")
            writeLogLine(info)
            cache.append(Console.RESET)
        case warn: Warn =>
            cache.append(Console.YELLOW)
            cache.append(warn.time.format(formatter))
            cache.append("\tWARN\t")
            writeLogLine(warn)
            cache.append(Console.RESET)
        case error: Error =>
            cache.append(Console.RED)
            cache.append(error.time.format(formatter))
            cache.append("\tERROR\t")
            writeLogLine(error)
            cache.append(Console.RESET)
        case fatal: Fatal => ???

    private def writeLogLine(logMsg: LogMsg): Unit = {
        cache.append('[')
        cache.append(logMsg.thread)
        cache.append("]\t")
        cache.append(logMsg.loggerName)
        cache.append('\t')
        cache.append(logMsg.log)
        cache.append('\n')
    }

}

object ConsoleAppender {

    private val space: String = " "
    private val line: String  = "\n"

}
