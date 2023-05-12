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

package io.otavia.core.rust.reactor

import io.otavia.core.channel.Channel
import io.otavia.core.reactor.*
import io.otavia.core.slf4a.Logger

import scala.language.unsafeNulls

class NativeHandler(val maxEvents: Int, val strategy: SelectStrategy) extends IoHandler {

    def this() = this(0, DefaultSelectStrategyFactory.newSelectStrategy())

    val logger: Logger = Logger.getLogger(getClass, system) // TODO: null bug for system

    /** Rust `Box<Poll>` */
    private var nativePoll: Long = ???

    override def run(context: IoExecutionContext): Int = ???

    override def prepareToDestroy(): Unit = ???

    override def destroy(): Unit = ???

    override def register(channel: Channel): Unit = ???

    override def deregister(channel: Channel): Unit = ???

    override def wakeup(inEventLoop: Boolean): Unit = ???

    override def isCompatible(handleType: Class[? <: Channel]): Boolean = ???

}

object NativeHandler {
    def newFactory(): IoHandlerFactory = new IoHandlerFactory {
        override def newHandler: IoHandler = new NativeHandler()
    }

}
