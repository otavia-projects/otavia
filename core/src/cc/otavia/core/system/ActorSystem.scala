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

import cc.otavia.buffer.BufferAllocator
import cc.otavia.common.{Report, SystemPropertyUtil}
import cc.otavia.core.actor.{Actor, ActorFactory, MainActor, MessageOf}
import cc.otavia.core.address.Address
import cc.otavia.core.cache.ThreadLocal
import cc.otavia.core.channel.{Channel, ChannelFactory}
import cc.otavia.core.ioc.{BeanDefinition, Module}
import cc.otavia.core.message.*
import cc.otavia.core.reactor.Reactor
import cc.otavia.core.slf4a.LogLevel
import cc.otavia.core.system.monitor.SystemMonitor
import cc.otavia.core.timer.Timer

import java.lang.management.MemoryUsage
import java.net.InetAddress
import scala.language.unsafeNulls

/** [[ActorSystem]] is a container of actor and channel group instance, a actor or channel group instance must be create
 *  by a actor system instance, actor instance in different actor system instance can not send message directly.
 *
 *  only [[Notice]] message can send to a actor or channel group out side actor and channel group
 */
trait ActorSystem {

    def initialized: Boolean

    /** [[cc.otavia.core.channel.Channel]] io reactor of this actor system */
    private[core] def reactor: Reactor

    /** Timeout event dispatcher of this actor system */
    def timer: Timer

    def shutdown(): Unit

    def defaultMaxFetchPerRunning: Int

    def defaultMaxBatchSize: Int

    /** Create some new actor instance, and return the address of those actor.
     *  @param factory
     *    factory for create actor instance
     *  @param num
     *    number of actor instance to create
     *  @param global
     *    whether register this address to ioc manager of actor system
     *  @param qualifier
     *    qualifier of ioc instance if [[ioc]] is true
     *  @tparam A
     *    type of actor
     *  @return
     */
    // format: off
    def buildActor[A <: Actor[? <: Call]](factory: ActorFactory[A], num: Int = 1, 
                                          global: Boolean = false, qualifier: Option[String] = None,
                                          primary: Boolean = false): Address[MessageOf[A]]
    // format: on

    private[core] def registerGlobalActor(entry: BeanDefinition): Unit

    def loadModule(module: Module): Unit

    def runMain[M <: MainActor](factory: ActorFactory[M], modules: Seq[Module] = Seq.empty): Unit

    /** IOC methods, developer can ues it by [[cc.otavia.core.actor.Actor]] */
    // format: off
    private[otavia] def getAddress[M <: Call](clz: Class[? <: Actor[?]], qualifier: Option[String] = None, 
                                              remote: Option[String] = None): Address[M]
    // format: on

    // IO transport layer

    /** [[ChannelFactory]] for TCP socket channel. */
    private[core] def channelFactory: ChannelFactory

    private[core] def pool: ActorThreadPool

    final def actorWorkerSize: Int = pool.size

    def monitor(): SystemMonitor

    def isBusy: Boolean

    private[core] def gc(): Unit

    private[core] def registerLongLifeThreadLocal(threadLocal: ThreadLocal[?]): Unit

}

object ActorSystem {

    private var system: ActorSystem = _

    private val DEFAULT_SYSTEM_NAME = s"ActorSystem:${InetAddress.getLocalHost.getHostName}"

    val DEFAULT_MAX_TASKS_PER_RUN: Int =
        Math.max(1, SystemPropertyUtil.getInt("cc.otavia.reactor.maxTaskPerRun", 16))

    val DEFAULT_POOL_HOLDER_MAX_SIZE: Int =
        SystemPropertyUtil.getInt("cc.otavia.pool.holder.maxSize", 256)

    private val DEFAULT_ACTOR_THREAD_POOL_SIZE: Int = Runtime.getRuntime.availableProcessors()
    val ACTOR_THREAD_POOL_SIZE: Int = {
        if (SystemPropertyUtil.get("cc.otavia.actor.worker.size").nonEmpty)
            SystemPropertyUtil.getInt("cc.otavia.actor.worker.size", ActorSystem.DEFAULT_ACTOR_THREAD_POOL_SIZE)
        else if (SystemPropertyUtil.get("cc.otavia.actor.worker.ratio").nonEmpty)
            (SystemPropertyUtil.getFloat("cc.otavia.actor.worker.ratio", 1.0) * DEFAULT_ACTOR_THREAD_POOL_SIZE).toInt
        else DEFAULT_ACTOR_THREAD_POOL_SIZE
    }

    private val DEFAULT_MEMORY_MONITOR: Boolean = true
    val MEMORY_MONITOR: Boolean =
        SystemPropertyUtil.getBoolean("cc.otavia.system.memory.monitor", DEFAULT_MEMORY_MONITOR)

    private val DEFAULT_MEMORY_MONITOR_DURATION: Int = 20
    val MEMORY_MONITOR_DURATION: Int =
        SystemPropertyUtil.getInt("cc.otavia.system.memory.monitor.duration", DEFAULT_MEMORY_MONITOR_DURATION)

