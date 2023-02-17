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

package io.otavia.core.actor

import io.otavia.core.actor.Actor.{ASK_TYPE, MessageType, NOTICE_TYPE, REPLY_TYPE}
import io.otavia.core.address.Address
import io.otavia.core.message.*
import io.otavia.core.reactor.{Event, Reactor}
import io.otavia.core.stack.*
import io.otavia.core.system.ActorSystem
import io.otavia.core.timer.Timer
import io.otavia.core.util.TimerService

import scala.reflect.{ClassTag, TypeTest, classTag}

/** base class of IO & Actor model, it has two subclass
 *    1. [[io.otavia.core.actor.StateActor]]: general actor
 *    1. [[io.otavia.core.actor.ChannelsActor]]: socket group, a actor which can handle io event
 *
 *  @tparam M
 *    the type of message of this actor can handle
 */
trait Actor[M <: Ask[?] | Notice] {

    /** message id distributor */
    given idAllocator: IdAllocator = new IdAllocator()

    given actor: this.type = this

    /** whether this actor is a batch actor, if override it to true, actor system will dispatch seq message to
     *  receiveBatchXXX method
     */
    val batchable: Boolean = false

    val maxFetchPerRunning: Int = system.defaultMaxFetchPerRunning

    // system context, these method can only used after actor instance mount to actor system

    private var ctx: ActorContext = _

    /** context of this actor */
    def context: ActorContext = ctx

    /** This method will called by [[ActorSystem]] when actor mount to actor system, when a actor is creating, the
     *  [[ActorSystem]] will create a [[ActorContext]]. When mount actor instance to actor system, use this method to
     *  set system context information.
     *  @param context
     *    the system context of this actor
     */
    private[core] def setCtx(context: ActorContext): Unit = {
        ctx = context
        idAllocator.setActorId(context.actorId)
        idAllocator.setActorAddress(context.address)
    }

    /** The ActorSystem of this actor instance is running
     *
     *  @return
     *    ActorSystem
     */
    def system: ActorSystem = context.system

    /** The unique id of this actor distributed by [[ActorSystem]], when a actor instance is mounted to a
     *  [[ActorSystem]], the actor system will distribute a unique id to the instance.
     *
     *  @return
     *    id number
     */
    def actorId: Long = context.actorId

    /** self address of this actor instance
     *
     *  @return
     *    self address
     */
    def self: Address[M] = context.address.asInstanceOf[Address[M]]

    // method for receive message

    /** receive notice message from other actor
     *  @param notice
     *    notice message
     */
    private[core] def receiveNotice(notice: Notice): Unit

    /** receive ask message from other actor
     *  @param ask
     *    ask message
     */
    private[core] def receiveAsk(ask: Ask[?]): Unit

    /** receive reply message from other actor
     *  @param reply
     *    reply message
     */
    private[core] def receiveReply(reply: Reply): Unit

    /** Receive IO event from [[Reactor]] or timeout event from [[Timer]]
     *  @param event
     *    IO/timeout event
     */
    private[core] def receiveEvent(event: Event): Unit

    // actor life cycle hook method

    /** actor system will call this method after this actor crated and before mount to actor system */
    def afterCreate(): Unit = {}

    /** When a actor instance is mounted to actor system, this method will call by actor system */
    def afterMount(): Unit = {}

    /** Actor system call this method before call restart method */
    def beforeRestart(): Unit = {}

    /** When this actor occur not handled exception, the actor system will call this method, if user actor do not
     *  implement this method, the actor will dead.
     */
    def restart(): Unit =
        throw new NotImplementedError(getClass.getName.nn + ": an implementation is missing")

    /** Actor system call this method after call restart */
    def afterRestart(): Unit = {}

    /** if restart method throw NotImplementedError, actor system will call this method and mark the actor instance
     *  dead, and release is resource
     */
    def beforeStop(): Unit = {}

}

object Actor {

    opaque type MessageType = Byte
    val NOTICE_TYPE: MessageType = 0
    val ASK_TYPE: MessageType    = 1
    val REPLY_TYPE: MessageType  = 2

}
