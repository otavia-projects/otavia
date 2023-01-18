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
import io.otavia.core.actor.{Actor, ActorFactory, MessageOf}
import io.otavia.core.address.Address
import io.otavia.core.channel.ChannelFactory
import io.otavia.core.message.{Ask, IdAllocator, Message, Notice}
import io.otavia.core.reactor.{Event, Reactor}
import io.otavia.core.timer.Timer

import scala.quoted

/** [[ActorSystem]] is a container of actor and channel group instance, a actor or channel group instance must be create
 *  by a actor system instance, actor instance in different actor system instance can not send message directly.
 *
 *  only [[Notice]] message can send to a actor or channel group out side actor and channel group
 */
trait ActorSystem {

    /** [[io.otavia.core.channel.Channel]] io reactor of this actor system */
    private[core] val reactor: Reactor

    /** Timeout event dispatcher of this actor system */
    val timer: Timer

    /** message id distributor usage for create [[Notice]] message */
    val distributor: IdAllocator

    /** byte buffer pool */
    val allocator: BufferAllocator

    /** log level for actor log system
     *
     *  ALL 7 > TRACE 6 > DEBUG 5 > INFO 4 > WARN 3 > ERROR 2 > FATAL 1 > OFF 0
     */
    val logLevel: Int

    def shutdown(): Unit

    def defaultMaxFetchPerRunning: Int

    def defaultMaxBatchSize: Int

    def buildActor[A <: Actor[_]](args: Any*)(num: Int = 1): Address[MessageOf[A]]
    def crateActor[A <: Actor[_]](factory: ActorFactory[A], num: Int = 1): Address[MessageOf[A]]

    /** IOC methods, developer can ues it by [[io.otavia.core.ioc.Injectable]] */
    private[core] def getAddress[M <: Ask[?] | Notice](
        clz: Class[_ <: Actor[_]],
        qualifier: Option[String] = None
    ): Address[M]

    // IO transport layer

    /** [[ChannelFactory]] for TCP server channel. */
    def serverChannelFactory: ChannelFactory

    /** [[ChannelFactory]] for TCP socket channel. */
    def channelFactory: ChannelFactory

    /** [[ChannelFactory]] for UDP socket channel. */
    def datagramChannelFactory: ChannelFactory

}
