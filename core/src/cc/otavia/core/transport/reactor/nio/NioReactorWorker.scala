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
import cc.otavia.core.reactor.Reactor.Command
import cc.otavia.core.reactor.Reactor.Command.*
import cc.otavia.core.reactor.{IoExecutionContext, IoHandler, IoHandlerFactory, Reactor}
import cc.otavia.core.slf4a.Logger
import cc.otavia.core.system.ActorSystem
import cc.otavia.core.util.SpinLockQueue

import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import scala.language.unsafeNulls

class NioReactorWorker(
    val executor: Executor,
    val system: ActorSystem,
    val maxTasksPerRun: Int,
    val ioHandlerFactory: IoHandlerFactory
) extends AtomicInteger {

    import NioReactorWorker.*

    private val logger: Logger = Logger.getLogger(getClass, system)

    private val commandQueue = new SpinLockQueue[Command]()

    private var thread: Thread = _

    private val context = new IoExecutionContext {

        override def canBlock: Boolean = commandQueue.isEmpty

        override def delayNanos(currentTimeNanos: Long): Long = 50 * 1000 * 1000

        override def deadlineNanos: Long = ???

    }

    private var ioHandler: IoHandler = _

    set(ST_NOT_STARTED)

    def submitCommand(command: Command): Unit = {
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
            ioHandler = ioHandlerFactory.newHandler
            try {
                run()
            } catch {
                case t: Throwable => logger.warn("Unexpected exception from an event executor:", t)
            } finally {}
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
        case register: Register                        => ioHandler.register(register.channel)
        case deregister: Deregister                    => ioHandler.deregister(deregister.channel)
        case Bind(channel, local)                      => ioHandler.bind(channel, local)
        case Open(channel, path, options, attrs)       => ioHandler.open(channel, path, options, attrs)
        case Connect(channel, remote, local, fastOpen) => ioHandler.connect(channel, remote, local, fastOpen)
        case Read(channel, plan)                       => ioHandler.read(channel, plan)
        case Flush(channel, payload)                   => ioHandler.flush(channel, payload)

    private def confirmShutdown(): Boolean = false

}

object NioReactorWorker {

    private val ST_NOT_STARTED   = 1
    private val ST_STARTED       = 2
    private val ST_SHUTTING_DOWN = 3
    private val ST_SHUTDOWN      = 4
    private val ST_TERMINATED    = 5

}
