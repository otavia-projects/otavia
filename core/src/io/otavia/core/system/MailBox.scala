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

package io.otavia.core.system

import io.otavia.core.system.MailBox.MailsChunk
import io.otavia.core.util.Nextable

import java.util.concurrent.atomic.AtomicLong
import scala.language.unsafeNulls

class MailBox(val house: ActorHouse) {

    private val noticeChunk = new MailsChunk()

    private val askChunk = new MailsChunk()

    private val replyChunk = new MailsChunk()

    private val exceptionsChunk = new MailsChunk()

    private val eventChunk = new MailsChunk()

    def size: Long = noticeChunk.size.get() + askChunk.size.get() +
        replyChunk.size.get() + exceptionsChunk.size.get() + eventChunk.size.get()

}

object MailBox {
    private class MailsChunk {

        @volatile var head: Nextable = _
        @volatile var tail: Nextable = _

        val size = new AtomicLong(0)

    }
}
