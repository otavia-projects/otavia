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

package io.otavia.core.transport.reactor.nio

import io.otavia.core.channel.Channel
import io.otavia.core.reactor.Reactor.Command.*
import io.otavia.core.reactor.Reactor.{Command, DEFAULT_MAX_TASKS_PER_RUN}
import io.otavia.core.reactor.{IoExecutionContext, IoHandler, LoopExecutor, Reactor}
import io.otavia.core.slf4a.Logger
import io.otavia.core.system.ActorSystem
import io.otavia.core.system.ActorSystem.DEFAULT_PRINT_BANNER
import io.otavia.core.transport.TransportFactory
import io.otavia.core.transport.reactor.nio.NioReactor.{NIO_REACTOR_WORKERS, NioThreadFactory}
import io.otavia.core.util.{SpinLockQueue, SystemPropertyUtil}

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ConcurrentLinkedQueue, Executors, ThreadFactory}
import scala.language.unsafeNulls

class NioReactor(
    val system: ActorSystem,
    val transportFactory: TransportFactory,
    val maxTasksPerRun: Int = DEFAULT_MAX_TASKS_PER_RUN
) extends AtomicInteger
    with Reactor {

    private val logger: Logger = Logger.getLogger(getClass, system)

    private val threadFactory = new NioThreadFactory()

    private val workers: Array[NioReactorWorker] = new Array[NioReactorWorker](NIO_REACTOR_WORKERS)

    workers.indices.foreach { idx =>
        workers(idx) = new NioReactorWorker(LoopExecutor(threadFactory), system, maxTasksPerRun, new NioHandler(system))
    }

    override def submit(command: Command): Unit = {
        val idx    = math.abs(command.channel.hashCode()) % NIO_REACTOR_WORKERS
        val worker = workers(idx)
        worker.submitCommand(command)
    }

}

object NioReactor {

    private val DEFAULT_NIO_REACTOR_WORKERS = 2
    val NIO_REACTOR_WORKERS: Int =
        SystemPropertyUtil.getInt("io.otavia.reactor.nio.workers", DEFAULT_NIO_REACTOR_WORKERS)

    final class NioThreadFactory extends ThreadFactory {

        private val tid: AtomicInteger = new AtomicInteger(0)

        private def getThreadId(): Int = {
            var cid: Int = tid.get()
            while (!tid.compareAndSet(cid, cid + 1)) {
                cid = tid.get()
            }
            cid
        }

        override def newThread(r: Runnable): Thread = {

            val thread = new Thread(r, s"otavia-reactor-nio-${getThreadId()}")
            try {
                if (thread.isDaemon) thread.setDaemon(false)
                if (thread.getPriority != Thread.NORM_PRIORITY) thread.setPriority(Thread.NORM_PRIORITY)
            } catch {
                case ignore: Exception =>
            }
            thread
        }

    }

}
