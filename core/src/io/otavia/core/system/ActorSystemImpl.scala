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
import io.otavia.core.actor.*
import io.otavia.core.address.Address
import io.otavia.core.channel.ChannelFactory
import io.otavia.core.message.{Call, IdAllocator}
import io.otavia.core.reactor.BlockTaskExecutor
import io.otavia.core.reactor.aio.Submitter
import io.otavia.core.timer.Timer
import io.otavia.core.util.SystemPropertyUtil

import java.util.concurrent.atomic.AtomicLong

class ActorSystemImpl(val name: String, val actorThreadFactory: ActorThreadFactory) extends ActorSystem {

    private val actorThreadPool: ActorThreadPool = new DefaultActorThreadPool(
      this,
      actorThreadFactory,
      SystemPropertyUtil.getInt("io.otavia.actor.thread.pool.size", ActorSystem.DEFAULT_ACTOR_THREAD_POOL_SIZE)
    )

    private val generator = new AtomicLong(1)

    override def pool: ActorThreadPool = actorThreadPool

    override private[core] def reactor = ???

    override def timer: Timer = ???

    override def blockingExecutor: BlockTaskExecutor = ???

    override def aio: Submitter = ???

    override def distributor: IdAllocator = ???

    override def directAllocator: BufferAllocator = ???

    override def headAllocator: BufferAllocator = ???

    override def logLevel: Int = ???

    override def shutdown(): Unit = ???

    override def defaultMaxFetchPerRunning: Int = ???

    override def defaultMaxBatchSize: Int = ???

    override def buildActor[A <: Actor[? <: Call]](
        args: Any*
    )(num: Int, ioc: Boolean = false, qualifier: Option[String] = None): Address[MessageOf[A]] = ???

    override def crateActor[A <: Actor[? <: Call]](
        factory: ActorFactory[A],
        num: Int = 1,
        ioc: Boolean = false,
        qualifier: Option[String] = None
    ): Address[MessageOf[A]] = {
        if (num == 1) {
            val actor   = factory.newActor().asInstanceOf[AbstractActor[? <: Call]]
            val thread  = pool.next(if (actor.isInstanceOf[ChannelsActor[?]]) true else false)
            val house   = thread.createActorHouse()
            val address = house.createActorAddress[MessageOf[A]]()
            val context = ActorContext(this, address, generator.getAndIncrement())

            actor.setCtx(context)
            house.setActor(actor)

            if (ioc) {
                // TODO: put to IOC manager
            }

            address
        } else {
            // TODO: batch create
            ???
        }

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
