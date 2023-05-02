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

package io.otavia.log4a.appender

import io.otavia.core.actor.StateActor
import io.otavia.core.message.Notice
import io.otavia.core.stack.{BatchNoticeStack, NoticeStack, StackState}
import io.otavia.log4a.appender.ConsoleAppender.*
import io.otavia.log4a.{LogMessage, MultiMessage, SingleMessage}

import scala.collection.mutable

class ConsoleAppender extends StateActor[LogMessage] with Appender {

    private val cache = new mutable.StringBuilder()

    override def batchable: Boolean = true

    override def maxBatchSize: Int = 1024

    override def batchContinueNotice(stack: BatchNoticeStack[LogMessage]): Option[StackState] = {
        for (notice <- stack.notices) {
            notice match
                case SingleMessage(msg) => printMsg(msg)
                case MultiMessage(msgs) => msgs.foreach(msg => printMsg(msg))
        }
        print(cache)
        cache.clear()
        stack.`return`()
    }

    inline private def printMsg(msg: String): Unit = {
        cache.append(msg)
    }

}

object ConsoleAppender {

    private val space: String = " "
    private val line: String  = "\n"

}
