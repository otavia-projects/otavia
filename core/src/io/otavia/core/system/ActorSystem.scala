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

import io.otavia.buffer.BufferAllocator
import io.otavia.core.actor.{Actor, ActorFactory, MainActor, MessageOf}
import io.otavia.core.address.Address
import io.otavia.core.channel.{Channel, ChannelFactory}
import io.otavia.core.ioc.{BeanDefinition, Module}
import io.otavia.core.message.*
import io.otavia.core.reactor.aio.Submitter
import io.otavia.core.reactor.{BlockTaskExecutor, Reactor}
import io.otavia.core.slf4a.LogLevel
import io.otavia.core.stack.BlockFuture
import io.otavia.core.system.monitor.SystemMonitor
import io.otavia.core.timer.Timer
import io.otavia.core.util.SystemPropertyUtil

import java.lang.management.MemoryUsage
import java.net.InetAddress
import scala.language.unsafeNulls
import scala.quoted
import scala.reflect.ClassTag

/** [[ActorSystem]] is a container of actor and channel group instance, a actor or channel group instance must be create
 *  by a actor system instance, actor instance in different actor system instance can not send message directly.
 *
 *  only [[Notice]] message can send to a actor or channel group out side actor and channel group
 */
trait ActorSystem {

    def initialed: Boolean

    /** [[io.otavia.core.channel.Channel]] io reactor of this actor system */
    private[core] def reactor: Reactor

    /** Timeout event dispatcher of this actor system */
    def timer: Timer

    def blockingExecutor: BlockTaskExecutor

    final def executeBlocking[V](task: () => V, owner: Channel): BlockFuture[V] =
        blockingExecutor.executeBlocking(task, owner)

    def aio: Submitter

    /** message id distributor usage for create [[Notice]] message */
    def distributor: IdAllocator

    /** A [[BufferAllocator]] which allocate heap memory. */
    def directAllocator: BufferAllocator

    /** A [[BufferAllocator]] which allocate heap memory. */
    def headAllocator: BufferAllocator

    /** log level for actor log system
     *
     *  ALL 7 > TRACE 6 > DEBUG 5 > INFO 4 > WARN 3 > ERROR 2 > FATAL 1 > OFF 0
     */
    def logLevel: LogLevel

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
    def buildActor[A <: Actor[? <: Call]](
        factory: ActorFactory[A],
        num: Int = 1,
        global: Boolean = false,
        qualifier: Option[String] = None,
        primary: Boolean = false
    ): Address[MessageOf[A]]

    private[core] def registerGlobalActor(entry: BeanDefinition): Unit

    def loadModule(module: Module): Unit

    def runMain[M <: MainActor](factory: ActorFactory[M], modules: Seq[Module] = Seq.empty): Unit

    /** IOC methods, developer can ues it by [[io.otavia.core.ioc.Injectable]] */
    def getAddress[M <: Call](
        clz: Class[? <: Actor[?]],
        qualifier: Option[String] = None,
        remote: Option[String] = None
    ): Address[M]

    // IO transport layer

    /** [[ChannelFactory]] for TCP socket channel. */
    def channelFactory: ChannelFactory

    def pool: ActorThreadPool

    final def threadPoolSize: Int = pool.size

    private[system] def setActorContext[A <: Actor[? <: Call]](actor: A, thread: ActorThread): Address[MessageOf[A]]

    def monitor(): SystemMonitor

    private[core] def getHeapMemoryUsage(): MemoryUsage

    def isBusy: Boolean

}

object ActorSystem {

    private val DEFAULT_SYSTEM_NAME = s"ActorSystem:${InetAddress.getLocalHost.getHostName}"

    val DEFAULT_MAX_TASKS_PER_RUN: Int =
        Math.max(1, SystemPropertyUtil.getInt("io.otavia.reactor.maxTaskPerRun", 1024 * 4))

    val DEFAULT_POOL_HOLDER_MAX_SIZE: Int =
        SystemPropertyUtil.getInt("io.otavia.pool.holder.maxSize", 1024)

    val DEFAULT_ACTOR_THREAD_POOL_SIZE: Int = Runtime.getRuntime.availableProcessors()

    def apply(): ActorSystem =
        new ActorSystemImpl(DEFAULT_SYSTEM_NAME, new ActorThreadFactory.DefaultActorThreadFactory)

    def apply(name: String): ActorSystem = new ActorSystemImpl(name, new ActorThreadFactory.DefaultActorThreadFactory)

}
