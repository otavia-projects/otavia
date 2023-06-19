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

import io.otavia.BuildInfo
import io.otavia.buffer.BufferAllocator
import io.otavia.core.actor.*
import io.otavia.core.address.*
import io.otavia.core.channel.ChannelFactory
import io.otavia.core.ioc.{BeanDefinition, BeanManager, Module}
import io.otavia.core.message.{Call, IdAllocator}
import io.otavia.core.reactor.aio.Submitter
import io.otavia.core.reactor.{BlockTaskExecutor, DefaultReactor}
import io.otavia.core.slf4a.{LogLevel, Logger}
import io.otavia.core.system.monitor.{ReactorMonitor, SystemMonitor, SystemMonitorTask, ThreadMonitor}
import io.otavia.core.timer.{Timeout, Timer, TimerImpl}
import io.otavia.core.transport.TransportFactory
import io.otavia.core.util.SystemPropertyUtil

import java.io.File
import java.lang.management.{ManagementFactory, MemoryMXBean}
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.{MILLISECONDS, MINUTES, TimeUnit}
import scala.language.unsafeNulls

class ActorSystemImpl(val name: String, val actorThreadFactory: ActorThreadFactory) extends ActorSystem {

    actorThreadFactory.setSystem(this)

    @volatile private var inited: Boolean = false

    private val earlyModules = new ConcurrentLinkedQueue[Module]()

    private val logger = Logger.getLogger(getClass, this)

    private val actorThreadPool: ActorThreadPool = new DefaultActorThreadPool(
      this,
      actorThreadFactory,
      ActorSystem.ACTOR_THREAD_POOL_SIZE
    )

    private val generator = new AtomicLong(1)

    private val beanManager = new BeanManager(this)

    private val totals = new AtomicLong(0)

    private val timerExecutor = new TimerImpl(this)

    private var mainActor: Address[MainActor.Args] = _

    private val direct = BufferAllocator.directPageAllocator
    private val heap   = BufferAllocator.heapPageAllocator

    private val transFactory: TransportFactory = TransportFactory.getTransportFactory
    private val chFactory: ChannelFactory      = new ChannelFactory(transFactory)

    private val memoryMXBean: MemoryMXBean = ManagementFactory.getMemoryMXBean

    @volatile private var busy: Boolean = false

    private var memoryMonitor: Timeout = _

    if (ActorSystem.MEMORY_MONITOR) {
        val duration = ActorSystem.MEMORY_MONITOR_DURATION * 100
        memoryMonitor =
            timer.internalTimer.newTimeout(_ => calculateBusy(), duration, MILLISECONDS, duration, MILLISECONDS)
    }

    private val systemMonitorTask      = new SystemMonitorTask(this)
    private var systemMonitor: Timeout = _

    if (ActorSystem.SYSTEM_MONITOR) {
        val duration = ActorSystem.SYSTEM_MONITOR_DURATION * 100
        systemMonitor = timer.internalTimer.newTimeout(_ => doMonitor(), duration, MILLISECONDS, duration, MILLISECONDS)
    }

    if (ActorSystem.PRINT_BANNER) {
        println(s"${Console.YELLOW}${SystemInfo.logo()}${Console.RESET}")
        println(SystemInfo.info())
        println("\n")
    }

    private val defaultReactor = new DefaultReactor(this, transFactory)

    private val gcTime = new AtomicLong(System.currentTimeMillis())

    inited = true

    loadEarlyModules()

    private def loadEarlyModules(): Unit = while (!earlyModules.isEmpty) {
        val module = earlyModules.poll()
        this.loadModule(module)
    }

    private def doMonitor(): Unit = systemMonitorTask.run()

    override def initialed: Boolean = inited

    override def pool: ActorThreadPool = actorThreadPool

    override private[core] def reactor = defaultReactor

    override def timer: Timer = timerExecutor

    override def blockingExecutor: BlockTaskExecutor = ???

    override def aio: Submitter = ???

    override def distributor: IdAllocator = ???

    override def directAllocator: BufferAllocator = direct

    override def headAllocator: BufferAllocator = heap

    override def shutdown(): Unit = ???

    override def defaultMaxFetchPerRunning: Int = ???

    override def defaultMaxBatchSize: Int = ???

    override def buildActor[A <: Actor[? <: Call]](
        factory: ActorFactory[A],
        num: Int = 1,
        global: Boolean = false,
        qualifier: Option[String] = None,
        primary: Boolean = false
    ): Address[MessageOf[A]] = {
        val actorFactory   = factory.asInstanceOf[ActorFactory[?]]
        val (address, clz) = createActor(actorFactory, num)

        if (global) beanManager.register(clz, address, qualifier, primary)

        mountActor(address)

        address.asInstanceOf[Address[MessageOf[A]]]
    }

    private final def mountActor(address: Address[?]): Unit = {
        address match
            case addr: ActorAddress[?] => addr.house.mount()
            case robinAddress: RobinAddress[?] =>
                robinAddress.underlying.foreach { addr =>
                    addr.asInstanceOf[ActorAddress[?]].house.mount()
                }
    }

