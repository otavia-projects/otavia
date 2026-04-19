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

import cc.otavia.core.actor.{Actor, ActorFactory, MessageOf}
import cc.otavia.core.address.Address
import cc.otavia.core.cache.ThreadLocal
import cc.otavia.core.channel.ChannelFactory
import cc.otavia.core.config.OtaviaConfig
import cc.otavia.core.ioc.{BeanDefinition, Module}
import cc.otavia.core.message.*
import cc.otavia.core.reactor.Reactor
import cc.otavia.core.system.monitor.SystemMonitor
import cc.otavia.core.timer.Timer
import cc.otavia.core.transport.TransportFactory

import java.net.InetAddress
import scala.language.unsafeNulls

/** [[ActorSystem]] is a container of actor and channel group instance, a actor or channel group instance must be create
 *  by a actor system instance, actor instance in different actor system instance can not send message directly.
 *
 *  only [[Notice]] message can send to a actor or channel group out side actor and channel group
 */
trait ActorSystem {

    def initialized: Boolean

    /** The immutable configuration for this actor system. */
    def config: OtaviaConfig

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

    /** IOC methods, developer can ues it by [[cc.otavia.core.actor.Actor]] */
    // format: off
    private[otavia] def getAddress[M <: Call](clz: Class[? <: Actor[?]], qualifier: Option[String] = None,
                                              remote: Option[String] = None): Address[M]
    // format: on

    // IO transport layer

    /** [[ChannelFactory]] for TCP socket channel. */
    private[core] def channelFactory: ChannelFactory

    private[core] def transportFactory: TransportFactory

    private[core] def pool: ActorThreadPool

    final def actorWorkerSize: Int = pool.size

    def monitor(): SystemMonitor

    def isBusy: Boolean

    private[core] def gc(): Unit

    private[core] def registerLongLifeThreadLocal(threadLocal: ThreadLocal[?]): Unit

}

object ActorSystem {

    private var system: ActorSystem = _

    /** Create an [[ActorSystem]] instance with default configuration. */
    def apply(): ActorSystem = this.synchronized {
        if (system == null) {
            system = new ActorSystemImpl(OtaviaConfig())
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
            system = new ActorSystemImpl(OtaviaConfig(name = name))
        } else
            throw new IllegalStateException(
              "Can't create multiple ActorSystem instances within the same JVM instance, use method global to get the only instance that has been created."
            )
        system
    }

    /** Create an [[ActorSystem]] instance with a custom [[OtaviaConfig]]. */
    def apply(config: OtaviaConfig): ActorSystem = this.synchronized {
        if (system == null) {
            system = new ActorSystemImpl(config)
        } else
            throw new IllegalStateException(
              "Can't create multiple ActorSystem instances within the same JVM instance, use method global to get the only instance that has been created."
            )
        system
    }

    /** Get the only instance that has been created, if not created, creating it and return it. */
    def global: ActorSystem = this.synchronized { if (system == null) apply() else system }

}
