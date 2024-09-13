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

package cc.otavia.core.system

import cc.otavia.core.actor.*
import cc.otavia.core.address.*
import cc.otavia.core.cache.ThreadLocal
import cc.otavia.core.channel.ChannelFactory
import cc.otavia.core.ioc.{BeanDefinition, BeanManager, Module}
import cc.otavia.core.message.Call
import cc.otavia.core.slf4a.Logger
import cc.otavia.core.system.monitor.{ReactorMonitor, SystemMonitor, SystemMonitorTask, ThreadMonitor}
import cc.otavia.core.timer.{Timeout, Timer, TimerImpl}
import cc.otavia.core.transport.TransportFactory

import java.lang.management.{ManagementFactory, MemoryMXBean}
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.MILLISECONDS
import scala.language.unsafeNulls

final private[core] class ActorSystemImpl(val name: String, val actorThreadFactory: ActorThreadFactory)
    extends ActorSystem {

    actorThreadFactory.setSystem(this)

    @volatile private var initialize: Boolean = false

    private val earlyModules = new ConcurrentLinkedQueue[Module]()

    private val logger = Logger.getLogger(getClass, this)

    private val timerImpl = new TimerImpl(this)

    private val actorThreadPool: ActorThreadPool = new DefaultActorThreadPool(
      this,
      actorThreadFactory,
      ActorSystem.ACTOR_THREAD_POOL_SIZE
    )

    private val generator = new AtomicLong(1)

    private val beanManager = new BeanManager(this)

    private val totals = new AtomicLong(0)

    private var mainActor: Address[MainActor.Args] = _

    private val transFactory: TransportFactory = TransportFactory.getTransportFactory(this)
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

    private val react = transFactory.openReactor(this)

    private val gcTime = new AtomicLong(System.currentTimeMillis())

    private val threadLocals: mutable.Set[ThreadLocal[?]] = mutable.HashSet.empty

    initialize = true

    loadEarlyModules()

    private def loadEarlyModules(): Unit = while (!earlyModules.isEmpty) {
        val module = earlyModules.poll()
        this.loadModule(module)
    }

    private def doMonitor(): Unit = systemMonitorTask.run()

    override def initialized: Boolean = initialize

    override private[core] def pool: ActorThreadPool = actorThreadPool

    override private[core] def reactor = react

    override def timer: Timer = timerImpl

    override def shutdown(): Unit = ???

    override def defaultMaxFetchPerRunning: Int = ???

    override def defaultMaxBatchSize: Int = 100000

    // format: off
    override def buildActor[A <: Actor[? <: Call]](factory: ActorFactory[A], num: Int = 1,
        global: Boolean = false, qualifier: Option[String] = None, primary: Boolean = false
    ): Address[MessageOf[A]] = {
    // format: on
        val actorFactory   = factory.asInstanceOf[ActorFactory[?]]
        val (address, clz) = createActor(actorFactory, num)

        if (global) beanManager.register(clz, address, qualifier, primary)

        mountActor(address)

        address.asInstanceOf[Address[MessageOf[A]]]
    }

    private def mountActor(address: Address[?]): Unit = {
        address match
            case addr: ActorAddress[?] => addr.house.mount()
            case robinAddress: RobinAddress[?] =>
                robinAddress.underlying.foreach { addr => addr.house.mount() }
    }

    private def setActorContext(actor: AbstractActor[?], thread: ActorThread, lb: Boolean = false): ActorAddress[?] = {
        val house = thread.createActorHouse()
        house.setActor(actor)
        house.setActorId(generator.getAndIncrement())
        house.setLB(lb)
        actor.setCtx(house)

        house.address
    }

    private def createActor(factory: ActorFactory[?], num: Int): (Address[?], Class[?]) = {
        if (num == 1) {
            val actor   = factory.create().asInstanceOf[AbstractActor[? <: Call]]
            val isIO    = actor.isInstanceOf[ChannelsActor[?]]
            val thread  = pool.next(isIO)
            val address = setActorContext(actor, thread)
            logger.debug(s"Created actor $actor")
            (address, actor.getClass)
        } else if (num == pool.size) {
            val address = pool.workers.map { thread =>
                val actor = factory.create().asInstanceOf[AbstractActor[? <: Call]]
                setActorContext(actor, thread, true)
            }
            val clz = address.head.house.actor.getClass
            (new RobinAddress[Call](address.asInstanceOf[Array[ActorAddress[Call]]], true), clz)
        } else if (num > 1) {
            val range   = (0 until num).toArray
            val actors  = range.map(_ => factory.create().asInstanceOf[AbstractActor[? <: Call]])
            val isIO    = actors.head.isInstanceOf[ChannelsActor[?]]
            val threads = pool.nexts(num, isIO)
            val address = range.map { index =>
                val actor  = actors(index)
                val thread = threads(index)
                setActorContext(actor, thread)
            }
            logger.debug(s"Created actors ${actors.mkString("Array(", ", ", ")")}")

            (new RobinAddress[Call](address.asInstanceOf[Array[ActorAddress[Call]]]), actors.head.getClass)
        } else throw new IllegalArgumentException("num must large than 0")
    }

    override private[core] def registerGlobalActor(definition: BeanDefinition): Unit = {
        val (address, clz) = createActor(definition.factory, definition.num)
        beanManager.register(clz, address, definition.qualifier, definition.primary)
        mountActor(address)
    }

    override def loadModule(module: Module): Unit = try {
        if (!initialize) {
            earlyModules.add(module)
        } else {
            logger.debug(s"Loading module $module")
            module.setSystem(this)
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
                        addr.house.mount()
                    }
            }

            module.onLoaded(this)
            logger.debug(s"Module $module load success!")
        }
    } catch {
        case t: Throwable => logger.error(s"Load module $module occur error with ", t)
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

    override private[core] def channelFactory: ChannelFactory = chFactory

    override private[core] def transportFactory = transFactory

    override def isBusy: Boolean = busy

    private def calculateBusy(): Unit = {
        val usage = memoryMXBean.getHeapMemoryUsage
        if (usage.getUsed.toFloat / usage.getMax.toFloat > 0.90 && usage.getMax - usage.getUsed < 100 * 1024 * 1024)
            busy = true
        else busy = false
    }

    override private[core] def registerLongLifeThreadLocal(threadLocal: ThreadLocal[?]): Unit =
        threadLocals.addOne(threadLocal)

    override private[core] def gc(): Unit = {
        val now  = System.currentTimeMillis()
        val last = gcTime.get()
        if (now - last > 1000 && gcTime.compareAndSet(last, now)) {
            System.gc()
            logger.trace("GC")
        }
    }

}
