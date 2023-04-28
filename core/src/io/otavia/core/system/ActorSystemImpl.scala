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
import io.otavia.BuildInfo
import io.otavia.core.actor.*
import io.otavia.core.address.{Address, RobinAddress}
import io.otavia.core.channel.ChannelFactory
import io.otavia.core.ioc.{BeanEntry, BeanManager, Module}
import io.otavia.core.log4a.{DefaultLog4aModule, LogLevel}
import io.otavia.core.message.{Call, IdAllocator}
import io.otavia.core.reactor.BlockTaskExecutor
import io.otavia.core.reactor.aio.Submitter
import io.otavia.core.timer.Timer
import io.otavia.core.util.SystemPropertyUtil

import java.util.concurrent.atomic.AtomicLong
import scala.language.unsafeNulls
import scala.reflect.{ClassTag, classTag}

class ActorSystemImpl(val name: String, val actorThreadFactory: ActorThreadFactory) extends ActorSystem {

    actorThreadFactory.setSystem(this)

    private val actorThreadPool: ActorThreadPool = new DefaultActorThreadPool(
      this,
      actorThreadFactory,
      SystemPropertyUtil.getInt("io.otavia.actor.thread.pool.size", ActorSystem.DEFAULT_ACTOR_THREAD_POOL_SIZE)
    )

    private val generator = new AtomicLong(1)

    private val beanManager = new BeanManager(this)

    private val logLvl: LogLevel = SystemPropertyUtil
        .get("io.otavia.actor.log.level")
        .map(str => LogLevel.valueOf(str.trim.toUpperCase))
        .getOrElse(LogLevel.INFO)

    private var mainActor: Address[MainActor.Args] = _

    private val direct = BufferAllocator.offHeapPooled()
    private val heap   = BufferAllocator.onHeapPooled()

    println(SystemInfo.logo())
    println(SystemInfo.info())

    override def pool: ActorThreadPool = actorThreadPool

    override private[core] def reactor = ???

    override def timer: Timer = ???

    override def blockingExecutor: BlockTaskExecutor = ???

    override def aio: Submitter = ???

    override def distributor: IdAllocator = ???

    override def directAllocator: BufferAllocator = direct

    override def headAllocator: BufferAllocator = heap

    override def logLevel: LogLevel = logLvl

    override def shutdown(): Unit = ???

    override def defaultMaxFetchPerRunning: Int = ???

    override def defaultMaxBatchSize: Int = ???

    override def buildActor[A <: Actor[? <: Call]](
        factory: ActorFactory[A],
        num: Int = 1,
        global: Boolean = false,
        qualifier: Option[String] = None
    ): Address[MessageOf[A]] = {
        if (num == 1) {
            val actor     = factory.create().asInstanceOf[AbstractActor[? <: Call]]
            val isIoActor = if (actor.isInstanceOf[ChannelsActor[?]]) true else false
            val thread    = pool.next(isIoActor)
            val address   = mountActor(actor, thread)

            if (global) {
//                beanManager.register(actor.getClass, qualifier)
            }
            address
        } else {
            val range     = (9 until num).toArray
            val actors    = range.map(_ => factory.create().asInstanceOf[AbstractActor[? <: Call]])
            val isIoActor = if (actors.head.isInstanceOf[ChannelsActor[?]]) true else false
            val threads   = pool.nexts(num, isIoActor)

            val address = range.map { index =>
                val actor  = actors(index)
                val thread = threads(index)
                mountActor(actor, thread)
            }

            new RobinAddress[MessageOf[A]](address)
        }
    }

    final private[system] def mountActor[A <: Actor[? <: Call]](
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

    override private[core] def registerGlobalActor(
        clz: Class[? <: Actor[? <: Call]],
        factory: ActorFactory[?],
        num: Int,
        qualifier: Option[String] = None,
        primary: Boolean = false
    ): Unit = {
        beanManager.register(clz, factory, num, qualifier, primary)
    }

    override private[core] def registerGlobalActor(entry: BeanEntry): Unit = beanManager.register(entry)

    override def loadModule(module: Module): Unit = try {
        module.load(this)
    } catch {
        case t: Throwable => // TODO: log
    }

    override def runMain[M <: MainActor](
        factory: ActorFactory[M],
        modules: Seq[Module] = Seq(new DefaultLog4aModule)
    ): Unit = {
        modules.foreach(m => loadModule(m))
        val address = this.buildActor(factory)
        mainActor = address
    }

    override private[core] def getAddress[M <: Call](
        clz: Class[? <: Actor[?]],
        qualifier: Option[String],
        remote: Option[String]
    ) = ???

    override def serverChannelFactory: ChannelFactory = ???

    override def channelFactory: ChannelFactory = ???

    override def datagramChannelFactory: ChannelFactory = ???

    override def fileChannelFactory: ChannelFactory = ???

}
