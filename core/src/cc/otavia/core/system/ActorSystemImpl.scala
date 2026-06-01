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
import cc.otavia.core.channel.ChannelFactory
import cc.otavia.core.config.OtaviaConfig
import cc.otavia.core.interceptor.{InterceptorActor, Intercept}
import cc.otavia.core.ioc.{BeanDefinition, BeanRegistry, DuplicateModuleException, Module, ModuleDependencyException}
import cc.otavia.core.message.Call
import cc.otavia.core.pool.IndexedThreadLocal
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

final private[core] class ActorSystemImpl(val config: OtaviaConfig) extends ActorSystem {

    val name: String                                   = config.name
    private val actorThreadFactory: ActorThreadFactory = new ActorThreadFactory.DefaultActorThreadFactory

    actorThreadFactory.setSystem(this)

    @volatile private var initialize: Boolean = false

    private val earlyModules = new ConcurrentLinkedQueue[Module]()

    private val logger = Logger.getLogger(getClass, this)

    private val timerImpl = new TimerImpl(this)

    private val generator = new AtomicLong(1)

    private val beanRegistry = new BeanRegistry(this)

    private val loadedModules = mutable.HashMap[String, Module]()

    private val totals = new AtomicLong(0)

    private var mainActor: Address[MainActor.Args] = _

    private val transFactory: TransportFactory = TransportFactory.getTransportFactory(this)
    private val chFactory: ChannelFactory      = new ChannelFactory(transFactory)

    private val actorThreadPool: ActorThreadPool = new DefaultActorThreadPool(
      this,
      actorThreadFactory,
      config.system.actorThreadPoolSize
    )

    private val memoryMXBean: MemoryMXBean = ManagementFactory.getMemoryMXBean

    @volatile private var busy: Boolean = false

    private var memoryMonitor: Timeout = _

    if (config.system.memoryMonitor) {
        val duration = config.system.memoryMonitorDurationMs
        memoryMonitor =
            timer.internalTimer.newTimeout(_ => calculateBusy(), duration, MILLISECONDS, duration, MILLISECONDS)
    }

    private val systemMonitorTask      = new SystemMonitorTask(this)
    private var systemMonitor: Timeout = _

    if (config.system.systemMonitor) {
        val duration = config.system.systemMonitorDurationMs
        systemMonitor = timer.internalTimer.newTimeout(_ => doMonitor(), duration, MILLISECONDS, duration, MILLISECONDS)
    }

    if (config.system.printBanner) {
        println(s"${Console.YELLOW}${SystemInfo.logo()}${Console.RESET}")
        println(SystemInfo.info())
        println("\n")
    }

    private val react = transFactory.openReactor(this)

    private val gcTime = new AtomicLong(System.currentTimeMillis())

    private val threadLocals: mutable.Set[IndexedThreadLocal[?]] = mutable.HashSet.empty

    initialize = true

    loadEarlyModules()

    beanRegistry.freeze()

    private def loadEarlyModules(): Unit = while (!earlyModules.isEmpty) {
        val module = earlyModules.poll()
        this.loadModule(module)
    }

    private def doMonitor(): Unit = systemMonitorTask.run()

    override def initialized: Boolean = initialize

    override private[core] def pool: ActorThreadPool = actorThreadPool

    override private[core] def reactor = react

    override def timer: Timer = timerImpl

    override def shutdown(): Unit = {
        logger.info(s"Shutting down ActorSystem [$name]")
        pool.workers.foreach(_.shutdown())
    }

    override def defaultMaxFetchPerRunning: Int = config.system.maxFetchPerRunning

    override def defaultMaxBatchSize: Int = config.system.maxBatchSize

    // format: off
    override def buildActor[A <: Actor[? <: Call]](factory: ActorFactory[A], num: Int = 1,
        global: Boolean = false, qualifier: Option[String] = None, primary: Boolean = false
    ): Address[MessageOf[A]] = {
    // format: on
        val actorFactory   = factory.asInstanceOf[ActorFactory[?]]
        val (address, clz) = createActor(actorFactory, num)
        val finalAddress   = wrapWithInterceptors(address, clz)

        if (global) beanRegistry.register(clz, finalAddress, qualifier, primary)

        finalAddress.asInstanceOf[Address[MessageOf[A]]]
    }

    override private[core] def registerGlobalActor(definition: BeanDefinition): Unit = {
        val (address, clz) = createActor(definition.factory, definition.num)
        val finalAddress   = wrapWithInterceptors(address, clz)
        beanRegistry.register(clz, finalAddress, definition.qualifier, definition.primary)
    }

    override def intercept[M <: Call](
        target: Address[M],
        factories: Seq[Address[M] => InterceptorActor[M]]
    ): Address[M] = {
        // foldRight: last factory wraps target first (innermost),
        // first factory becomes outermost.
        factories.foldRight[Address[M]](target) { (factory, nextAddr) =>
            val interceptor = factory(nextAddr).asInstanceOf[AbstractActor[? <: Call]]
            val thread      = pool.next(false)
            val address     = setActorContext(interceptor, thread)
            mountActor(address)
            address.asInstanceOf[Address[M]]
        }
    }

    /** Check for @Intercept annotation and create interceptor chain if present. Mounts the target address
     *  (and any interceptors) and returns the outermost address.
     */
    private def wrapWithInterceptors(address: Address[?], clz: Class[?]): Address[?] = {
        clz.getAnnotation(classOf[Intercept]) match {
            case annot if annot != null && annot.value().nonEmpty =>
                createInterceptorChain(address, annot.value(), annot.perInstance())
            case _ =>
                mountActor(address)
                address
        }
    }

    private def createInterceptorChain(
        targetAddress: Address[?],
        interceptorClasses: Array[Class[? <: InterceptorActor[?]]],
        perInstance: Boolean
    ): Address[?] = {
        mountActor(targetAddress)

        targetAddress match {
            case robin: RobinAddress[?] if perInstance =>
                // perInstance=true + Robin: create one interceptor per target instance
                var currentAddr: Address[?] = robin
                var i = interceptorClasses.length - 1
                while (i >= 0) {
                    currentAddr = createPerInstanceInterceptors(interceptorClasses(i), currentAddr)
                    i -= 1
                }
                currentAddr
            case _ =>
                // Single target or perInstance=false: one interceptor wrapping the whole address
                interceptorClasses.foldRight(targetAddress: Address[?]) { (clazz, nextAddr) =>
                    val interceptor = createInterceptorInstance(clazz, nextAddr)
                    val thread      = pool.next(false)
                    val address     = setActorContext(interceptor, thread)
                    mountActor(address)
                    address
                }
        }
    }

    /** Create one interceptor per target instance, returning a new RobinAddress. */
    private def createPerInstanceInterceptors(
        clazz: Class[? <: InterceptorActor[?]],
        innerAddr: Address[?]
    ): Address[?] = {
        innerAddr match {
            case robin: RobinAddress[?] =>
                val addresses = robin.underlying.map { targetAddr =>
                    val interceptor = createInterceptorInstance(clazz, targetAddr)
                    val thread      = pool.next(false)
                    val address     = setActorContext(interceptor, thread)
                    mountActor(address)
                    address
                }
                new RobinAddress[Call](addresses.asInstanceOf[Array[ActorAddress[Call]]])
            case _ =>
                // Not a Robin, just create one interceptor
                val interceptor = createInterceptorInstance(clazz, innerAddr)
                val thread      = pool.next(false)
                val address     = setActorContext(interceptor, thread)
                mountActor(address)
                address
        }
    }

    /** Instantiate an interceptor class via reflection. Requires a public constructor with Address[_] parameter. */
    private def createInterceptorInstance(
        clazz: Class[? <: InterceptorActor[?]],
        nextAddr: Address[?]
    ): AbstractActor[? <: Call] = {
        try {
            val constructor = clazz.getConstructor(classOf[Address[_]])
            constructor.newInstance(nextAddr).asInstanceOf[AbstractActor[? <: Call]]
        } catch {
            case e: NoSuchMethodException =>
                throw new IllegalArgumentException(
                    s"Interceptor class [${clazz.getName}] must have a public constructor with an Address[_] parameter",
                    e
                )
            case e: Exception =>
                throw new RuntimeException(
                    s"Failed to create interceptor instance of [${clazz.getName}]",
                    e
                )
        }
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
        house.setLoadBalanced(lb)
        actor.setHouse(house)

        house.address
    }

    private def createActor(factory: ActorFactory[?], num: Int): (Address[?], Class[?]) = {
        if (num == 1) {
            val actor   = factory.newActor().asInstanceOf[AbstractActor[? <: Call]]
            val isIO    = actor.isInstanceOf[ChannelsActor[?]]
            val thread  = pool.next(isIO)
            val address = setActorContext(actor, thread)
            logger.debug(s"Created actor $actor")
            (address, actor.getClass)
        } else if (num == pool.size) {
            val address = pool.workers.map { thread =>
                val actor = factory.newActor().asInstanceOf[AbstractActor[? <: Call]]
                setActorContext(actor, thread, true)
            }
            val clz = address.head.house.actor.getClass
            (new RobinAddress[Call](address.asInstanceOf[Array[ActorAddress[Call]]], true), clz)
        } else if (num > 1) {
            val range   = (0 until num).toArray
            val actors  = range.map(_ => factory.newActor().asInstanceOf[AbstractActor[? <: Call]])
            val isIO    = actors.head.isInstanceOf[ChannelsActor[?]]
            val threads = pool.nexts(num, isIO)
            val address = range.map { index =>
                val actor  = actors(index)
                val thread = threads(index)
                setActorContext(actor, thread)
            }
            logger.debug(s"Created actor ${actors.mkString("Array(", ", ", ")")}")

            (new RobinAddress[Call](address.asInstanceOf[Array[ActorAddress[Call]]]), actors.head.getClass)
        } else throw new IllegalArgumentException("num must large than 0")
    }

    override def loadModule(module: Module): Unit = try {
        if (!initialize) {
            earlyModules.add(module)
        } else {
            logger.debug(s"Loading module [${module.name}]")

            if (loadedModules.contains(module.name))
                throw DuplicateModuleException(module.name, loadedModules(module.name).getClass.getName)

            for (depName <- module.dependencies) {
                if (!loadedModules.contains(depName))
                    throw ModuleDependencyException(module.name, depName, loadedModules.keys.toSeq)
            }

            val wasFrozen = beanRegistry.isFrozen
            if wasFrozen then beanRegistry.unfreeze()

            try {
                module.setSystem(this)
                val unmount = new ArrayBuffer[Address[?]](module.definitions.length)
                module.definitions.foreach { definition =>
                    val (address, clz) = createActor(definition.factory, definition.num)
                    unmount.addOne(address)
                    beanRegistry.register(clz, address, definition.qualifier, definition.primary)
                }
                unmount.foreach {
                    case address: ActorAddress[?] => address.house.mount()
                    case robinAddress: RobinAddress[?] =>
                        robinAddress.underlying.foreach { addr =>
                            addr.house.mount()
                        }
                }

                module.onLoaded(this)
                loadedModules(module.name) = module
                logger.debug(s"Module [${module.name}] load success!")
            } finally {
                if wasFrozen then beanRegistry.freeze()
            }
        }
    } catch {
        case t: Throwable => logger.error(s"Load module [${module.name}] occur error with ", t)
    }

    override def getAddress[M <: Call](
        clz: Class[? <: Actor[?]],
        qualifier: Option[String]
    ): Address[M] = {
        val address = qualifier match
            case Some(value) => beanRegistry.getBean(value, clz)
            case None        => beanRegistry.getBean(clz)

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
        SystemMonitor(name, pool.size, beanRegistry.count, threadMonitor)
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

    override private[core] def registerLongLifeThreadLocal(threadLocal: IndexedThreadLocal[?]): Unit =
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
