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

import cc.otavia.common.SystemPropertyUtil
import cc.otavia.core.actor.Actor.*
import cc.otavia.core.address.Address
import cc.otavia.core.message.*
import cc.otavia.core.reactor.Reactor
import cc.otavia.core.stack.*
import cc.otavia.core.system.ActorSystem
import cc.otavia.core.timer.Timer

import scala.language.unsafeNulls
import scala.reflect.{ClassTag, TypeTest, classTag}

/** base class of IO & Actor model, it has two subclass
 *    1. [[cc.otavia.core.actor.StateActor]]: general actor
 *    1. [[cc.otavia.core.actor.ChannelsActor]]: socket group, a actor which can handle io event
 *
 *  @tparam M
 *    the type of message of this actor can handle
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

    /** When this actor occur not handled exception, the actor system will call this method, if user actor do not
     *  implement this method, the actor will dead.
     */
    protected def restart(): Unit =
        throw new NotImplementedError(getClass.getName + ": an implementation is missing: [restart]")

    /** Actor system call this method after call restart */
    protected def afterRestart(): Unit = {}

    def maxFetchPerRunning: Int = system.defaultMaxFetchPerRunning

    def niceAsk: Int = NICE_ASK

    def niceReply: Int = NICE_REPLY

    def niceNotice: Int = NICE_NOTICE

    def niceEvent: Int = NICE_EVENT

    def nice: Int = 8

    /** user actor override this to control whether restart when occur exception */
    protected def noticeExceptionStrategy: ExceptionStrategy = ExceptionStrategy.Restart

    // method for receive message

    /** receive notice message by this method, the method will be call when this actor instance receive notice message
     *  @param notice
     *    notice message receive by this actor instance
     */
    private[core] def receiveNotice(notice: Notice): Unit

    /** receive ask message by this method, the method will be call when this actor instance receive ask message
     *
     *  @param ask
     *    ask message received by this actor instance
     */
    private[core] def receiveAsk(ask: Ask[? <: Reply]): Unit

    /** receive reply message by this method, the method will be call when this actor instance receive reply message
     *
     *  @param reply
     *    reply message receive by this actor instance
     */
    private[core] def receiveReply(reply: Reply): Unit

    /** Receive IO event from [[Reactor]] or timeout event from [[Timer]]
     *  @param event
     *    IO/timeout event
     */
    private[core] def receiveEvent(event: Event): Unit

    /** Receive [[Notice]] messages in bulk from other [[Actor]]s, possibly more than one sending [[Actor]].
     *
     *  The [[ActorSystem]] call this method if and only if [[batchable]] is true. Conversely, the [[receiveNotice]]
     *  method will not be called
     *
     *  @param notices
     *    batch notices message.
     */
    private[core] def receiveBatchNotice(notices: Seq[Notice]): Unit

    /** Receive [[Ask]] messages in bulk from other [[Actor]]s, possibly more than one sending [[Actor]].
     *
     *  The [[ActorSystem]] call this method if and only if [[batchable]] is true. Conversely, the [[receiveBatchAsk]]
     *  method will not be called
     *
     *  @param asks
     *    batch ask messages.
     */
    private[core] def receiveBatchAsk(asks: Seq[Ask[?]]): Unit

}

object Actor {

    opaque type MessageType = Byte
    val NOTICE_TYPE: MessageType = 0
    val ASK_TYPE: MessageType    = 1
    val REPLY_TYPE: MessageType  = 2

    private val NICE_MESSAGE_DEFAULT = 16
    private val NICE_MESSAGE = SystemPropertyUtil.getInt("cc.otavia.core.actor.nice.message", NICE_MESSAGE_DEFAULT)

    private val NICE_ASK = SystemPropertyUtil.getInt("cc.otavia.core.actor.nice.ask", NICE_MESSAGE)

    private val NICE_REPLY = SystemPropertyUtil.getInt("cc.otavia.core.actor.nice.reply", NICE_MESSAGE)

    private val NICE_NOTICE = SystemPropertyUtil.getInt("cc.otavia.core.actor.nice.notice", NICE_MESSAGE)

    private val NICE_EVENT_DEFAULT = 32
    private val NICE_EVENT         = SystemPropertyUtil.getInt("cc.otavia.core.actor.nice.notice", NICE_EVENT_DEFAULT)

}