    final private[system] def setActorContext[A <: Actor[? <: Call]](
        actor: A,
        thread: ActorThread
    ): Address[MessageOf[A]] = {
        val house   = thread.createActorHouse()
        val address = house.createActorAddress[MessageOf[A]]()
        val context = ActorContext(this, address, generator.getAndIncrement())

        actor.setCtx(context)
        house.setActor(actor.asInstanceOf[AbstractActor[? <: Call]])

        address
    }

    private def setActorContext0(actor: AbstractActor[?], thread: ActorThread): ActorAddress[?] = {
        val house   = thread.createActorHouse()
        val address = house.createUntypedAddress()
        val context = ActorContext(this, address, generator.getAndIncrement())

        actor.setCtx(context)
        house.setActor(actor)

        address
    }

    private def createActor(factory: ActorFactory[?], num: Int): (Address[?], Class[?]) = {
        if (num == 1) {
            val actor   = factory.create().asInstanceOf[AbstractActor[? <: Call]]
            val isIO    = actor.isInstanceOf[ChannelsActor[?]]
            val thread  = pool.next(isIO)
            val address = setActorContext0(actor, thread)
            logger.debug(s"Created actor $actor")
            (address, actor.getClass)
        } else if (num > 1) {
            val range   = (0 until num).toArray
            val actors  = range.map(_ => factory.create().asInstanceOf[AbstractActor[? <: Call]])
            val isIO    = actors.head.isInstanceOf[ChannelsActor[?]]
            val threads = pool.nexts(num, isIO)
            val address = range.map { index =>
                val actor  = actors(index)
                val thread = threads(index)
                setActorContext0(actor, thread)
            }
            logger.debug(s"Created actors ${actors.mkString("Array(", ", ", ")")}")

            (new RobinAddress[Call](address.asInstanceOf[Array[Address[Call]]]), actors.head.getClass)
        } else throw new IllegalArgumentException("num must large than 0")
    }

    override private[core] def registerGlobalActor(definition: BeanDefinition): Unit = {
        val (address, clz) = createActor(definition.factory, definition.num)
        beanManager.register(clz, address, definition.qualifier, definition.primary)
        mountActor(address)
    }

    override def loadModule(module: Module): Unit = try {
        if (!inited) {
            earlyModules.add(module)
        } else {
            logger.debug(s"Loading module $module")
            val unmount = new ArrayBuffer[Address[?]](module.definitions.length)
            module.definitions.foreach { definition =>
                val (address, clz) = createActor(definition.factory, definition.num)
                unmount.addOne(address)
                beanManager.register(clz, address, definition.qualifier, definition.primary)
            }
            unmount.foreach {
                case address: ActorAddress[?] => address.house.mount()
                case robinAddress: RobinAddress[?] =>
                    robinAddress.underlying.foreach { addr =>
                        addr.asInstanceOf[ActorAddress[?]].house.mount()
                    }
            }

            module.onLoaded(this)
        }
    } catch {
        case t: Throwable => logger.error(s"Load module $module occur error with ", t)
    }

    override def runMain[M <: MainActor](factory: ActorFactory[M], modules: Seq[Module] = Seq.empty): Unit = {
        modules.foreach(m => loadModule(m))
        val address = this.buildActor(factory)
        mainActor = address
    }

    override def getAddress[M <: Call](
        clz: Class[? <: Actor[?]],
        qualifier: Option[String],
        remote: Option[String]
    ): Address[M] = {
        val address = qualifier match
            case Some(value) => beanManager.getBean(value, clz)
            case None        => beanManager.getBean(clz)

        address.asInstanceOf[Address[M]]
    }

    override def toString: String = {
        val stats = monitor()

        s"name = ${stats.name}, threads = ${stats.threads}, beans = ${stats.beans}\n" +
            s"${stats.threadMonitor.timerMonitor}\n" +
            s"${stats.threadMonitor.actorThreadMonitors.map(_.toString).mkString("[", ",\n", "]")}"
    }

    override def monitor(): SystemMonitor = {
        val threadMonitor = ThreadMonitor(timer.monitor(), ReactorMonitor(), pool.workers.map(_.monitor()))
        SystemMonitor(name, pool.size, beanManager.count, threadMonitor)
    }

    override def channelFactory: ChannelFactory = chFactory

    override def isBusy: Boolean = busy

    private def calculateBusy(): Unit = {
        val usage = memoryMXBean.getHeapMemoryUsage
        if (usage.getUsed.toFloat / usage.getMax.toFloat > 0.90 && usage.getMax - usage.getUsed < 100 * 1024 * 1024)
            busy = true
        else busy = false
    }

    override private[core] def gc(): Unit = {
        val now  = System.currentTimeMillis()
        val last = gcTime.get()
        if (now - last > 1000 && gcTime.compareAndSet(last, now)) {
            System.gc()
            println(s"${Thread.currentThread()} GC")
        }
    }

}
