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

package io.otavia.core.reactor

import io.otavia.core.channel.Channel
import io.otavia.core.reactor.DefaultReactor.{ST_NOT_STARTED, ST_STARTED}
import io.otavia.core.reactor.Reactor.Command.*
import io.otavia.core.reactor.Reactor.{Command, DEFAULT_MAX_TASKS_PER_RUN}
import io.otavia.core.slf4a.Logger
import io.otavia.core.system.ActorSystem
import io.otavia.core.transport.TransportFactory
import io.otavia.core.util.SpinLockQueue

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import scala.language.unsafeNulls

class DefaultReactor(
    val system: ActorSystem,
    val transportFactory: TransportFactory,
    val maxTasksPerRun: Int = DEFAULT_MAX_TASKS_PER_RUN
) extends AtomicInteger
    with Reactor {

    private val logger: Logger = Logger.getLogger(getClass, system)

    private val commandQueue = new SpinLockQueue[Command]()

    private val executor              = LoopExecutor()
    private var thread: Thread | Null = _

    private val ioHandler: IoHandler = {
        val handler = transportFactory.openIoHandler(system)
        handler
    }

    private val context = new IoExecutionContext {

        override def canBlock: Boolean = commandQueue.isEmpty

        override def delayNanos(currentTimeNanos: Long): Long = 50 * 1000 * 1000

        override def deadlineNanos: Long = ???

    }

    set(ST_NOT_STARTED)

    override def submit(command: Command): Unit = {
        commandQueue.enqueue(command)
        startThread()
    }

    private def startThread(): Unit = if (get() == ST_NOT_STARTED && compareAndSet(ST_NOT_STARTED, ST_STARTED)) {
        var success = false
        try {
            doStartThread()
            success = true
        } finally {
            if (!success) compareAndSet(ST_STARTED, ST_NOT_STARTED)
        }
    }

    private def doStartThread(): Unit = {
        assert(thread == null)
        executor.execute(() => {
            thread = Thread.currentThread()
            try { run() }
            catch { case t: Throwable => logger.warn("Unexpected exception from an event executor:") }
            finally {}
        })
    }

    private def run(): Unit = {
        while (!confirmShutdown()) {
            runIO()
            runCommands(maxTasksPerRun)
        }
    }

    /** Called when IO will be processed for all the [[Channel]]s on this [[Reactor]]. This method returns the number of
     *  [[Channel]]s for which IO was processed.
     *
     *  This method must be called from the [[executor]] executor.
     */
    private def runIO(): Unit = ioHandler.run(context)

    private def hasTask: Boolean = commandQueue.nonEmpty

    private def runCommands(maxTasks: Int): Int = {
        var processedTasks: Int = 0
        while (processedTasks < maxTasks && commandQueue.nonEmpty) {
            runCommand(commandQueue.dequeue())
            processedTasks += 1
        }
        processedTasks
    }

    private def runCommand(command: Command): Unit = command match
        case read: Read =>
            read.channel.unsafeChannel.setReadPlan(read.plan)
            read.channel.unsafeChannel.unsafeRead()
        case register: Register     => ioHandler.register(register.channel)
        case deregister: Deregister => ioHandler.deregister(deregister.channel)

    private def confirmShutdown(): Boolean = false

}

object DefaultReactor {

    private val ST_NOT_STARTED   = 1
    private val ST_STARTED       = 2
    private val ST_SHUTTING_DOWN = 3
    private val ST_SHUTDOWN      = 4
    private val ST_TERMINATED    = 5

//    final case class ChannelTask(channel: Channel, address: ChannelsActorAddress[?])
}
