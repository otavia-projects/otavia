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

package cc.otavia.core.actor

import cc.otavia.core.actor.Actor.*
import cc.otavia.core.address.Address
import cc.otavia.core.message.*
import cc.otavia.core.system.ActorSystem
import cc.otavia.core.timer.Timer

import scala.language.unsafeNulls
import scala.reflect.{ClassTag, classTag}

/** Base trait of the actor model. It has two concrete subclasses:
 *    1. [[cc.otavia.core.actor.StateActor]]: pure business logic actor
 *    1. [[cc.otavia.core.actor.ChannelsActor]]: IO-capable actor that manages Channel instances
 *
 *  @tparam M
 *    the type of messages this actor can handle
 */
trait Actor[+M <: Call] {

    protected given actor: this.type = this

    /** Context of this actor. This method can only used after actor instance mount to actor system */
    def context: ActorContext

    /** The ActorSystem of this actor instance is running
     *
     *  @return
     *    ActorSystem
     */
    final def system: ActorSystem = context.system

    final def timer: Timer = system.timer

    /** The unique id of this actor distributed by [[ActorSystem]], when a actor instance is mounted to a
     *  [[ActorSystem]], the actor system will distribute a unique id to the instance.
     *
     *  @return
     *    id number
     */
    final def actorId: Long = context.actorId

    // actor life cycle hook method

    /** When a actor instance is mounted to actor system, this method will call by actor system */
    protected def afterMount(): Unit = {}

    /** Actor system call this method before call restart method */
    protected def beforeRestart(): Unit = {}

    /** Called during the restart process to reset actor state. Override to clear internal state before resuming
     *  message processing after an exception.
     */
    protected def restart(): Unit = {}

    /** Actor system call this method after call restart */
    protected def afterRestart(): Unit = {}

    /** user actor override this to control whether restart when occur exception */
    protected def noticeExceptionStrategy: ExceptionStrategy = ExceptionStrategy.Restart

    /** Strategy for handling exceptions thrown during Ask processing. Unlike Notice, the caller always receives the
     *  exception via [[ExceptionMessage]]. This strategy controls what happens to the actor itself.
     */
    protected def askExceptionStrategy: ExceptionStrategy = ExceptionStrategy.Ignore

    /** Override to mark specific message types as barrier calls. When a barrier message is received, the actor
     *  pauses processing of subsequent asks/notices until the barrier is resolved (all pending stacks complete).
     *  This prevents message reordering within the actor.
     *
     *  @param call
     *    the incoming message to check
     *  @return
     *    true if this message should act as a barrier
     */
    protected def isBarrierCall(call: Call): Boolean = false

    // --- Scheduling configuration ---

    def maxFetchPerRunning: Int = system.defaultMaxFetchPerRunning

    def nice: Int = 8

    /** Whether this actor supports batch message processing. When true, the [[ActorSystem]] dispatches multiple
     *  messages in bulk rather than individually.
     */
    def batchable: Boolean = false

    /** Maximum number of messages per batch. Used by the scheduling system. */
    def maxBatchSize: Int = system.defaultMaxBatchSize

    /** Filter function for batching [[Notice]] messages. Return true to include in batch. */
    val batchNoticeFilter: Notice => Boolean = _ => true

    /** Filter function for batching [[Ask]] messages. Return true to include in batch. */
    val batchAskFilter: Ask[?] => Boolean = _ => true

    // --- Timeout hook ---

    /** Handle a user-registered timeout event.
     *
     *  @param timeoutEvent
     *    the timeout event
     */
    protected def handleActorTimeout(timeoutEvent: TimeoutEvent): Unit = {}

    final def autowire[A <: Actor[?]: ClassTag](
        qualifier: Option[String] = None,
        remote: Option[String] = None
    ): Address[MessageOf[A]] =
        system.getAddress(classTag[A].runtimeClass.asInstanceOf[Class[? <: Actor[?]]], qualifier, remote)

    final def autowire[A <: Actor[?]: ClassTag](qualifier: String): Address[MessageOf[A]] = autowire(Some(qualifier))

}

object Actor {

    opaque type MessageType = Byte
    val NOTICE_TYPE: MessageType = 0
    val ASK_TYPE: MessageType    = 1
    val REPLY_TYPE: MessageType  = 2

}