    private val DEFAULT_MEMORY_OVER_SLEEP: Int = 40
    val MEMORY_OVER_SLEEP: Int =
        SystemPropertyUtil.getInt("cc.otavia.system.memory.over.sleep", DEFAULT_MEMORY_OVER_SLEEP)

    private val DEFAULT_SYSTEM_MONITOR: Boolean = false
    val SYSTEM_MONITOR: Boolean = SystemPropertyUtil.getBoolean("cc.otavia.system.monitor", DEFAULT_SYSTEM_MONITOR)

    private val DEFAULT_SYSTEM_MONITOR_DURATION: Int = 10
    val SYSTEM_MONITOR_DURATION: Int =
        SystemPropertyUtil.getInt("cc.otavia.system.monitor.duration", DEFAULT_SYSTEM_MONITOR_DURATION)

    private val DEFAULT_PRINT_BANNER: Boolean = true
    val PRINT_BANNER: Boolean = SystemPropertyUtil.getBoolean("cc.otavia.system.banner", DEFAULT_PRINT_BANNER)

    // buffer setting
    private val DEFAULT_PAGE_SIZE: Int        = 2
    private val ENABLE_PAGE_SIZES: Array[Int] = Array(1, 2, 4, 8, 16)
    private val K: Int                        = 1024

    val PAGE_SIZE: Int = {
        val size = SystemPropertyUtil.getInt("cc.otavia.buffer.page.size", DEFAULT_PAGE_SIZE)
        if (ENABLE_PAGE_SIZES.contains(size)) size * K
        else {
            Report.report(
              s"cc.otavia.buffer.page.size is set to $size, but only support ${ENABLE_PAGE_SIZES
                      .mkString("[", ", ", "]")}, set to default ${DEFAULT_PAGE_SIZE * K} ",
              "Buffer"
            )
            DEFAULT_PAGE_SIZE * K
        }
    }

    private val DEFAULT_ALLOCATOR_MIN_CACHE_SIZE: Int = 8
    val ALLOCATOR_MIN_CACHE_SIZE: Int = {
        val size = SystemPropertyUtil.getInt("cc.otavia.buffer.allocator.cache.min", DEFAULT_ALLOCATOR_MIN_CACHE_SIZE)
        if (size < 1) {
            Report.report(
              s"cc.otavia.buffer.allocator.cache.min can't set $size, it must large than 1, set $DEFAULT_ALLOCATOR_MIN_CACHE_SIZE default"
            )
            DEFAULT_ALLOCATOR_MIN_CACHE_SIZE
        } else size
    }

    private val DEFAULT_ALLOCATOR_MAX_CACHE_SIZE: Int = 10240
    val ALLOCATOR_MAX_CACHE_SIZE: Int = {
        val size = SystemPropertyUtil.getInt("cc.otavia.buffer.allocator.cache.max", DEFAULT_ALLOCATOR_MAX_CACHE_SIZE)
        if (size < ALLOCATOR_MIN_CACHE_SIZE) {
            Report.report(
              s"cc.otavia.buffer.allocator.cache.max can't set $size, it must large than [cc.otavia.buffer.allocator.cache.min = $ALLOCATOR_MIN_CACHE_SIZE], set $ALLOCATOR_MIN_CACHE_SIZE default"
            )
            DEFAULT_ALLOCATOR_MAX_CACHE_SIZE
        } else size
    }

    private val DEFAULT_AGGRESSIVE_GC: Boolean = true
    val AGGRESSIVE_GC: Boolean = SystemPropertyUtil.getBoolean("cc.otavia.system.gc.aggressive", DEFAULT_AGGRESSIVE_GC)

    /** Create an [[ActorSystem]] instance with default name [[DEFAULT_SYSTEM_NAME]].
     *  @return
     *    [[ActorSystem]] instance.
     */
    def apply(): ActorSystem = this.synchronized {
        if (system == null) {
            system = new ActorSystemImpl(DEFAULT_SYSTEM_NAME, new ActorThreadFactory.DefaultActorThreadFactory)
        } else
            throw new IllegalStateException(
              "Can't create multiple ActorSystem instances within the same JVM instance, use method global to get the only instance that has been created."
            )
        system
    }

    /** Create an [[ActorSystem]] instance with [[name]].
     *  @param name
     *    name of the [[ActorSystem]].
     *  @return
     *    [[ActorSystem]] instance.
     */
    def apply(name: String): ActorSystem = this.synchronized {
        if (system == null) {
            system = new ActorSystemImpl(name, new ActorThreadFactory.DefaultActorThreadFactory)
        } else
            throw new IllegalStateException(
              "Can't create multiple ActorSystem instances within the same JVM instance, use method global to get the only instance that has been created."
            )
        system
    }

    /** Get the only instance that has been created, if not created, creating it and return it. */
    def global: ActorSystem = this.synchronized { if (system == null) apply() else system }

}
