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

import cc.otavia.core.channel.Channel
import cc.otavia.core.reactor.*
import cc.otavia.core.reactor.Reactor.Command
import cc.otavia.core.slf4a.Logger
import cc.otavia.core.system.{ActorSystem, ActorThread}
import cc.otavia.core.transport.TransportFactory
import cc.otavia.core.transport.reactor.nio.NioReactor.{NioThreadChooser, NioThreadFactory}
import cc.otavia.core.util.SpinLockQueue

import java.util.SplittableRandom
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ConcurrentLinkedQueue, Executors, ThreadFactory, ThreadLocalRandom}
import scala.language.unsafeNulls

class NioReactor(
    val system: ActorSystem,
    val transportFactory: TransportFactory,
    val maxTasksPerRun: Int = -1  // -1 means use config default
) extends AtomicInteger
    with Reactor {

    private val logger: Logger = Logger.getLogger(getClass, system)

    private val threadFactory = new NioThreadFactory()

    private val actualMaxTasksPerRun = if (maxTasksPerRun > 0) maxTasksPerRun else system.config.reactor.maxTasksPerRun
    private val nioWorkers = system.config.reactor.nioWorkerSize

    private val workers: Array[NioReactorWorker] = new Array[NioReactorWorker](nioWorkers)

    private val nioThreadChoicer = NioThreadChooser(system.actorWorkerSize, nioWorkers)

    workers.indices.foreach { idx =>
        workers(idx) = new NioReactorWorker(
          LoopExecutor(threadFactory),
          system,
          actualMaxTasksPerRun,
          new IoHandlerFactory {
              override def newHandler: IoHandler = new NioHandler(system)
          }
        )
    }

    override def submit(command: Reactor.Command): Unit = {
        val idx    = nioThreadChoicer.choice(command.channel)
        val worker = workers(idx)
        worker.submitCommand(command)
    }

}

object NioReactor {

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

    private trait NioThreadChooser {
        def choice(channel: Channel): Int
    }

    private object NioThreadChooser {

        def apply(actorThreadSize: Int, ioThreadSize: Int): NioThreadChooser =
            if (actorThreadSize == ioThreadSize) new OneByOneNioThreadChooser
            else if (ioThreadSize % actorThreadSize == 0)
                new PreferentialNioThreadChooser(ioThreadSize / actorThreadSize, actorThreadSize)
            else new RandomNioThreadChooser(ioThreadSize)

        private final class OneByOneNioThreadChooser extends NioThreadChooser {
            override def choice(channel: Channel): Int = channel.mountThreadId
        }

        private final class RandomNioThreadChooser(nioSize: Int) extends NioThreadChooser {
            override def choice(channel: Channel): Int = math.abs(channel.hashCode()) % nioSize
        }

        private final class PreferentialNioThreadChooser(factor: Int, base: Int) extends NioThreadChooser {

            override def choice(channel: Channel): Int =
                (math.abs(channel.hashCode()) % factor) * base + channel.mountThreadId

        }

    }

}
