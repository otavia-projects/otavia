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

package cc.otavia.core.transport.reactor.nio

import cc.otavia.common.SystemPropertyUtil
import cc.otavia.core.channel.Channel
import cc.otavia.core.reactor.*
import cc.otavia.core.reactor.Reactor.Command.*
import cc.otavia.core.reactor.Reactor.{Command, DEFAULT_MAX_TASKS_PER_RUN}
import cc.otavia.core.slf4a.Logger
import cc.otavia.core.system.ActorSystem.DEFAULT_PRINT_BANNER
import cc.otavia.core.system.{ActorSystem, ActorThread}
import cc.otavia.core.transport.TransportFactory
import cc.otavia.core.transport.reactor.nio.NioReactor.{NIO_REACTOR_WORKERS, NioThreadChoicer, NioThreadFactory}
import cc.otavia.core.util.SpinLockQueue

import java.util.SplittableRandom
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ConcurrentLinkedQueue, Executors, ThreadFactory, ThreadLocalRandom}
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

    private val nioThreadChoicer = NioThreadChoicer(system.actorWorkerSize, NIO_REACTOR_WORKERS)

    workers.indices.foreach { idx =>
        workers(idx) = new NioReactorWorker(
          LoopExecutor(threadFactory),
          system,
          maxTasksPerRun,
          new IoHandlerFactory {
              override def newHandler: IoHandler = new NioHandler(system)
          }
        )
    }

    override def submit(command: Command): Unit = {
        val idx    = nioThreadChoicer.choice(command.channel)
        val worker = workers(idx)
        worker.submitCommand(command)
    }

}

object NioReactor {

    private val DEFAULT_NIO_REACTOR_WORKERS = Runtime.getRuntime.availableProcessors()
    val NIO_REACTOR_WORKERS: Int = {
        if (SystemPropertyUtil.get("cc.otavia.nio.worker.size").nonEmpty)
            SystemPropertyUtil.getInt("cc.otavia.nio.worker.size", DEFAULT_NIO_REACTOR_WORKERS)
        else if (SystemPropertyUtil.get("cc.otavia.nio.worker.ratio").nonEmpty)
            (SystemPropertyUtil.getFloat("cc.otavia.nio.worker.ratio", 1.0) * DEFAULT_NIO_REACTOR_WORKERS).toInt
        else Runtime.getRuntime.availableProcessors()
    }

    final private class NioThreadFactory extends ThreadFactory {

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

    private trait NioThreadChoicer {
        def choice(channel: Channel): Int
    }

    private object NioThreadChoicer {

        def apply(actorThreadSize: Int, ioThreadSize: Int): NioThreadChoicer =
            if (actorThreadSize == ioThreadSize) new OneByOneNioThreadChoicer
            else if (ioThreadSize % actorThreadSize == 0)
                new PreferentialNioThreadChoicer(ioThreadSize / actorThreadSize, actorThreadSize)
            else new RandomNioThreadChoicer(ioThreadSize)

        private final class OneByOneNioThreadChoicer extends NioThreadChoicer {
            override def choice(channel: Channel): Int = channel.mountThreadId
        }

        private final class RandomNioThreadChoicer(nioSize: Int) extends NioThreadChoicer {
            override def choice(channel: Channel): Int = math.abs(channel.hashCode()) % nioSize
        }

        private final class PreferentialNioThreadChoicer(factor: Int, base: Int) extends NioThreadChoicer {

            override def choice(channel: Channel): Int =
                (math.abs(channel.hashCode()) % factor) * base + channel.mountThreadId

        }

    }

}
