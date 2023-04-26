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

import io.netty5.buffer.BufferAllocator
import io.netty5.util.internal.SystemPropertyUtil
import io.otavia.core.actor.{Actor, ActorFactory, MainActor, MessageOf}
import io.otavia.core.address.Address
import io.otavia.core.channel.{Channel, ChannelFactory}
import io.otavia.core.log4a.LogLevel
import io.otavia.core.message.*
import io.otavia.core.reactor.aio.Submitter
import io.otavia.core.reactor.{BlockTaskExecutor, Event, Reactor}
import io.otavia.core.stack.BlockFuture
import io.otavia.core.timer.Timer

import java.net.InetAddress
import scala.language.unsafeNulls
import scala.quoted

/** [[ActorSystem]] is a container of actor and channel group instance, a actor or channel group instance must be create
 *  by a actor system instance, actor instance in different actor system instance can not send message directly.
 *
 *  only [[Notice]] message can send to a actor or channel group out side actor and channel group
 */
trait ActorSystem {

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
     *  @param ioc
     *    whether register this address to ioc manager of actor system
     *  @param qualifier
     *    qualifier of ioc instance if [[ioc]] is true
     *  @tparam A
     *    type of actor
     *  @return
     */
    def crateActor[A <: Actor[? <: Call]](
        factory: ActorFactory[A],
        num: Int = 1,
        ioc: Boolean = false,
        qualifier: Option[String] = None
    ): Address[MessageOf[A]]

    def runMain[M <: MainActor](factory: ActorFactory[M]): Unit

    /** IOC methods, developer can ues it by [[io.otavia.core.ioc.Injectable]] */
    private[core] def getAddress[M <: Call](
        clz: Class[? <: Actor[?]],
        qualifier: Option[String] = None,
        remote: Option[String] = None
    ): Address[M]

    // IO transport layer

    /** [[ChannelFactory]] for TCP server channel. */
    def serverChannelFactory: ChannelFactory

    /** [[ChannelFactory]] for TCP socket channel. */
    def channelFactory: ChannelFactory

    /** [[ChannelFactory]] for UDP socket channel. */
    def datagramChannelFactory: ChannelFactory

    /** [[ChannelFactory]] for file */
    def fileChannelFactory: ChannelFactory

    def pool: ActorThreadPool

    final def threadPoolSize: Int = pool.size

    private[system] def mountActor[A <: Actor[? <: Call]](actor: A, thread: ActorThread): Address[MessageOf[A]]

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
