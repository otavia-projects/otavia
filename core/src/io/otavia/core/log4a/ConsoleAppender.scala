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

import io.otavia.core.actor.StateActor
import io.otavia.core.address.Address
import io.otavia.core.ioc.{Component, Primary}
import io.otavia.core.log4a.Appender
import io.otavia.core.stack.{BatchNoticeStack, StackState}

import scala.collection.mutable

@Component
class ConsoleAppender extends StateActor[Appender.LogMsg], Appender {

    import ConsoleAppender.*

    override def batchable: Boolean = true
    override def maxBatchSize: Int  = 1000

    private val cache = new mutable.StringBuilder()

    override def batchContinueNotice(stack: BatchNoticeStack[Appender.LogMsg]): Option[StackState] = {
        for (notice <- stack.notices) {
            cache.append(notice.clz.getName)
            cache.append(space)
            cache.append(notice.time.toString)
            cache.append(space)
            cache.append(notice.log)
            cache.append(line)
        }
        print(cache)
        cache.clear()
        stack.`return`()
    }

}

object ConsoleAppender {

    private val space: String = " "
    private val line: String  = "\n"

}
