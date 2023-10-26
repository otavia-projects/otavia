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

package cc.otavia.core.timer

import cc.otavia.core.actor.{AbstractActor, Actor}
import cc.otavia.core.address.{Address, EventableAddress}
import cc.otavia.core.cache.{ResourceTimer, TimeoutResource}
import cc.otavia.core.channel.Channel
import cc.otavia.core.message.TimeoutEvent
import cc.otavia.core.reactor.Reactor
import cc.otavia.core.stack.TimeoutEventFuture
import cc.otavia.core.system.ActorSystem
import cc.otavia.core.system.monitor.TimerMonitor
import cc.otavia.core.timer.Timer.*

import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.duration.{MILLISECONDS, TimeUnit}

/** [[Timer]] can generate timeout event. */
trait Timer {

    /** Generate a unique id for a new [[TimeoutTask]] */
    private[timer] def nextRegisterId(): Long

    /** The [[ActorSystem]] of this [[Timer]] belong. */
    private[timer] def system: ActorSystem

    private[core] def internalTimer: InternalTimer

    private[core] def monitor(): TimerMonitor

    /** API for [[Actor]] to register timeout event to [[Timer]].
     *
     *  @param trigger
     *    Timeout event trigger.
     *  @param address
     *    [[Address]] of this [[TimeoutTrigger]] belong to. The timeout event of the [[TimeoutTrigger]] is send to this
     *    address.
     *  @param attach
     *    optional attachment object
     *  @return
     *    Register id of [[ReactorTimerTask]], [[cc.otavia.core.actor.Actor]] can use this id to cancel this trigger by
     *    [[cancelTimerTask]]
     */
    def registerActorTimeout(trigger: TimeoutTrigger, address: EventableAddress, attach: Option[AnyRef] = None): Long

    /** API for [[Channel]] to register timeout event to [[Timer]].
     *
     *  @param trigger
     *    Timeout event trigger.
     *  @param channel
     *    Channel
     *  @return
     *    Register id of [[ReactorTimerTask]], [[cc.otavia.core.actor.Actor]] can use this id to cancel this trigger by
     *    [[cancelTimerTask]]
     */
    def registerChannelTimeout(trigger: TimeoutTrigger, channel: Channel): Long

    /** API for [[TimeoutResource]] to register timeout event to [[Timer]].
     *
     *  @param trigger
     *    Timeout event trigger.
     *  @param address
     *    [[Address]] of this [[TimeoutTrigger]] belong to. The timeout event of the [[TimeoutTrigger]] is send to this
     *    address.
     *  @param resource
     *    time-out [[TimeoutResource]]
     *  @return
     */
    private[core] def registerResourceTimeout(
        trigger: TimeoutTrigger,
        address: EventableAddress,
        resource: ResourceTimer
    ): Long

    /** API for [[Actor]] to register ask message timeout.
     *  @param trigger
     *    Timeout event trigger.
     *  @param sender
     *    [[Actor]] which send time-out ask message.
     *  @return
     *    Register id of ReactorTimerTask, Actor can use this id to cancel this trigger by cancelTimerTask.
     */
    private[core] def registerAskTimeout(trigger: TimeoutTrigger, sender: EventableAddress, askId: Long): Long

    final def sleepStack(future: TimeoutEventFuture, delay: Long, unit: TimeUnit = MILLISECONDS)(using
        actor: AbstractActor[?]
    ): TimeoutEventFuture = {
        val aid = actor.generateSendMessageId()
        actor.attachStack(aid, future)
        registerAskTimeout(TimeoutTrigger.DelayTime(delay, unit), actor.self, aid)
        future
    }

    /** Update an existed [[TimeoutTrigger]].
     *  @param trigger
     *    The new [[TimeoutTrigger]] for update.
     *  @param registerId
     *    the old register id.
     */
    def updateTimerTask(trigger: TimeoutTrigger, registerId: Long): Unit

    /** API for [[cc.otavia.core.actor.Actor]] to cancel timeout event
     *
     *  @param registerId
     *    Register id of [[ReactorTimerTask]] to cancel.
     */
    def cancelTimerTask(registerId: Long): Unit

}

object Timer {

    val INVALID_TIMEOUT_REGISTER_ID: Long = Long.MinValue

}
