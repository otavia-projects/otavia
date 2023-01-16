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

import io.netty5.util.HashedWheelTimer
import io.netty5.util.internal.shaded.org.jctools.queues.MpscChunkedArrayQueue
import io.otavia.core.address.ChannelsActorAddress
import io.otavia.core.channel.Channel
import io.otavia.core.reactor.Reactor.DEFAULT_MAX_TASKS_PER_RUN
import io.otavia.core.reactor.ReactorImpl.ST_NOT_STARTED
import io.otavia.core.system.ActorSystem
import org.log4s.{Logger, getLogger}

import java.util.concurrent.{ConcurrentHashMap, ThreadFactory}

final class ReactorImpl(
    private[core] val system: ActorSystem,
    private[core] val ioHandlerFactory: IoHandlerFactory,
    private[core] val maxTasksPerRun: Int = DEFAULT_MAX_TASKS_PER_RUN
) extends Reactor {

    protected val logger: Logger = getLogger

    private val registerQueue   = new MpscChunkedArrayQueue[Channel](10240)
    private val deregisterQueue = new MpscChunkedArrayQueue[Channel](10240)

    private val executor              = LoopExecutor()
    private var thread: Thread | Null = null

    private val ioHandler: IoHandler = ioHandlerFactory.newHandler

    private val context = new IoExecutionContext {

        override def canBlock: Boolean = registerQueue.isEmpty && deregisterQueue.isEmpty

        override def delayNanos(currentTimeNanos: Long): Long = ???

        override def deadlineNanos: Long = ???

    }

    @volatile private val state = ST_NOT_STARTED

    override def register(channel: Channel): Unit = {
        registerQueue.add(channel)
        startThread()
    }

    override def deregister(channel: Channel): Unit = deregisterQueue.add(channel)

    private def startThread(): Unit = if (state == ST_NOT_STARTED) {}

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
        runIO()

        runRegisters(maxTasksPerRun)
        runDeregisters(maxTasksPerRun)
    }

    /** Called when IO will be processed for all the [[Channel]]s on this [[Reactor]]. This method returns the number of
     *  [[Channel]]s for which IO was processed.
     *
     *  This method must be called from the [[executor]] executor.
     */
    private def runIO(): Unit = ioHandler.run(context)

    private def hasTask: Boolean = !registerQueue.isEmpty && !deregisterQueue.isEmpty

    /** Run by [[executor]] executor */
    private def runRegisters(maxTasks: Int): Int = {
        var processedTasks: Int = 0
        while (processedTasks < maxTasks && hasTask) {
            runRegister(registerQueue.poll())
            processedTasks += 1
        }
        processedTasks
    }

    private def runRegister(channel: Channel): Unit = if (channel.isRegistered)
        channel.executorAddress.inform(
          RegisterReplyEvent(channel, false, new IllegalStateException("Channel already registered"))
        )
    else ioHandler.register(channel)

    private def runDeregisters(maxTasks: Int): Int = {
        var processedTasks: Int = 0
        while (processedTasks < maxTasks && hasTask) {
            runDeregister(deregisterQueue.poll())
            processedTasks += 1
        }
        processedTasks
    }

    private def runDeregister(channel: Channel): Unit = if (!channel.isRegistered)
        channel.executorAddress.inform(
          DeregisterReplyEvent(channel, false, new IllegalStateException("Channel not registered"))
        )
    else ioHandler.deregister(channel)

}

object ReactorImpl {

    private val ST_NOT_STARTED   = 1
    private val ST_STARTED       = 2
    private val ST_SHUTTING_DOWN = 3
    private val ST_SHUTDOWN      = 4
    private val ST_TERMINATED    = 5

//    final case class ChannelTask(channel: Channel, address: ChannelsActorAddress[?])
}
