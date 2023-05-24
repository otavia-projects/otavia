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

import java.util.concurrent.atomic.AtomicBoolean

class DefaultActorThreadPool(
    override val system: ActorSystem,
    override val actorThreadFactory: ActorThreadFactory,
    override val size: Int
) extends ActorThreadPool {

    private var cursor: Int = ActorThreadPool.INVALID_THREAD_ID

    private val threads: Array[ActorThread] = new Array[ActorThread](size)

    @volatile private var inited: Boolean = false

    private val normalSelector = new TilingThreadSelector(threads)
    private val ioSelector     = new TilingThreadSelector(threads)

    private def init(): Unit = {
        for (index <- 0 until size) {
            val thread = createActorThread()
            threads(index) = thread
            thread.start()
        }
        inited = true
    }

    override def isInit: Boolean = inited

    override def nextThreadId(): Int = {
        cursor += 1
        cursor
    }

    override protected def createActorThread(): ActorThread = {
        actorThreadFactory.newThread()
    }

    override def next(channels: Boolean): ActorThread = {
        if (!inited) {
            this.synchronized { init() }
        }
        if (!channels) normalSelector.select() else ioSelector.select()
    }

    override def nexts(num: Int, channels: Boolean): Seq[ActorThread] = {
        if (!inited) {
            this.synchronized {
                init()
            }
        }
        if (!channels) normalSelector.select(num) else ioSelector.select(num)
    }

    override def workers: Array[ActorThread] = threads

    override def busiest: Option[ActorThread] = {
        ???
    }

}
