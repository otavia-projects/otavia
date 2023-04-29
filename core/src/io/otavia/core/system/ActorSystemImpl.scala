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
import io.otavia.core.address.*
import io.otavia.core.channel.ChannelFactory
import io.otavia.core.ioc.{BeanDefinition, BeanManager, Module}
import io.otavia.core.log4a.{DefaultLog4aModule, LogLevel}
import io.otavia.core.message.{Call, IdAllocator}
import io.otavia.core.reactor.BlockTaskExecutor
import io.otavia.core.reactor.aio.Submitter
import io.otavia.core.timer.Timer
import io.otavia.core.util.SystemPropertyUtil

import java.util.concurrent.atomic.AtomicLong
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
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
        if (num == 1) createActor0(factory)
        else if (num > 1) {
            val addrs = createActor0(factory, num)
            (new RobinAddress[Call](addrs._1.asInstanceOf[Array[Address[Call]]]), addrs._2)
        } else throw new IllegalArgumentException("num must large than 0")
    }

    private def createActor0(factory: ActorFactory[?]): (ActorAddress[?], Class[?]) = {
        val actor   = factory.create().asInstanceOf[AbstractActor[? <: Call]]
        val isIO    = actor.isInstanceOf[ChannelsActor[?]]
        val thread  = pool.next(isIO)
        val address = setActorContext0(actor, thread)
        (address, actor.getClass)
    }

    private def createActor0(factory: ActorFactory[?], num: Int): (Array[ActorAddress[?]], Class[?]) = {
        val range   = (0 until num).toArray
        val actors  = range.map(_ => factory.create().asInstanceOf[AbstractActor[? <: Call]])
        val isIO    = actors.head.isInstanceOf[ChannelsActor[?]]
        val threads = pool.nexts(num, isIO)
        val address = range.map { index =>
            val actor  = actors(index)
            val thread = threads(index)
            setActorContext0(actor, thread)
        }

        (address, actors.head.getClass)
    }

    override private[core] def registerGlobalActor(definition: BeanDefinition): Unit = {
        val (address, clz) = createActor(definition.factory, definition.num)
        beanManager.register(clz, address, definition.qualifier, definition.primary)
        mountActor(address)
    }

    override def loadModule(module: Module): Unit = try {
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

    override def serverChannelFactory: ChannelFactory = ???

    override def channelFactory: ChannelFactory = ???

    override def datagramChannelFactory: ChannelFactory = ???

    override def fileChannelFactory: ChannelFactory = ???

}
